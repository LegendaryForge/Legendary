import java.util.*;

/**
 * N9: all crossings are nexuses, not just same-element ones.
 *
 * Four questions, none of which the existing specs answer:
 *   1. SUPPLY   — how many nexuses per world under N9 vs today, and how many survive a 3D gate?
 *   2. GATE     — what fraction of plan-view crossings are vertically absurd, by gate value?
 *   3. RATIO    — under ACCUMULATION (sum contributions from every nearby arm rather than taking
 *                 the nearest), what composition ratios do geodes actually get? If they cluster at
 *                 1:1 the whole accumulation idea is theatre.
 *   4. RANGE    — the distribution of accumulated sums, which is what should pick the [0,1]
 *                 normalisation rather than a formula chosen in advance.
 *
 * Self-contained on purpose: reimplements ResidueRandom / GrandConvergence / the arm walk (all
 * package-private in :core) and adds the height profile from graded-nexus spec §3, which is
 * DESIGNED BUT NOT IMPLEMENTED. `restore` and `maxPitch` are unspecified there, so both published
 * calibrations are run and every |dy| figure is reported per calibration.
 *
 * Element identity IS included (mirrors CurrentGeometry after PR #91) — without it all six
 * elements are one star and every cross-element figure here would be meaningless.
 */
public final class N9Scan {

    static final long GOLDEN = 0x9E3779B97F4A7C15L, MIX_A = 0xBF58476D1CE4E5B9L, MIX_B = 0x94D049BB133111EBL;
    static final long ARM_DOMAIN = 0x41726D73L, CONV_DOMAIN = 0x436F6E76L, HEIGHT_DOMAIN = 0x48676874L;
    static final long ELEMENT_DOMAIN = 0x456C656DL;

    static final double STEP = 16.0, JITTER = 0.35, INFLUENCE = 24.0;
    static final double PITCH_JITTER = 0.15, HEIGHT_BAND = 96.0;
    static final double CELL = 32.0;

    static final String[] ELEMENTS = {
        "stormseeker:fire", "stormseeker:ice", "stormseeker:lightning",
        "stormseeker:life", "stormseeker:void", "stormseeker:water"
    };
    static final double[] GATES = {12.0, 24.0, 48.0, 96.0};

    static long mix(long s, long a, long b) {
        long z = s ^ (a * GOLDEN) ^ (b * MIX_A);
        z = (z ^ (z >>> 30)) * MIX_A; z = (z ^ (z >>> 27)) * MIX_B; return z ^ (z >>> 31);
    }
    static double unit(long s, long a, long b) { return (mix(s, a, b) >>> 11) * 0x1.0p-53; }
    static double signed(long s, long a, long b) { return unit(s, a, b) * 2.0 - 1.0; }
    static long stableHash(String v) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < v.length(); i++) { h ^= v.charAt(i); h *= 0x100000001B3L; }
        return h;
    }

    record P(double x, double z, double y) {}
    /** One polyline segment, tagged with the element that owns it. */
    record Seg(int element, P a, P b, double pathLen) {}
    record Hit(int elemA, int elemB, double x, double z, double yA, double yB, double dist) {
        double sep() { return Math.abs(yA - yB); }
        boolean cross() { return elemA != elemB; }
    }

    public static void main(String[] args) {
        int armCount = Integer.parseInt(args[0]);
        int stepsPerArm = Integer.parseInt(args[1]);
        int seeds = Integer.parseInt(args[2]);
        double restore = Double.parseDouble(args[3]);
        double maxPitch = Double.parseDouble(args[4]);
        String label = args[5];

        List<Double> heights = new ArrayList<>();
        List<Double> sameCount = new ArrayList<>(), allCount = new ArrayList<>();
        Map<Double, List<Double>> gatedCount = new LinkedHashMap<>();
        for (double g : GATES) gatedCount.put(g, new ArrayList<>());
        int[] zeroWorlds = new int[GATES.length];

        List<Double> crossSep = new ArrayList<>(), sameSep = new ArrayList<>();
        List<Double> pairShare = new ArrayList<>();    // dominant of the TWO crossing elements
        List<Double> pairEuclid = new ArrayList<>(), pairPath = new ArrayList<>();
        List<Double> topShare = new ArrayList<>();     // dominant element's share of the whole geode
        List<Double> participants = new ArrayList<>(); // elements present above 5% at a nexus
        List<Double> sumOnArm = new ArrayList<>(), sumAtNexus = new ArrayList<>(), sumAtConv = new ArrayList<>();
        List<double[]> pileUp = new ArrayList<>(); // {distance from convergence, participant count}
        List<Double> todayDist = new ArrayList<>();  // same-element, ungated — the current model
        List<Double> n9Dist = new ArrayList<>();     // all pairs, 3D-gated — the N9 model

        for (long s = 0; s < seeds; s++) {
            double ang = unit(s, CONV_DOMAIN, 0L) * 2 * Math.PI;
            double rad = 512.0 + unit(s, CONV_DOMAIN, 1L) * (2048.0 - 512.0);
            double gx = Math.cos(ang) * rad, gz = Math.sin(ang) * rad;

            List<Seg> segs = new ArrayList<>();
            for (int e = 0; e < ELEMENTS.length; e++) {
                long elementSeed = mix(s, ELEMENT_DOMAIN, stableHash(ELEMENTS[e]));
                double headingOffset = unit(elementSeed, ELEMENT_DOMAIN, 0L) * 2.0 * Math.PI;
                for (int a = 0; a < armCount; a++) {
                    List<P> arm = buildArm(elementSeed, headingOffset, a, armCount, stepsPerArm, gx, gz, restore, maxPitch);
                    for (int i = 1; i < arm.size(); i++) segs.add(new Seg(e, arm.get(i - 1), arm.get(i), (i - 0.5) * STEP));
                }
            }

            Map<Long, List<Integer>> grid = buildGrid(segs);
            List<Hit> hits = findCrossings(segs, grid, gx, gz);

            long same = hits.stream().filter(h -> !h.cross()).count();
            sameCount.add((double) same);
            allCount.add((double) hits.size());
            for (Hit h : hits) {
                heights.add(h.yA()); heights.add(h.yB());
                (h.cross() ? crossSep : sameSep).add(h.sep());
                if (!h.cross()) todayDist.add(h.dist());
                if (h.sep() < INFLUENCE) n9Dist.add(h.dist());
            }

            for (int gi = 0; gi < GATES.length; gi++) {
                final double g = GATES[gi];
                long n = hits.stream().filter(h -> h.sep() < g).count();
                gatedCount.get(g).add((double) n);
                if (n == 0) zeroWorlds[gi]++;
            }

            // Accumulation, measured only on nexuses that survive the influence-radius gate.
            for (Hit h : hits) {
                if (h.sep() >= INFLUENCE) continue;
                double[] per = accumulate(segs, grid, h.x(), h.z(), (h.yA() + h.yB()) / 2.0);
                double total = 0, top = 0;
                int present = 0;
                for (double v : per) { total += v; top = Math.max(top, v); }
                if (total <= 1e-9) continue;
                for (double v : per) if (v / total >= 0.05) present++;
                double pa = per[h.elemA()], pb = per[h.elemB()];
                if (pa + pb > 1e-9) pairShare.add(Math.max(pa, pb) / (pa + pb));
                double scale = stepsPerArm * STEP;
                double[] pe = accumulate(segs, grid, h.x(), h.z(), (h.yA() + h.yB()) / 2.0, 1, gx, gz, scale);
                double ea = pe[h.elemA()], eb = pe[h.elemB()];
                if (ea + eb > 1e-9) pairEuclid.add(Math.max(ea, eb) / (ea + eb));
                double[] pp = accumulate(segs, grid, h.x(), h.z(), (h.yA() + h.yB()) / 2.0, 2, gx, gz, scale);
                double qa = pp[h.elemA()], qb = pp[h.elemB()];
                if (qa + qb > 1e-9) pairPath.add(Math.max(qa, qb) / (qa + qb));
                topShare.add(top / total);
                pileUp.add(new double[]{h.dist(), present});
                participants.add((double) present);
                sumAtNexus.add(total);
            }

            // Reference points for the normalisation question. The arm sample must be FAR from
            // the convergence, or it measures the convergence instead: every arm starts there, so a
            // segment picked by list index lands on the hub.
            for (Seg cand : segs) {
                double mx = (cand.a().x() + cand.b().x()) / 2, mz = (cand.a().z() + cand.b().z()) / 2;
                if (Math.hypot(mx - gx, mz - gz) < 800.0) continue;
                double my = (cand.a().y() + cand.b().y()) / 2;
                sumOnArm.add(total(accumulate(segs, grid, mx, mz, my)));
                break;
            }
            sumAtConv.add(total(accumulate(segs, grid, gx, gz, 0.0)));
        }

        Collections.sort(heights);
        System.out.printf("=== N9 SCAN [%s] %d elements x %d arms x %d steps | %d seeds ===%n",
                label, ELEMENTS.length, armCount, stepsPerArm, seeds);
        System.out.printf("calibration: restore=%.3f maxPitch=%.2f pitchJitter=%.2f heightBand=%.0f influence=%.0f%n",
                restore, maxPitch, PITCH_JITTER, HEIGHT_BAND, INFLUENCE);
        System.out.printf("VALIDATION crossing-height p5..p95: %.0f .. %.0f  (spec reports -155 .. +158)%n%n",
                pct(heights, 5), pct(heights, 95));

        System.out.println("1. SUPPLY — nexuses per world");
        System.out.printf("  %-34s %8s %8s %8s%n", "", "median", "p10", "p90");
        report("same-element only (today)", sameCount);
        report("all pairs, plan view (N9 ungated)", allCount);
        for (int gi = 0; gi < GATES.length; gi++) {
            report(String.format("all pairs, |dy| < %.0f", GATES[gi]), gatedCount.get(GATES[gi]));
        }
        double medSame = med(sameCount), medGate = med(gatedCount.get(INFLUENCE));
        System.out.printf("  => N9 with the %.0f-gate is %.2fx today's supply%n%n", INFLUENCE, medGate / Math.max(1e-9, medSame));

        System.out.println("2. GATE — worlds left with NO nexus (the completability check §4 raised)");
        for (int gi = 0; gi < GATES.length; gi++) {
            System.out.printf("  |dy| < %-5.0f : %d of %d (%.2f%%)%n", GATES[gi], zeroWorlds[gi], seeds, 100.0 * zeroWorlds[gi] / seeds);
        }
        Collections.sort(crossSep); Collections.sort(sameSep);
        System.out.printf("  |dy| median — cross-element %.1f, same-element %.1f%n", pct(crossSep, 50), pct(sameSep, 50));
        System.out.printf("  fraction of cross-element crossings under the %.0f gate: %.1f%%%n%n",
                INFLUENCE, 100.0 * countBelow(crossSep, INFLUENCE) / Math.max(1, crossSep.size()));

        System.out.println("3. RATIO — under accumulation");
        Collections.sort(pairShare);
        System.out.printf("  the TWO crossing elements: n=%d  p10 %.3f  median %.3f  p90 %.3f  max %.3f%n",
                pairShare.size(), pct(pairShare, 10), pct(pairShare, 50), pct(pairShare, 90), pct(pairShare, 100));
        System.out.printf("    within 45-55%% (effectively 1:1): %.1f%%   >= 65%% (about 2:1 or stronger): %.1f%%%n",
                100.0 * (countBelow(pairShare, 0.55) - countBelow(pairShare, 0.45)) / Math.max(1, pairShare.size()),
                100.0 * (pairShare.size() - countBelow(pairShare, 0.65)) / Math.max(1, pairShare.size()));
        for (String[] v : new String[][]{{"attenuate by EUCLIDEAN dist", "e"}, {"attenuate by PATH LENGTH", "p"}}) {
            List<Double> lst = v[1].equals("e") ? pairEuclid : pairPath;
            Collections.sort(lst);
            System.out.printf("  %-28s p10 %.3f  median %.3f  p90 %.3f  max %.3f | >=65%%: %.1f%%%n",
                    v[0], pct(lst, 10), pct(lst, 50), pct(lst, 90), pct(lst, 100),
                    100.0 * (lst.size() - countBelow(lst, 0.65)) / Math.max(1, lst.size()));
        }
        Collections.sort(topShare);
        System.out.printf("  dominant of ALL elements present: median %.3f  p90 %.3f%n",
                pct(topShare, 50), pct(topShare, 90));
        Collections.sort(participants);
        System.out.printf("  elements present (>=5%% share): median %.0f, p90 %.0f, max %.0f%n%n",
                pct(participants, 50), pct(participants, 90), pct(participants, 100));

        System.out.println("4. RANGE — accumulated sums, for the normalisation decision");
        Collections.sort(sumOnArm); Collections.sort(sumAtNexus); Collections.sort(sumAtConv);
        System.out.printf("  %-22s median %7.2f   p10 %7.2f   p90 %7.2f%n", "on an ordinary arm", pct(sumOnArm, 50), pct(sumOnArm, 10), pct(sumOnArm, 90));
        System.out.printf("  %-22s median %7.2f   p10 %7.2f   p90 %7.2f%n", "at a gated nexus", pct(sumAtNexus, 50), pct(sumAtNexus, 10), pct(sumAtNexus, 90));
        System.out.printf("  %-22s median %7.2f   p10 %7.2f   p90 %7.2f%n", "at the convergence", pct(sumAtConv, 50), pct(sumAtConv, 10), pct(sumAtConv, 90));
        System.out.println();
        System.out.println("5. PILE-UP — elements threading one geode, by distance from the convergence");
        double[][] bands = {{0, 100}, {100, 200}, {200, 300}, {300, 500}, {500, 1000}, {1000, 1e9}};
        System.out.printf("  %-14s %8s %8s %8s %8s %8s%n", "band", "n", "median", "max", ">=3", ">=4");
        for (double[] b : bands) {
            List<Double> counts = new ArrayList<>();
            for (double[] e : pileUp) if (e[0] >= b[0] && e[0] < b[1]) counts.add(e[1]);
            if (counts.isEmpty()) continue;
            Collections.sort(counts);
            long ge3 = counts.stream().filter(c -> c >= 3).count();
            long ge4 = counts.stream().filter(c -> c >= 4).count();
            System.out.printf("  %-14s %8d %8.0f %8.0f %7.1f%% %7.1f%%%n",
                    (int) b[0] + "-" + (b[1] > 1e8 ? "inf" : String.valueOf((int) b[1])), counts.size(),
                    pct(counts, 50), pct(counts, 100), 100.0 * ge3 / counts.size(), 100.0 * ge4 / counts.size());
        }

        System.out.println();
        System.out.println("6. ABSORPTION — what a world-level absorption radius costs and buys");
        System.out.printf("  %-10s %10s %10s %9s %9s %9s%n", "radius", "nexuses/wd", "vs today", "max elems", ">=3", ">=4");
        for (double r : new double[]{0, 100, 150, 200, 300, 400, 500}) {
            List<Double> kept = new ArrayList<>();
            for (double[] e : pileUp) if (e[0] >= r) kept.add(e[1]);
            if (kept.isEmpty()) continue;
            Collections.sort(kept);
            long ge3 = kept.stream().filter(c -> c >= 3).count();
            long ge4 = kept.stream().filter(c -> c >= 4).count();
            System.out.printf("  %-10.0f %10.1f %9.2fx %9.0f %8.1f%% %8.1f%%%n",
                    r, (double) kept.size() / seeds, (kept.size() / (double) seeds) / Math.max(1e-9, med(sameCount)),
                    pct(kept, 100), 100.0 * ge3 / kept.size(), 100.0 * ge4 / kept.size());
        }
        System.out.println();
        System.out.println("7. WILD DENSITY — nexuses per km^2 by distance from the convergence");
        System.out.println("   (radial single-convergence model, NOT the N10 lattice)");
        System.out.printf("  %-14s %12s %12s %10s%n", "band", "today/km2", "N9 gated/km2", "ratio");
        double[][] rings = {{0, 200}, {200, 500}, {500, 1000}, {1000, 2000}, {2000, 3000}, {3000, 4000}};
        for (double[] b : rings) {
            double areaKm2 = Math.PI * (b[1] * b[1] - b[0] * b[0]) / 1_000_000.0;
            long t = todayDist.stream().filter(d -> d >= b[0] && d < b[1]).count();
            long n = n9Dist.stream().filter(d -> d >= b[0] && d < b[1]).count();
            double tp = t / (areaKm2 * seeds), np = n / (areaKm2 * seeds);
            System.out.printf("  %-14s %12.2f %12.2f %9.2fx%n",
                    (int) b[0] + "-" + (int) b[1] + " m", tp, np, tp < 1e-9 ? Double.NaN : np / tp);
        }
        System.out.println();
        System.out.println("SCAN_VERDICT: COMPLETE");
    }

    /** Sum of (1 - d3/influence) over every segment of each element within the influence radius. */
    static double[] accumulate(List<Seg> segs, Map<Long, List<Integer>> grid, double x, double z, double y) {
        return accumulate(segs, grid, x, z, y, 0, 0, 0, 0);
    }

    /** mode 0 = none, 1 = Euclidean from convergence, 2 = path length along the arm. */
    static double[] accumulate(List<Seg> segs, Map<Long, List<Integer>> grid, double x, double z, double y,
            int mode, double gx, double gz, double pathScale) {
        double[] per = new double[ELEMENTS.length];
        int r = (int) Math.ceil(INFLUENCE / CELL);
        int cx = (int) Math.floor(x / CELL), cz = (int) Math.floor(z / CELL);
        Set<Integer> seen = new HashSet<>();
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                List<Integer> bucket = grid.get(key(cx + i, cz + j));
                if (bucket == null) continue;
                for (int idx : bucket) {
                    if (!seen.add(idx)) continue;
                    Seg s = segs.get(idx);
                    double d = distToSegment3d(x, y, z, s.a(), s.b());
                    if (d >= INFLUENCE) continue;
                    double att = switch (mode) {
                        case 1 -> Math.max(0.05, 1.0 - Math.hypot(x - gx, z - gz) / pathScale);
                        case 2 -> Math.max(0.05, 1.0 - s.pathLen() / pathScale);
                        default -> 1.0;
                    };
                    per[s.element()] += (1.0 - d / INFLUENCE) * att;
                }
            }
        }
        return per;
    }

    static List<Hit> findCrossings(List<Seg> segs, Map<Long, List<Integer>> grid, double gx, double gz) {
        List<Hit> hits = new ArrayList<>();
        Set<Long> tested = new HashSet<>();
        for (List<Integer> bucket : grid.values()) {
            for (int a = 0; a < bucket.size(); a++) {
                for (int b = a + 1; b < bucket.size(); b++) {
                    int i = bucket.get(a), j = bucket.get(b);
                    if (i > j) { int t = i; i = j; j = t; }
                    if (!tested.add((long) i * 1_000_000L + j)) continue;
                    Seg s1 = segs.get(i), s2 = segs.get(j);
                    if (s1.element() == s2.element() && sharesEndpoint(s1, s2)) continue;
                    double[] c = cross(s1.a(), s1.b(), s2.a(), s2.b());
                    if (c == null) continue;
                    hits.add(new Hit(s1.element(), s2.element(), c[0], c[1], c[2], c[3],
                            Math.hypot(c[0] - gx, c[1] - gz)));
                }
            }
        }
        return hits;
    }

    static boolean sharesEndpoint(Seg a, Seg b) {
        return same(a.a(), b.a()) || same(a.a(), b.b()) || same(a.b(), b.a()) || same(a.b(), b.b());
    }
    static boolean same(P p, P q) { return Math.abs(p.x() - q.x()) < 1e-9 && Math.abs(p.z() - q.z()) < 1e-9; }

    static Map<Long, List<Integer>> buildGrid(List<Seg> segs) {
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            int x0 = (int) Math.floor(Math.min(s.a().x(), s.b().x()) / CELL);
            int x1 = (int) Math.floor(Math.max(s.a().x(), s.b().x()) / CELL);
            int z0 = (int) Math.floor(Math.min(s.a().z(), s.b().z()) / CELL);
            int z1 = (int) Math.floor(Math.max(s.a().z(), s.b().z()) / CELL);
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    grid.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(i);
                }
            }
        }
        return grid;
    }

    static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    static double distToSegment3d(double px, double py, double pz, P a, P b) {
        double ax = b.x() - a.x(), ay = b.y() - a.y(), az = b.z() - a.z();
        double len2 = ax * ax + ay * ay + az * az;
        double t = len2 < 1e-12 ? 0 : ((px - a.x()) * ax + (py - a.y()) * ay + (pz - a.z()) * az) / len2;
        t = Math.max(0, Math.min(1, t));
        double dx = px - (a.x() + ax * t), dy = py - (a.y() + ay * t), dz = pz - (a.z() + az * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static List<P> buildArm(long elementSeed, double headingOffset, int arm, int armCount, int steps,
            double gx, double gz, double restore, double maxPitch) {
        List<P> pts = new ArrayList<>(steps + 1);
        pts.add(new P(gx, gz, 0.0));
        double heading = headingOffset + 2.0 * Math.PI * arm / armCount, x = gx, z = gz, y = 0.0, pitch = 0.0;
        for (int st = 0; st < steps; st++) {
            heading += signed(elementSeed, ARM_DOMAIN + arm, st) * JITTER;
            pitch += signed(elementSeed, HEIGHT_DOMAIN + arm, st) * PITCH_JITTER - restore * (y / HEIGHT_BAND);
            pitch = Math.max(-maxPitch, Math.min(maxPitch, pitch));
            x += Math.cos(heading) * STEP; z += Math.sin(heading) * STEP; y += Math.sin(pitch) * STEP;
            pts.add(new P(x, z, y));
        }
        return pts;
    }

    static double[] cross(P a1, P a2, P b1, P b2) {
        double ax = a2.x() - a1.x(), az = a2.z() - a1.z(), bx = b2.x() - b1.x(), bz = b2.z() - b1.z();
        double den = ax * bz - az * bx;
        if (Math.abs(den) < 1e-9) return null;
        double dx = b1.x() - a1.x(), dz = b1.z() - a1.z();
        double t = (dx * bz - dz * bx) / den, u = (dx * az - dz * ax) / den;
        if (t <= 1e-9 || t >= 1 - 1e-9 || u <= 1e-9 || u >= 1 - 1e-9) return null;
        return new double[]{a1.x() + ax * t, a1.z() + az * t, a1.y() + (a2.y() - a1.y()) * t, b1.y() + (b2.y() - b1.y()) * u};
    }

    static double total(double[] v) { double t = 0; for (double d : v) t += d; return t; }
    static double med(List<Double> v) { List<Double> c = new ArrayList<>(v); Collections.sort(c); return pct(c, 50); }
    static int countBelow(List<Double> sorted, double v) {
        int n = 0; for (double d : sorted) { if (d < v) n++; else break; } return n;
    }
    static void report(String name, List<Double> v) {
        List<Double> c = new ArrayList<>(v); Collections.sort(c);
        System.out.printf("  %-34s %8.0f %8.0f %8.0f%n", name, pct(c, 50), pct(c, 10), pct(c, 90));
    }
    static double pct(List<Double> sorted, int p) {
        if (sorted.isEmpty()) return Double.NaN;
        int i = (int) Math.round((p / 100.0) * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, i)));
    }
}

/// Measures the residue sizing chain that anchor-density design §3 estimates.
///
/// §3 derives coverage through three steps — anchors pack at one per R², an MST gives one edge each
/// of length ~R, and the crystal band is 2w times that length. The first has a closed form that
/// differs by π (AnchorSelector is a Matérn type II hard-core process), and the second is only
/// self-consistent when anchor density really is 1/R². This executes the chain instead.
///
/// Faithful to the implementation in three ways that matter: candidates come from the real
/// CandidateLattice jitter, the tie-break is the real ResidueRandom.mix SplitMix64 finaliser, and
/// selection applies AnchorSelector's exact rule (retain unless something within the suppression
/// radius outranks you).
///
/// Run (Java 25, single file, no build step):
///   java SizingScan.java <suppressionRadius> <cellSize> <span> <trials> <label>
///   java SizingScan.java 512 256 8192 3 "as designed"
///   java SizingScan.java 256 128 8192 3 "corrected"
///
/// Ends with SIZING_VERDICT: COMPLETE. Read that line by name, not by position.
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SizingScan {

    static final int W = 8;       // influence radius: crystals grow within w of a current
    static final int RASTER = 4;  // blocks per pixel when measuring band area
    static final int ELEMENTS = 5;
    static final double JITTER = 0.35;

    // --- ResidueRandom, reproduced so this file stays standalone -------------------------------
    static final long MIX_A = 0xBF58476D1CE4E5B9L;
    static final long MIX_B = 0x94D049BB133111EBL;

    static long mix(long seed, long a, long b) {
        long z = seed + a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        return z ^ (z >>> 31);
    }

    static double unit(long seed, long a, long b) {
        return (mix(seed, a, b) >>> 11) * 0x1.0p-53;
    }

    static double signed(long seed, long a, long b) {
        return unit(seed, a, b) * 2.0 - 1.0;
    }

    record Pt(double x, double z) {}

    /// One jittered candidate per cell — CandidateLattice.candidateInCell.
    static List<Pt> candidates(long seed, int cell, double lo, double hi) {
        long first = Math.floorDiv((long) Math.floor(lo), cell) - 1;
        long last = Math.floorDiv((long) Math.floor(hi), cell) + 1;
        double reach = JITTER * cell;
        List<Pt> out = new ArrayList<>();
        for (long cx = first; cx <= last; cx++) {
            for (long cz = first; cz <= last; cz++) {
                double x = cx * (double) cell + cell / 2.0 + signed(seed, cx, cz) * reach;
                double z = cz * (double) cell + cell / 2.0 + signed(seed ^ 0x5DEECE66DL, cz, cx) * reach;
                if (x >= lo && x <= hi && z >= lo && z <= hi) {
                    out.add(new Pt(x, z));
                }
            }
        }
        return out;
    }

    /// AnchorSelector.select in the regime §3 sizes against: strengths tie, so the hash decides.
    /// Element-blind by construction in the real selector; given its own domain here so the five
    /// elements get independent strength fields, which is what varied terrain supplies.
    static List<Pt> select(long seed, long elementDomain, List<Pt> pts, double radius) {
        int n = pts.size();
        long[] mark = new long[n];
        for (int i = 0; i < n; i++) {
            mark[i] = mix(seed ^ elementDomain, Math.round(pts.get(i).x()), Math.round(pts.get(i).z()));
        }
        // Uniform grid of one radius so each candidate examines 9 buckets rather than all n.
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int i = 0; i < n; i++) {
            grid.computeIfAbsent(key(pts.get(i), radius), k -> new ArrayList<>()).add(i);
        }
        List<Pt> kept = new ArrayList<>();
        double r2 = radius * radius;
        for (int i = 0; i < n; i++) {
            Pt p = pts.get(i);
            long cx = (long) Math.floor(p.x() / radius);
            long cz = (long) Math.floor(p.z() / radius);
            boolean beaten = false;
            outer:
            for (long dx = -1; dx <= 1 && !beaten; dx++) {
                for (long dz = -1; dz <= 1; dz++) {
                    List<Integer> bucket = grid.get((cx + dx) * 73856093L ^ (cz + dz) * 19349663L);
                    if (bucket == null) {
                        continue;
                    }
                    for (int j : bucket) {
                        if (j == i) {
                            continue;
                        }
                        Pt q = pts.get(j);
                        double d2 = (q.x() - p.x()) * (q.x() - p.x()) + (q.z() - p.z()) * (q.z() - p.z());
                        // AnchorSelector skips when distance > radius, so equality still competes.
                        if (d2 <= r2 && Long.compareUnsigned(mark[j], mark[i]) > 0) {
                            beaten = true;
                            break outer;
                        }
                    }
                }
            }
            if (!beaten) {
                kept.add(p);
            }
        }
        return kept;
    }

    static long key(Pt p, double radius) {
        long cx = (long) Math.floor(p.x() / radius);
        long cz = (long) Math.floor(p.z() / radius);
        return cx * 73856093L ^ cz * 19349663L;
    }

    /// Euclidean minimum spanning tree — anchor-density design §5. Prim's, O(n²).
    static int[][] mst(List<Pt> pts) {
        int n = pts.size();
        if (n < 2) {
            return new int[0][];
        }
        boolean[] in = new boolean[n];
        double[] best = new double[n];
        int[] src = new int[n];
        in[0] = true;
        for (int i = 0; i < n; i++) {
            best[i] = d2(pts.get(0), pts.get(i));
        }
        int[][] edges = new int[n - 1][2];
        for (int e = 0; e < n - 1; e++) {
            int pick = -1;
            double bd = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!in[i] && best[i] < bd) {
                    bd = best[i];
                    pick = i;
                }
            }
            edges[e] = new int[] {src[pick], pick};
            in[pick] = true;
            for (int i = 0; i < n; i++) {
                double d = d2(pts.get(pick), pts.get(i));
                if (!in[i] && d < best[i]) {
                    best[i] = d;
                    src[i] = pick;
                }
            }
        }
        return edges;
    }

    static double d2(Pt a, Pt b) {
        return (a.x() - b.x()) * (a.x() - b.x()) + (a.z() - b.z()) * (a.z() - b.z());
    }

    /// Marks every pixel within w of some edge. Union, so overlapping bands are not double-counted.
    static void paint(boolean[][] grid, double lo, List<Pt> a, int[][] edges) {
        int n = grid.length;
        for (int[] edge : edges) {
            Pt p = a.get(edge[0]);
            Pt q = a.get(edge[1]);
            int i0 = clamp((int) Math.floor((Math.min(p.x(), q.x()) - W - lo) / RASTER), n);
            int i1 = clamp((int) Math.ceil((Math.max(p.x(), q.x()) + W - lo) / RASTER), n);
            int j0 = clamp((int) Math.floor((Math.min(p.z(), q.z()) - W - lo) / RASTER), n);
            int j1 = clamp((int) Math.ceil((Math.max(p.z(), q.z()) + W - lo) / RASTER), n);
            double dx = q.x() - p.x();
            double dz = q.z() - p.z();
            double len2 = dx * dx + dz * dz;
            for (int i = i0; i < i1; i++) {
                double px = lo + (i + 0.5) * RASTER;
                for (int j = j0; j < j1; j++) {
                    double pz = lo + (j + 0.5) * RASTER;
                    double t = len2 == 0 ? 0 : Math.clamp(((px - p.x()) * dx + (pz - p.z()) * dz) / len2, 0.0, 1.0);
                    double ox = px - (p.x() + t * dx);
                    double oz = pz - (p.z() + t * dz);
                    if (ox * ox + oz * oz <= (double) W * W) {
                        grid[i][j] = true;
                    }
                }
            }
        }
    }

    static int clamp(int v, int n) {
        return Math.max(0, Math.min(n, v));
    }

    static double fraction(boolean[][] g) {
        long hit = 0;
        for (boolean[] row : g) {
            for (boolean b : row) {
                if (b) {
                    hit++;
                }
            }
        }
        return hit / (double) (g.length * (long) g[0].length);
    }

    public static void main(String[] args) {
        int radius = Integer.parseInt(args[0]);
        int cell = Integer.parseInt(args[1]);
        double span = Double.parseDouble(args[2]);
        int trials = Integer.parseInt(args[3]);
        String label = args.length > 4 ? args[4] : "";

        double km2 = (span / 1000.0) * (span / 1000.0);
        int px = (int) Math.ceil(span / RASTER);
        double anchorsSum = 0;
        double lengthSum = 0;
        double perElemSum = 0;
        double unionSum = 0;

        for (int t = 0; t < trials; t++) {
            long seed = 1787872410574L + t;   // the live world seed, offset per trial
            List<Pt> pts = candidates(seed, cell, -span / 2 - radius, span / 2 + radius);
            boolean[][] union = new boolean[px][px];
            for (int e = 0; e < ELEMENTS; e++) {
                List<Pt> anchors = select(seed, 0x456C656DL * (e + 1), pts, radius);
                int[][] edges = mst(anchors);
                long core = anchors.stream()
                        .filter(p -> Math.abs(p.x()) <= span / 2 && Math.abs(p.z()) <= span / 2)
                        .count();
                double len = 0;
                for (int[] edge : edges) {
                    Pt p = anchors.get(edge[0]);
                    Pt q = anchors.get(edge[1]);
                    if (Math.abs((p.x() + q.x()) / 2) <= span / 2 && Math.abs((p.z() + q.z()) / 2) <= span / 2) {
                        len += Math.sqrt(d2(p, q));
                    }
                }
                boolean[][] one = new boolean[px][px];
                paint(one, -span / 2, anchors, edges);
                paint(union, -span / 2, anchors, edges);
                anchorsSum += core / km2;
                lengthSum += len / km2;
                perElemSum += fraction(one);
            }
            unionSum += fraction(union);
        }

        int n = trials * ELEMENTS;
        double anchors = anchorsSum / n;
        double length = lengthSum / n;
        double perElem = perElemSum / n;
        double union = unionSum / trials;

        System.out.printf("label                 %s%n", label);
        System.out.printf("suppressionRadius R   %d%n", radius);
        System.out.printf("cellSize              %d   (R/cell = %.2f)%n", cell, radius / (double) cell);
        System.out.printf("saturation lam_p*pi*R^2  %.2f   competitors within R = %.1f%n",
                Math.PI * radius * radius / ((double) cell * cell),
                Math.PI * radius * radius / ((double) cell * cell) - 1);
        System.out.printf("%nanchors/km2           %7.2f   design 1/R^2        = %7.2f   ratio %.2f%n",
                anchors, 1e6 / ((double) radius * radius), 1e6 / ((double) radius * radius) / anchors);
        System.out.printf("MST m/km2             %7.0f   design A/R          = %7.0f   ratio %.2f%n",
                length, 1e6 / radius, (1e6 / radius) / length);
        System.out.printf("coverage per element  %6.2f%%   design 2w/R         = %6.2f%%   ratio %.2f%n",
                perElem * 100, 200.0 * W / radius, (2.0 * W / radius) / perElem);
        System.out.printf("coverage 5x convention %5.2f%%   design 10w/R        = %6.2f%%%n",
                perElem * 500, 1000.0 * W / radius);
        System.out.printf("coverage UNION        %6.2f%%   <- what a player walks%n", union * 100);
        System.out.printf("%nband check 2w*L       %6.2f%%   (union overlap is the difference)%n",
                2.0 * W * length / 1e6 * 100);
        System.out.println("\nSIZING_VERDICT: COMPLETE");
    }
}

package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ColumnMeasurement;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.AnchorSelector;
import io.github.legendaryforge.legendary.core.internal.residue.CandidateLattice;
import io.github.legendaryforge.legendary.core.internal.residue.VolumeStrength;
import io.github.legendaryforge.legendary.mod.hytale.residue.GeneratorTerrainSample;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Debug command: {@code /anchordrainage}
 *
 * <p>Answers the risk the per-element topology decision names as the one to find before building:
 * <strong>does a `Sprawling` current forest degenerate when the strength field is near-uniform?</strong>
 * And measures, in the same walk, the one parameter that decision adds — the distribution of
 * nearest-stronger distances that sets `reach`.
 *
 * <p><strong>What `Sprawling` is.</strong> Each anchor links to the nearest anchor that
 * {@link AnchorSelector#beats} it, within `reach`; an anchor with nothing stronger in reach is a
 * source. Acyclic because strength strictly decreases along every edge. The order is the shipped one
 * rather than a replica, which is why `beats` is visible: a replica would make this a measurement of
 * the replica.
 *
 * <p><strong>The degeneracy to look for is not a long chain.</strong> A long chain is correct on a
 * real gradient — that is what flowing uphill looks like. What would be wrong is a chain that follows
 * the <em>hash</em> rather than the field, which happens where strengths tie: `beats` then decides by
 * position hash, so each anchor's parent is an arbitrary neighbour and the forest is a random
 * functional graph. Both look like "long chains" in a depth statistic, so depth alone cannot tell
 * them apart. The discriminator printed here is <strong>edge-direction coherence</strong>: the modal
 * fraction of edge bearings over eight sectors. A field-following forest agrees; a hash-following one
 * sits near the 12.5% chance line.
 *
 * <p><strong>Two controls, and they bracket the question.</strong>
 * {@link GeneratorTerrainSample#flat} makes every strength identical, which is the degenerate case
 * itself — so it measures how bad bad looks rather than proving the instrument works.
 * {@link GeneratorTerrainSample#slope} gives a clean monotone gradient, where the forest must come
 * out ordered and uphill. Real terrain is then read between them.
 *
 * <p><strong>The ramp will not read near 100%, and stating that before the run matters.</strong> On
 * an east-rising ramp every stronger anchor lies east, so the nearest one lies somewhere in the
 * eastern half-plane — about four of the eight sectors, concentrated near due east but genuinely
 * spread. So a clean monotone field should produce a modal sector somewhere around 30–60%, far above
 * the 12.5% chance line and nowhere near saturation. Nothing in the verdict depends on a threshold
 * for this: all three figures are printed side by side and the comparison is the reader's, because a
 * control whose pass condition is invented after seeing it is not a control.
 *
 * <p><strong>Earth only</strong>, and <strong>sample step 16 rather than the shipped 8</strong>.
 * {@link GeneratorTerrainSample} documents why the other four elements are not measurable by this
 * route. The step is a stated proxy: strength is a block volume and so invariant under
 * `sampleStep`, which is what makes the substitution legitimate, but it coarsens the integer
 * granularity of strength — so this run will report slightly <em>more</em> ties than the shipped step
 * would, and therefore slightly more hash-decided edges. It biases the degeneracy measurement
 * pessimistically, which is the safe direction. It is here because the region needed to see forest
 * structure is ~38 km², and at step 8 that is four times the work for a structural question.
 *
 * <p>Prints {@code DRAINAGE_VERDICT:} as its last line. Read it by name.
 */
public class AnchorDrainageCommand extends AbstractWorldCommand {

    /**
     * Half-width of the region whose anchors are reported on. ~105 km² at this size.
     *
     * <p>Widened from 3072 on the third run: the binding constraint on the discriminator was
     * <strong>n = 52 edges</strong>, not the metric. At the measured 1.48 anchors/km² this should
     * yield roughly 155.
     */
    private static final int CORE_HALF = 5120;

    /** Coarser than the shipped 8. Legitimate because strength is a volume; see the class javadoc. */
    private static final int PROBE_SAMPLE_STEP = 16;

    /** Reaches measured, in blocks. 256 is `suppressionRadius`, which must yield no edge at all. */
    private static final int[] REACHES = {256, 384, 512, 768, 1024, 1536};

    /**
     * The reach the discriminator is read at, in blocks.
     *
     * <p>1536 rather than 512, and fixed before the run. The measured nearest-stronger p90 is
     * 1312 b, so at reach 512 roughly 70% of core anchors have no parent and the edge set is both
     * tiny and selected for short edges — which is how the previous version of this probe came to
     * read its ramp control off <strong>five edges</strong>. The discriminator needs the reach where
     * nearly every anchor is linked, not the one where few are.
     */
    private static final int USABLE_REACH = 1536;

    private static final int FLAT_SURFACE = 160;

    private static final int SLOPE_BASE = 96;

    /** One block of rise per 32 east: ~192 blocks of rise across the core region. */
    private static final int SLOPE_RUN = 32;

    /** Edge bearings are bucketed into this many sectors. Retained only for the forest table. */
    private static final int BEARING_SECTORS = 8;

    /**
     * Radii, in blocks, over which the local strength gradient is fitted.
     *
     * <p><strong>Four of them, and the curve is the result rather than any one row.</strong> A
     * gradient is an estimate at a scale, and reading one scale and believing it is what defeated
     * this probe's previous metric and the wind-channel probe before it.
     *
     * <p><strong>The prediction, stated before the run that uses these radii.</strong> "Does this
     * edge go uphill" means uphill <em>over the distance the edge travels</em>, and the measured
     * median edge is 640 b. So alignment should strengthen as the radius approaches the edge scale
     * and then <strong>plateau</strong>. If instead it rises monotonically through 2048 b, that is
     * not edges-follow-field — it is the regional trend being picked up, and {@code Sprawling}
     * should be reported as unsupported. The first two radii already measured 89.6 and 74.8 degrees
     * on real terrain, so the direction of movement is known and only the shape of the rest of the
     * curve is not.
     */
    private static final int[] GRADIENT_RADII = {256, 512, 1024, 2048};

    /** No parent. */
    private static final int NO_PARENT = -1;

    public AnchorDrainageCommand() {
        super("anchordrainage", "Measures whether a Sprawling current forest degenerates on a near-uniform field");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- anchor drainage probe ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        WorldConfig config;
        long seed;
        try {
            config = world.getWorldConfig();
            seed = config.getSeed();
        } catch (RuntimeException e) {
            bail(context, sb, "no world config: " + describe(e));
            return;
        }
        sb.append("seed: ").append(seed).append('\n');

        ChunkGenerator generator;
        try {
            IWorldGen raw = config.getWorldGenProvider().getGenerator();
            if (!(raw instanceof ChunkGenerator cg)) {
                bail(
                        context,
                        sb,
                        "generator is " + (raw == null ? "null" : raw.getClass().getName()));
                return;
            }
            generator = cg;
        } catch (Exception e) {
            bail(context, sb, "getGenerator() threw: " + describe(e));
            return;
        }

        ElementGrounding earth = null;
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if ("earth".equals(grounding.elementId())) {
                earth = grounding;
            }
        }
        if (earth == null) {
            bail(context, sb, "no 'earth' grounding in cosmologicalFive()");
            return;
        }

        AnchorParameters base = withStep(AnchorParameters.defaults(), PROBE_SAMPLE_STEP);
        int maxReach = REACHES[REACHES.length - 1];
        // Halo discipline, and it is the claim this topology rests on: an anchor needs every rival
        // within suppressionRadius to be selected correctly, and every potential parent within
        // maxReach to be linked correctly. Report only on the core, where both hold.
        int haloHalf = CORE_HALF + base.suppressionRadius() + maxReach;

        List<WorldPoint2d> candidates = CandidateLattice.candidatesWithin(
                seed, base.cellSize(), base.jitterFraction(), -haloHalf, -haloHalf, haloHalf, haloHalf);

        sb.append("element: earth (the only one this route can measure)\n");
        sb.append("core: +/-")
                .append(CORE_HALF)
                .append(" b   halo: +/-")
                .append(haloHalf)
                .append(" (core + suppressionRadius ")
                .append(base.suppressionRadius())
                .append(" + maxReach ")
                .append(maxReach)
                .append(")\n");
        sb.append("candidates in halo: ")
                .append(candidates.size())
                .append("   sampleStep: ")
                .append(base.sampleStep())
                .append(" (shipped is ")
                .append(AnchorParameters.defaults().sampleStep())
                .append(", see javadoc)\n");
        if (candidates.isEmpty()) {
            bail(context, sb, "the lattice proposed no candidate");
            return;
        }

        sb.append("\n=== CONTROL A: flat field — the degenerate case itself ===\n");
        sb.append("Every strength identical, so every edge is decided by the position hash. Bearing\n")
                .append("coherence should sit near the ")
                .append(String.format("%.1f%%", 100.0 / BEARING_SECTORS))
                .append(" chance line. This shows how bad bad looks.\n");
        Forests flatForests = run(GeneratorTerrainSample.flat(FLAT_SURFACE), base, earth, candidates, seed);
        sb.append(flatForests.render());

        sb.append("\n=== CONTROL B: monotone ramp, 1 block per ")
                .append(SLOPE_RUN)
                .append(" east ===\n");
        sb.append("Strength is proportional to mean height in the window, so the field is cleanly\n")
                .append("monotone and every edge must point the same way. Coherence should approach 100%.\n");
        Forests slopeForests = run(GeneratorTerrainSample.slope(SLOPE_BASE, SLOPE_RUN), base, earth, candidates, seed);
        sb.append(slopeForests.render());

        sb.append("\n=== REAL TERRAIN, via generateHeight ===\n");
        GeneratorTerrainSample real = GeneratorTerrainSample.of(generator, (int) seed);
        long startedAt = System.nanoTime();
        Forests realForests = run(real, base, earth, candidates, seed);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        sb.append(realForests.render());
        sb.append("columns fetched: ")
                .append(real.columnsCached())
                .append("   elapsed: ")
                .append(elapsedMs)
                .append(" ms\n");

        sb.append('\n').append(interpret(flatForests, slopeForests, realForests));
        sb.append("\nDRAINAGE_VERDICT: ")
                .append(verdict(flatForests, slopeForests, realForests))
                .append('\n');
        send(context, sb.toString());
    }

    /** Select anchors once, then build one forest per reach over the same anchor set. */
    private static Forests run(
            TerrainSample sample,
            AnchorParameters parameters,
            ElementGrounding earth,
            List<WorldPoint2d> candidates,
            long seed) {
        int floor = earth.minimumStrengthSamples(parameters.sampleStep());
        List<Anchor> measured = new ArrayList<>();
        for (WorldPoint2d candidate : candidates) {
            ColumnMeasurement measurement = VolumeStrength.measure(sample, parameters, earth, candidate);
            if (measurement.strength() < floor) {
                continue;
            }
            Optional<Anchor> anchor = measurement.asCandidate(candidate.x(), candidate.z());
            anchor.ifPresent(measured::add);
        }
        List<Anchor> anchors = AnchorSelector.select(seed, parameters, earth, measured);

        Forests out = new Forests();
        out.eligible = measured.size();
        out.anchors = anchors.size();
        for (Anchor a : anchors) {
            if (inCore(a)) {
                out.coreAnchors++;
            }
        }

        // The nearest-stronger distance distribution, unbounded by reach. This is what sets reach,
        // and it is a property of the anchor set rather than of any reach value -- so it is measured
        // once here rather than inferred from the per-reach rows below.
        List<Double> nearestStronger = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            if (!inCore(anchors.get(i))) {
                continue;
            }
            int parent = nearestStronger(anchors, i, Double.MAX_VALUE, seed);
            if (parent == NO_PARENT) {
                out.noStrongerAnywhere++;
                continue;
            }
            nearestStronger.add(distance(anchors.get(i), anchors.get(parent)));
        }
        out.nearestStrongerP10 = percentile(nearestStronger, 10);
        out.nearestStrongerP50 = percentile(nearestStronger, 50);
        out.nearestStrongerP90 = percentile(nearestStronger, 90);
        out.nearestStrongerMax = percentile(nearestStronger, 100);

        // Gradients are a property of the field and the anchor, not of any reach, so they are
        // computed once here. Recomputing them inside the per-reach loop multiplied the fit cost by
        // the number of reaches for no change in the answer.
        //
        // `measured` -- the candidate field -- is what the gradient is fitted over, not the anchor
        // set: candidates sit every cellSize (128 b) where anchors sit every ~820, so the anchor set
        // is far too sparse to estimate a local gradient from.
        double[][][] gradients = new double[GRADIENT_RADII.length][anchors.size()][];
        for (int r = 0; r < GRADIENT_RADII.length; r++) {
            for (int i = 0; i < anchors.size(); i++) {
                if (!inCore(anchors.get(i))) {
                    continue;
                }
                Anchor a = anchors.get(i);
                gradients[r][i] =
                        gradientAt(measured, a.position().x(), a.position().z(), GRADIENT_RADII[r]);
            }
        }

        for (int reach : REACHES) {
            Row row = forestAt(anchors, reach, seed);
            for (int r = 0; r < GRADIENT_RADII.length; r++) {
                row.alignments.add(alignment(anchors, row.parents, gradients[r], GRADIENT_RADII[r]));
            }
            out.rows.add(row);
        }
        return out;
    }

    /**
     * How well a reach's edges follow the local strength gradient.
     *
     * <p><strong>This replaces edge-bearing coherence, which could not discriminate.</strong> That
     * metric compared edges against <em>each other</em>, so its null had to be calibrated from a
     * control — and measured 29% on a clean monotone ramp against 19% on a pure hash field, a window
     * too narrow to classify real terrain, which sat inside it. This metric compares each edge
     * against the field it is supposed to be following, so <strong>the null is arithmetic</strong>:
     * if edges ignore the field, the angle between edge and uphill is uniform on {@code [0,180]}, so
     * the mean is {@code 90} degrees and the fraction below {@code 90} is exactly {@code 0.5}. No
     * control calibrates that.
     *
     * <p>A zero gradient is reported as <strong>undefined rather than as a number</strong>, which is
     * what makes the flat control informative: on a field with no gradient this metric must decline
     * to answer, and a figure appearing there would mean the fit is reading noise.
     */
    private static Alignment alignment(List<Anchor> anchors, int[] parents, double[][] gradients, int gradientRadius) {
        Alignment out = new Alignment();
        out.gradientRadius = gradientRadius;
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Anchor self = anchors.get(i);
            if (!inCore(self) || parents[i] == NO_PARENT) {
                continue;
            }
            double[] gradient = gradients[i];
            if (gradient == null) {
                out.undefined++;
                continue;
            }
            Anchor parent = anchors.get(parents[i]);
            double ex = parent.position().x() - self.position().x();
            double ez = parent.position().z() - self.position().z();
            double edgeLength = Math.hypot(ex, ez);
            double gradientLength = Math.hypot(gradient[0], gradient[1]);
            if (edgeLength == 0 || gradientLength == 0) {
                out.undefined++;
                continue;
            }
            double cosine = (ex * gradient[0] + ez * gradient[1]) / (edgeLength * gradientLength);
            double theta = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosine))));
            angles.add(theta);
            if (theta < 90.0) {
                out.uphill++;
            }
        }
        out.defined = angles.size();
        double sum = 0;
        for (double theta : angles) {
            sum += theta;
        }
        out.meanAngle = angles.isEmpty() ? Double.NaN : sum / angles.size();
        out.medianAngle = percentile(angles, 50);
        return out;
    }

    /**
     * Least-squares plane fit of candidate strength around a point, as {@code {dS/dx, dS/dz}}.
     *
     * <p>Null when the neighbourhood is degenerate or the fitted gradient is exactly zero — which is
     * the whole-field case for the flat control, and must not be reported as a direction.
     */
    private static double[] gradientAt(List<Anchor> field, double x, double z, int radius) {
        double sumX = 0;
        double sumZ = 0;
        double sumS = 0;
        int n = 0;
        for (Anchor a : field) {
            double dx = a.position().x() - x;
            double dz = a.position().z() - z;
            if (Math.hypot(dx, dz) > radius) {
                continue;
            }
            sumX += dx;
            sumZ += dz;
            sumS += a.strength();
            n++;
        }
        if (n < 4) {
            return null;
        }
        double meanX = sumX / n;
        double meanZ = sumZ / n;
        double meanS = sumS / n;
        double sxx = 0;
        double szz = 0;
        double sxz = 0;
        double sxs = 0;
        double szs = 0;
        for (Anchor a : field) {
            double dx = a.position().x() - x;
            double dz = a.position().z() - z;
            if (Math.hypot(dx, dz) > radius) {
                continue;
            }
            double cx = dx - meanX;
            double cz = dz - meanZ;
            double cs = a.strength() - meanS;
            sxx += cx * cx;
            szz += cz * cz;
            sxz += cx * cz;
            sxs += cx * cs;
            szs += cz * cs;
        }
        double det = sxx * szz - sxz * sxz;
        if (det == 0) {
            return null;
        }
        double bx = (szz * sxs - sxz * szs) / det;
        double bz = (sxx * szs - sxz * sxs) / det;
        if (bx == 0 && bz == 0) {
            return null;
        }
        return new double[] {bx, bz};
    }

    /** One reach's forest, with statistics over core anchors only. */
    private static Row forestAt(List<Anchor> anchors, int reach, long seed) {
        int n = anchors.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = nearestStronger(anchors, i, reach, seed);
        }

        Row row = new Row();
        row.reach = reach;
        row.parents = parent;
        List<Double> lengths = new ArrayList<>();
        int[] sectors = new int[BEARING_SECTORS];
        for (int i = 0; i < n; i++) {
            if (!inCore(anchors.get(i))) {
                continue;
            }
            row.core++;
            if (parent[i] == NO_PARENT) {
                row.sources++;
                continue;
            }
            row.edges++;
            Anchor a = anchors.get(i);
            Anchor b = anchors.get(parent[i]);
            lengths.add(distance(a, b));
            double bearing = Math.atan2(
                    b.position().z() - a.position().z(),
                    b.position().x() - a.position().x());
            int sector = (int) Math.floor((bearing + Math.PI) / (2 * Math.PI) * BEARING_SECTORS);
            sectors[Math.min(sector, BEARING_SECTORS - 1)]++;
        }
        row.medianEdge = percentile(lengths, 50);
        row.maxEdge = percentile(lengths, 100);

        int modal = 0;
        for (int k = 1; k < BEARING_SECTORS; k++) {
            if (sectors[k] > sectors[modal]) {
                modal = k;
            }
        }
        row.bearingCoherence = row.edges == 0 ? Double.NaN : 100.0 * sectors[modal] / row.edges;

        // Depth, walking parents to a source. Cannot loop: every edge strictly decreases strength
        // under a total order, so the walk terminates -- but it is bounded anyway, because an
        // assertion is cheaper than trusting the argument.
        for (int i = 0; i < n; i++) {
            if (!inCore(anchors.get(i))) {
                continue;
            }
            int depth = 0;
            int at = i;
            while (parent[at] != NO_PARENT && depth <= n) {
                at = parent[at];
                depth++;
            }
            if (depth > n) {
                row.cycleSuspected = true;
            }
            row.maxDepth = Math.max(row.maxDepth, depth);
        }
        return row;
    }

    /**
     * Index of the nearest anchor that beats {@code i}, within {@code reach}, or {@link #NO_PARENT}.
     *
     * <p>Uses the shipped {@link AnchorSelector#beats} so "stronger" means here exactly what it means
     * in selection.
     */
    private static int nearestStronger(List<Anchor> anchors, int i, double reach, long seed) {
        Anchor self = anchors.get(i);
        int best = NO_PARENT;
        double bestDistance = Double.MAX_VALUE;
        for (int j = 0; j < anchors.size(); j++) {
            if (i == j) {
                continue;
            }
            Anchor other = anchors.get(j);
            if (!AnchorSelector.beats(seed, other, self)) {
                continue;
            }
            double d = distance(self, other);
            if (d > reach) {
                continue;
            }
            if (d < bestDistance) {
                bestDistance = d;
                best = j;
            }
        }
        return best;
    }

    private static double distance(Anchor a, Anchor b) {
        return a.position().horizontalDistanceTo(b.position());
    }

    private static boolean inCore(Anchor a) {
        return Math.abs(a.position().x()) <= CORE_HALF && Math.abs(a.position().z()) <= CORE_HALF;
    }

    private static double percentile(List<Double> values, int percent) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int index = (int) Math.round((percent / 100.0) * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    /** {@code base} with one dial changed, so every other parameter is provably identical. */
    private static AnchorParameters withStep(AnchorParameters base, int sampleStep) {
        return new AnchorParameters(
                base.cellSize(),
                base.jitterFraction(),
                base.suppressionRadius(),
                base.measurementRadius(),
                sampleStep,
                base.influenceRadius(),
                base.crossingTolerance());
    }

    private static String interpret(Forests flat, Forests slope, Forests real) {
        StringBuilder out = new StringBuilder();
        Row realMin = real.at(REACHES[0]);
        out.append("reach = suppressionRadius (")
                .append(REACHES[0])
                .append(" b) yields ")
                .append(realMin == null ? -1 : realMin.edges)
                .append(" edges on real terrain. It must be 0: no two anchors are closer than\n")
                .append("the suppression radius, because `beats` is a total order and the loser of any\n")
                .append("close pair is not an anchor. A non-zero count here falsifies that argument.\n\n");

        int reach = USABLE_REACH;
        out.append("=== THE DISCRIMINATOR, at reach ").append(reach).append(" ===\n");
        out.append("Null is arithmetic, not calibrated: edges that ignore the field make the angle\n")
                .append("between edge and uphill uniform on [0,180], so meanAngle 90 and uphill 0.50.\n\n");
        for (int gradientRadius : GRADIENT_RADII) {
            Alignment flatA = flat.alignmentAt(reach, gradientRadius);
            Alignment slopeA = slope.alignmentAt(reach, gradientRadius);
            Alignment realA = real.alignmentAt(reach, gradientRadius);
            if (flatA == null || slopeA == null || realA == null) {
                out.append("  gradient radius ").append(gradientRadius).append(": not computed\n");
                continue;
            }
            out.append(String.format("  gradient radius %d b%n", gradientRadius));
            out.append(String.format(
                    "    flat  meanAngle %5.1f  uphill %s   (defined %d, undefined %d)%n",
                    flatA.meanAngle, frac(flatA), flatA.defined, flatA.undefined));
            out.append(String.format(
                    "    ramp  meanAngle %5.1f  uphill %s   (defined %d, undefined %d)%n",
                    slopeA.meanAngle, frac(slopeA), slopeA.defined, slopeA.undefined));
            out.append(String.format(
                    "    real  meanAngle %5.1f  uphill %s   (defined %d, undefined %d)%n",
                    realA.meanAngle, frac(realA), realA.defined, realA.undefined));
        }
        out.append("\nThe flat row should be almost entirely UNDEFINED: a field with no gradient has no\n")
                .append("uphill, so the metric must decline to answer rather than produce a figure. A\n")
                .append("number there would mean the plane fit is reading noise, and would invalidate the\n")
                .append("real row too.\n");
        out.append("\nIf the two gradient radii disagree, the answer is about the smoothing scale and\n")
                .append("not about the forest -- which is the mistake both earlier probes in this session\n")
                .append("made by reading a single scale.\n");

        out.append(String.format(
                "%nnearest-stronger distance on real terrain: p10 %.0f  p50 %.0f  p90 %.0f  max %.0f b%n",
                real.nearestStrongerP10, real.nearestStrongerP50, real.nearestStrongerP90, real.nearestStrongerMax));
        out.append("That distribution is what sets `reach`: a reach below p90 leaves a tenth of\n")
                .append("anchors as sources for want of a parent they have.\n");
        return out.toString();
    }

    private static String frac(Alignment a) {
        return Double.isNaN(a.uphillFraction())
                ? "-"
                : String.format("%.2f+-%.2f", a.uphillFraction(), a.nullStandardError());
    }

    private static String verdict(Forests flat, Forests slope, Forests real) {
        Row realMin = real.at(REACHES[0]);
        if (realMin == null) {
            return "UNTESTED | the floor reach row is missing";
        }
        if (real.coreAnchors == 0) {
            return "UNTESTED | no anchor in the core region";
        }
        for (Forests f : List.of(flat, slope, real)) {
            for (Row r : f.rows) {
                if (r.cycleSuspected) {
                    return "FAILED | a parent walk exceeded the anchor count, so the forest has a cycle";
                }
            }
        }
        Alignment flatA = flat.alignmentAt(USABLE_REACH, GRADIENT_RADII[0]);
        Alignment slopeA = slope.alignmentAt(USABLE_REACH, GRADIENT_RADII[0]);
        Alignment realA = real.alignmentAt(USABLE_REACH, GRADIENT_RADII[0]);
        if (flatA == null || slopeA == null || realA == null) {
            return "UNTESTED | the alignment rows are missing";
        }
        // The flat control must DECLINE to answer. If it produces a direction, the plane fit is
        // reading noise and the real row is not evidence -- so this is a gate, not a reported stat.
        if (flatA.defined > flatA.undefined) {
            return String.format(
                    "CONTROL_FAILED | flat field produced %d defined gradients against %d undefined; "
                            + "a field with no gradient has no uphill, so the fit is reading noise",
                    flatA.defined, flatA.undefined);
        }
        String floorClaim = realMin.edges == 0 ? "HOLDS" : "FALSIFIED";
        return String.format(
                "MEASURED | core anchors %d | reach=%d floor %s | reach=%d gradR=%d: ramp %.1fdeg/%.2f real %.1fdeg/%.2f (null 90deg/0.50, n=%d) | flat declined %d/%d | nearestStronger p50 %.0f p90 %.0f b",
                real.coreAnchors,
                REACHES[0],
                floorClaim,
                USABLE_REACH,
                GRADIENT_RADII[0],
                slopeA.meanAngle,
                slopeA.uphillFraction(),
                realA.meanAngle,
                realA.uphillFraction(),
                realA.defined,
                flatA.undefined,
                flatA.defined + flatA.undefined,
                real.nearestStrongerP50,
                real.nearestStrongerP90);
    }

    /** One terrain's anchor set and its forest at every reach. */
    private static final class Forests {
        int eligible;
        int anchors;
        int coreAnchors;
        int noStrongerAnywhere;
        double nearestStrongerP10 = Double.NaN;
        double nearestStrongerP50 = Double.NaN;
        double nearestStrongerP90 = Double.NaN;
        double nearestStrongerMax = Double.NaN;
        final List<Row> rows = new ArrayList<>();

        Row at(int reach) {
            for (Row r : rows) {
                if (r.reach == reach) {
                    return r;
                }
            }
            return null;
        }

        String render() {
            StringBuilder out = new StringBuilder();
            out.append("eligible ")
                    .append(eligible)
                    .append("   anchors ")
                    .append(anchors)
                    .append("   in core ")
                    .append(coreAnchors)
                    .append("   core anchors with nothing stronger in the whole halo ")
                    .append(noStrongerAnywhere)
                    .append('\n');
            out.append(String.format(
                    "nearest-stronger: p10 %.0f  p50 %.0f  p90 %.0f  max %.0f b%n",
                    nearestStrongerP10, nearestStrongerP50, nearestStrongerP90, nearestStrongerMax));
            out.append(String.format(
                    "%-7s %6s %8s %8s %10s %9s %10s%n",
                    "reach", "core", "edges", "sources", "medEdge", "maxDepth", "coherence"));
            for (Row r : rows) {
                out.append(r.render());
            }
            out.append("\nedge agreement with the local strength gradient -- the discriminator.\n")
                    .append("meanAngle 90 and uphill 0.50 are the arithmetic null: edges ignoring the field.\n")
                    .append("Lower angle and higher fraction mean the forest follows the field.\n");
            out.append(String.format(
                    "%-7s %7s %9s %11s %11s %10s %9s%n",
                    "reach", "gradR", "defined", "undefined", "meanAngle", "medAngle", "uphill"));
            for (Row r : rows) {
                for (Alignment a : r.alignments) {
                    out.append(a.render(r.reach));
                }
            }
            return out.toString();
        }

        /** The alignment at one reach and one gradient radius, or null. */
        Alignment alignmentAt(int reach, int gradientRadius) {
            Row row = at(reach);
            if (row == null) {
                return null;
            }
            for (Alignment a : row.alignments) {
                if (a.gradientRadius == gradientRadius) {
                    return a;
                }
            }
            return null;
        }
    }

    /** One edge set's agreement with the local strength gradient. */
    private static final class Alignment {
        int gradientRadius;
        int defined;
        int undefined;
        int uphill;
        double meanAngle = Double.NaN;
        double medianAngle = Double.NaN;

        /** Fraction of edges pointing into the uphill half-plane. Arithmetic null: 0.5. */
        double uphillFraction() {
            return defined == 0 ? Double.NaN : (double) uphill / defined;
        }

        /** One standard error of {@link #uphillFraction()} under the null, so n is visible. */
        double nullStandardError() {
            return defined == 0 ? Double.NaN : 0.5 / Math.sqrt(defined);
        }

        String render(int reach) {
            return String.format(
                    "%-7d %7d %9d %11d %11s %10s %9s%n",
                    reach,
                    gradientRadius,
                    defined,
                    undefined,
                    fmt(meanAngle),
                    fmt(medianAngle),
                    Double.isNaN(uphillFraction())
                            ? "-"
                            : String.format("%.2f+-%.2f", uphillFraction(), nullStandardError()));
        }

        private static String fmt(double value) {
            return Double.isNaN(value) ? "-" : String.format("%.1f", value);
        }
    }

    /** One reach's forest statistics, over core anchors. */
    private static final class Row {
        int reach;
        int[] parents = new int[0];
        final List<Alignment> alignments = new ArrayList<>();
        int core;
        int edges;
        int sources;
        int maxDepth;
        boolean cycleSuspected;
        double medianEdge = Double.NaN;
        double maxEdge = Double.NaN;
        double bearingCoherence = Double.NaN;

        String render() {
            return String.format(
                    "%-7d %6d %8d %8d %10s %9d %9s%s%n",
                    reach,
                    core,
                    edges,
                    sources,
                    fmt(medianEdge),
                    maxDepth,
                    fmt(bearingCoherence) + "%",
                    cycleSuspected ? "  CYCLE SUSPECTED" : "");
        }

        private static String fmt(double value) {
            return Double.isNaN(value) ? "-" : String.format("%.0f", value);
        }
    }

    private static void bail(CommandContext context, StringBuilder sb, String why) {
        sb.append("\nDRAINAGE_VERDICT: UNTESTED | ").append(why).append('\n');
        send(context, sb.toString());
    }

    private static String describe(Throwable thrown) {
        String message = thrown.getMessage();
        return thrown.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

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

    /** Half-width of the region whose anchors are reported on. ~38 km² at this size. */
    private static final int CORE_HALF = 3072;

    /** Coarser than the shipped 8. Legitimate because strength is a volume; see the class javadoc. */
    private static final int PROBE_SAMPLE_STEP = 16;

    /** Reaches measured, in blocks. 256 is `suppressionRadius`, which must yield no edge at all. */
    private static final int[] REACHES = {256, 384, 512, 768, 1024, 1536};

    private static final int FLAT_SURFACE = 160;

    private static final int SLOPE_BASE = 96;

    /** One block of rise per 32 east: ~192 blocks of rise across the core region. */
    private static final int SLOPE_RUN = 32;

    /** Edge bearings are bucketed into this many sectors; chance agreement is 1/this. */
    private static final int BEARING_SECTORS = 8;

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

        for (int reach : REACHES) {
            out.rows.add(forestAt(anchors, reach, seed));
        }
        return out;
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
        Row flatMid = flat.at(512);
        Row slopeMid = slope.at(512);
        Row realMid = real.at(512);
        if (flatMid == null || slopeMid == null || realMid == null) {
            return "a reach row is missing; nothing to compare.\n";
        }
        Row realMin = real.at(REACHES[0]);
        out.append("reach = suppressionRadius (")
                .append(REACHES[0])
                .append(" b) yields ")
                .append(realMin == null ? -1 : realMin.edges)
                .append(" edges on real terrain. It must be 0: no two anchors are closer than\n")
                .append("the suppression radius, because `beats` is a total order and the loser of any\n")
                .append("close pair is not an anchor. A non-zero count here falsifies that argument.\n\n");
        out.append(String.format(
                "bearing coherence at reach 512, the discriminator:%n  flat %.1f%%   ramp %.1f%%   real %.1f%%   (chance %.1f%%)%n",
                flatMid.bearingCoherence,
                slopeMid.bearingCoherence,
                realMid.bearingCoherence,
                100.0 / BEARING_SECTORS));
        out.append("Real terrain near the flat figure would mean the forest follows the hash rather\n")
                .append("than the field, which is the degeneracy. Near the ramp figure means it follows\n")
                .append("the field. Depth alone cannot separate those, which is why it is not the test.\n\n");
        out.append(String.format(
                "nearest-stronger distance on real terrain: p10 %.0f  p50 %.0f  p90 %.0f  max %.0f b%n",
                real.nearestStrongerP10, real.nearestStrongerP50, real.nearestStrongerP90, real.nearestStrongerMax));
        out.append("That distribution is what sets `reach`: a reach below p90 leaves a tenth of\n")
                .append("anchors as sources for want of a parent they have.\n");
        return out.toString();
    }

    private static String verdict(Forests flat, Forests slope, Forests real) {
        Row flatMid = flat.at(512);
        Row slopeMid = slope.at(512);
        Row realMid = real.at(512);
        Row realMin = real.at(REACHES[0]);
        if (flatMid == null || slopeMid == null || realMid == null || realMin == null) {
            return "UNTESTED | a reach row is missing";
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
        String floorClaim = realMin.edges == 0 ? "HOLDS" : "FALSIFIED";
        return String.format(
                "MEASURED | core anchors %d | reach=%d floor %s | coherence flat %.1f%% ramp %.1f%% real %.1f%% (chance %.1f%%) | nearestStronger p50 %.0f p90 %.0f b | real sources %d/%d at reach 512",
                real.coreAnchors,
                REACHES[0],
                floorClaim,
                flatMid.bearingCoherence,
                slopeMid.bearingCoherence,
                realMid.bearingCoherence,
                100.0 / BEARING_SECTORS,
                real.nearestStrongerP50,
                real.nearestStrongerP90,
                realMid.sources,
                realMid.core);
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
            return out.toString();
        }
    }

    /** One reach's forest statistics, over core anchors. */
    private static final class Row {
        int reach;
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

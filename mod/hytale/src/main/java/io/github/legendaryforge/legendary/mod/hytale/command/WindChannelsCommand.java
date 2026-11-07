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
import io.github.legendaryforge.legendary.mod.hytale.residue.GeneratorTerrainSample;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Debug command: {@code /windchannels}
 *
 * <p>Answers one question, and it is an operator's: <strong>does this generator produce laterally
 * constricted channels — ravines, gorges, saddles — in enough quantity and with enough spatial
 * coherence to carry a wind network?</strong>
 *
 * <p><strong>Whose question, and what it does not answer.</strong> Wind's shipped grounding is
 * {@code Geometric} with {@code Occupancy.OPEN} over the whole column, which its own javadoc records
 * as reading <em>"strongest over deep water"</em> — so wind anchors currently favour ocean floors.
 * The proposal on the table is to ground Wind in <em>constriction</em> instead, because air
 * concentrates where terrain funnels it. That proposal is only worth designing if the terrain has
 * channels to funnel through, and nobody has looked.
 *
 * <p><strong>It cannot see tunnels or caves, and that is structural.</strong>
 * {@link GeneratorTerrainSample} answers solidity as {@code y <= height}, so a hole under the
 * surface is rock by construction. Everything below is a heightmap measurement: ravines, gorges and
 * passes are visible in a heightmap and caves are not, at any effort. Reading a low channel count
 * here as "the terrain has no wind channels" would be wrong in exactly one direction — subsurface
 * channels are invisible, not absent.
 *
 * <p><strong>The measure.</strong> For a column at height {@code h}, probe the eight neighbours at
 * ring distance {@code d} and take four opposite pairs as axes. An axis's <em>wall</em> is
 * {@code min(h(a), h(b)) - h}: positive means the ground rises on both sides, so the column is
 * enclosed along that axis. Then every column is exactly one of three things, and the classification
 * is exhaustive on purpose — a count that does not account for every column is not a count:
 *
 * <ul>
 *   <li><strong>OPEN</strong> — no axis walled. Open sky, plain, hilltop.
 *   <li><strong>BOWL</strong> — every axis walled. A pit or basin: enclosed, but nothing flows
 *       through it.
 *   <li><strong>CHANNEL</strong> — some axes walled and some not. Air entering along the open axis
 *       has nowhere to spread. The least-walled axis is the flow direction, so this measure yields a
 *       <em>direction</em> as well as a strength, which is the property a current needs.
 * </ul>
 *
 * <p><strong>Proxy, stated before the result is read.</strong> Two of the four axes are the cardinal
 * pairs at true distance {@code d}; the two diagonal pairs sample at {@code d * sqrt(2)}. So a
 * diagonal channel is measured slightly wider than a cardinal one of the same width. Diagonals are
 * probed at all because an axis-aligned test alone misclassifies a 45-degree ravine as a BOWL — both
 * cardinal probes exit it — which would have suppressed exactly the feature being counted.
 *
 * <p><strong>Three controls, and the positive one is the load-bearing one.</strong> A probe reporting
 * near-zero channels on real terrain is ambiguous between "the terrain is smooth" and "the detector
 * does not work", and this project's standing rule is that a negative among positives is ambiguous
 * while a negative on its own is not:
 *
 * <ol>
 *   <li><strong>flat</strong> — every wall is 0, so 100% OPEN. Proves the detector needs relief.
 *   <li><strong>slope</strong> — a monotone ramp. Every axis has one side above and one below, so
 *       every wall is {@code <= 0} and it is again 100% OPEN. Proves the detector needs
 *       <em>constriction</em> and not merely relief, which flat alone does not establish.
 *   <li><strong>trench</strong> — a synthetic north-south gorge, 7 blocks wide and 30 deep. Predicted
 *       exactly, not approximately: at {@code d = 8} the qualifying columns are those inside the
 *       trench whose two probes at {@code +/-8} both clear it, i.e. {@code x} in {@code -2, 0, 2} at
 *       this sample step, every one CHANNEL with the flow axis running north-south, 100% axis
 *       agreement, in one component spanning the region. If the detector cannot find a trench
 *       drawn for it, no real-terrain number below it is evidence.
 * </ol>
 *
 * <p>Prints {@code WINDCHANNEL_VERDICT:} as its last line. Read it by name.
 */
public class WindChannelsCommand extends AbstractWorldCommand {

    /** Spacing of classified columns, in blocks. Every ring distance is a multiple of it. */
    private static final int SAMPLE_STEP = 2;

    /** Half-width of each classified region: a 1024-block square. */
    private static final int CLASSIFY_HALF = 512;

    /**
     * Region centres, in blocks. Every coordinate must be a multiple of {@link #SAMPLE_STEP}.
     *
     * <p><strong>Five regions because one is not the world.</strong> A single patch measures one
     * patch, and the origin is the worst possible choice for a lone sample -- a world's spawn
     * surroundings are the most likely place for terrain to be deliberately gentle. These are tens
     * of kilometres apart so they fall in different zones, and the summary reports the spread across
     * them rather than a mean, because between-region variance is what a single figure hides.
     */
    private static final int[][] REGION_CENTRES = {
        {0, 0}, {18_000, -11_000}, {-27_000, 23_000}, {41_000, 39_000}, {-52_000, -48_000},
    };

    /** Ring distances probed, in blocks. Spans a 4-block notch to a 128-block valley floor. */
    private static final int[] RING_DISTANCES = {2, 4, 8, 16, 32, 64};

    /** Wall heights that count as enclosure, in blocks. Reported as a sweep rather than chosen. */
    private static final int[] RISE_THRESHOLDS = {4, 8, 16, 32};

    /**
     * The {@code (d, rise)} pairs the spatial analysis runs at. Fixed before the run.
     *
     * <p>Two narrow and two broad, deliberately. The first version analysed only narrow scales, so
     * it could only ever have answered "are there gorges" -- and a wind current funnelling along a
     * <em>valley</em> is a weaker but entirely real version of the same idea, invisible to a
     * narrow-only instrument. {@code (32, 8)} and {@code (64, 16)} are the valley scales.
     */
    private static final int[][] COMPONENT_AT = {{4, 8}, {8, 16}, {32, 8}, {64, 16}};

    private static final int FLAT_SURFACE = 160;

    /** One block of rise every {@code SLOPE_RUN} blocks east. */
    private static final int SLOPE_RUN = 8;

    private static final int TRENCH_SURFACE = 160;
    private static final int TRENCH_DEPTH = 30;
    private static final int TRENCH_HALF_WIDTH = 3;

    /** The ring distance the trench control's exact prediction is stated at. */
    private static final int TRENCH_ASSERT_D = 8;

    private static final int TRENCH_ASSERT_RISE = 16;

    /** A column's height, as the probe reads it. The only terrain access this probe makes. */
    @FunctionalInterface
    private interface Heights {
        int at(int x, int z);
    }

    private static final int OPEN = 0;
    private static final int CHANNEL = 1;
    private static final int BOWL = 2;

    /** No axis, for a column that is not a CHANNEL. */
    private static final int NO_AXIS = -1;

    public WindChannelsCommand() {
        super("windchannels", "Measures whether the generator produces wind-channelling terrain constrictions");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- wind channel probe ---\n");
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

        int side = 2 * CLASSIFY_HALF / SAMPLE_STEP + 1;
        sb.append("regions: ")
                .append(REGION_CENTRES.length)
                .append(", each +/-")
                .append(CLASSIFY_HALF)
                .append(" blocks at step ")
                .append(SAMPLE_STEP)
                .append("  =  ")
                .append(side)
                .append('x')
                .append(side)
                .append(" = ")
                .append(side * side)
                .append(" classified columns\n");
        sb.append("probes: 8 neighbours at ring d, taken as 4 opposite-pair axes; ")
                .append("cardinal pairs reach d, diagonal pairs d*sqrt(2)\n");
        sb.append("CANNOT SEE CAVES OR TUNNELS: solidity here is y <= height, so a hole is rock.\n");

        // CONTROLS FIRST, and the negative ones before the positive one -- a negative result read
        // among positives is ambiguous, and read alone it is not. The controls are synthetic so they
        // run at the origin only; the trench is defined by |x| <= halfWidth and must stay there.
        sb.append("\n=== CONTROL 1: flat at y=").append(FLAT_SURFACE).append(" ===\n");
        sb.append("Every wall is 0, so this MUST be 100% OPEN.\n");
        Heights flatHeights = cached((x, z) -> FLAT_SURFACE, 0, 0);
        Classification flat = classify(flatHeights, 0, 0, TRENCH_ASSERT_D, TRENCH_ASSERT_RISE);
        sb.append(flat.render("flat"));
        boolean flatOk = flat.channel == 0 && flat.bowl == 0 && flat.open == flat.total();

        sb.append("\n=== CONTROL 2: monotone slope, 1 block per ")
                .append(SLOPE_RUN)
                .append(" east ===\n");
        sb.append("Relief everywhere, constriction nowhere: every axis has one side above and one\n")
                .append("below, so every wall is <= 0. MUST also be 100% OPEN. This is the control\n")
                .append("that distinguishes 'needs relief' from 'needs constriction'.\n");
        Heights slopeHeights = cached((x, z) -> FLAT_SURFACE + Math.floorDiv(x, SLOPE_RUN), 0, 0);
        Classification slope = classify(slopeHeights, 0, 0, TRENCH_ASSERT_D, TRENCH_ASSERT_RISE);
        sb.append(slope.render("slope"));
        boolean slopeOk = slope.channel == 0 && slope.bowl == 0 && slope.open == slope.total();

        sb.append("\n=== CONTROL 3 (positive): synthetic trench, ")
                .append(2 * TRENCH_HALF_WIDTH + 1)
                .append(" blocks wide, ")
                .append(TRENCH_DEPTH)
                .append(" deep, running north-south ===\n");
        Heights trench = cached(
                (x, z) -> Math.abs(x) <= TRENCH_HALF_WIDTH ? TRENCH_SURFACE - TRENCH_DEPTH : TRENCH_SURFACE, 0, 0);
        Classification trenchResult = classify(trench, 0, 0, TRENCH_ASSERT_D, TRENCH_ASSERT_RISE);
        sb.append(trenchResult.render("trench"));

        // The prediction, computed from the trench's own geometry rather than hardcoded, so the
        // arithmetic is stated rather than asserted: a column qualifies when it is inside the
        // trench and both ring probes clear it.
        int qualifyingColumnsPerRow = 0;
        for (int x = -CLASSIFY_HALF; x <= CLASSIFY_HALF; x += SAMPLE_STEP) {
            boolean inside = Math.abs(x) <= TRENCH_HALF_WIDTH;
            boolean leftClears = Math.abs(x - TRENCH_ASSERT_D) > TRENCH_HALF_WIDTH;
            boolean rightClears = Math.abs(x + TRENCH_ASSERT_D) > TRENCH_HALF_WIDTH;
            if (inside && leftClears && rightClears) {
                qualifyingColumnsPerRow++;
            }
        }
        int predictedChannels = qualifyingColumnsPerRow * side;
        sb.append("predicted CHANNEL columns at d=")
                .append(TRENCH_ASSERT_D)
                .append(" rise=")
                .append(TRENCH_ASSERT_RISE)
                .append(": ")
                .append(qualifyingColumnsPerRow)
                .append(" per row x ")
                .append(side)
                .append(" rows = ")
                .append(predictedChannels)
                .append('\n');
        boolean trenchOk = trenchResult.channel == predictedChannels && trenchResult.bowl == 0;
        sb.append("actual: ")
                .append(trenchResult.channel)
                .append(" channel, ")
                .append(trenchResult.bowl)
                .append(" bowl  ->  ")
                .append(trenchOk ? "MATCH" : "MISMATCH")
                .append('\n');
        sb.append(components(trench, 0, 0, TRENCH_ASSERT_D, TRENCH_ASSERT_RISE).render());
        sb.append("a trench is one component spanning the region, so the longest run should be ")
                .append(2 * CLASSIFY_HALF)
                .append(" blocks at 100% axis agreement\n");

        boolean controlsOk = flatOk && slopeOk && trenchOk;
        if (!controlsOk) {
            sb.append("\nCONTROLS FAILED (flat ")
                    .append(flatOk ? "ok" : "BAD")
                    .append(", slope ")
                    .append(slopeOk ? "ok" : "BAD")
                    .append(", trench ")
                    .append(trenchOk ? "ok" : "BAD")
                    .append(") -- the real rows below are NOT evidence.\n");
        }

        // REAL TERRAIN, one region at a time.
        List<Region> regions = new ArrayList<>();
        for (int[] centre : REGION_CENTRES) {
            int cx = centre[0];
            int cz = centre[1];
            sb.append("\n=== REAL TERRAIN at (")
                    .append(cx)
                    .append(", ")
                    .append(cz)
                    .append(") ===\n");

            GeneratorTerrainSample sample = GeneratorTerrainSample.of(generator, (int) seed);
            long fetchStartedAt = System.nanoTime();
            Heights real = cached(sample::heightAt, cx, cz);
            long fetchMs = (System.nanoTime() - fetchStartedAt) / 1_000_000L;

            Region region = new Region();
            region.centreX = cx;
            region.centreZ = cz;

            long sum = 0;
            int n = 0;
            for (int x = cx - CLASSIFY_HALF; x <= cx + CLASSIFY_HALF; x += SAMPLE_STEP) {
                for (int z = cz - CLASSIFY_HALF; z <= cz + CLASSIFY_HALF; z += SAMPLE_STEP) {
                    int h = real.at(x, z);
                    region.minHeight = Math.min(region.minHeight, h);
                    region.maxHeight = Math.max(region.maxHeight, h);
                    sum += h;
                    n++;
                }
            }
            region.meanHeight = n == 0 ? 0 : (int) (sum / n);
            sb.append("columns fetched: ")
                    .append(sample.columnsCached())
                    .append(" in ")
                    .append(fetchMs)
                    .append(" ms   height min ")
                    .append(region.minHeight)
                    .append("  max ")
                    .append(region.maxHeight)
                    .append("  mean ")
                    .append(region.meanHeight)
                    .append("  relief ")
                    .append(region.relief())
                    .append(" blocks\n");

            sb.append(String.format("%-6s %7s %9s %10s %9s%n", "d", "rise", "open%", "channel%", "bowl%"));
            region.grid = new Classification[RING_DISTANCES.length][RISE_THRESHOLDS.length];
            for (int di = 0; di < RING_DISTANCES.length; di++) {
                for (int ri = 0; ri < RISE_THRESHOLDS.length; ri++) {
                    Classification c = classify(real, cx, cz, RING_DISTANCES[di], RISE_THRESHOLDS[ri]);
                    region.grid[di][ri] = c;
                    sb.append(String.format(
                            "%-6d %7d %8.2f%% %9.2f%% %8.2f%%%n",
                            RING_DISTANCES[di],
                            RISE_THRESHOLDS[ri],
                            100.0 * c.open / c.total(),
                            100.0 * c.channel / c.total(),
                            100.0 * c.bowl / c.total()));
                }
            }

            for (int[] pair : COMPONENT_AT) {
                sb.append("  spatial at d=")
                        .append(pair[0])
                        .append(" rise=")
                        .append(pair[1])
                        .append('\n');
                Spatial sp = components(real, cx, cz, pair[0], pair[1]);
                region.spatials.add(sp);
                sb.append(sp.render());
            }
            regions.add(region);
        }

        sb.append('\n').append(summary(regions));
        sb.append('\n').append(verdictBody(controlsOk, regions));
        sb.append("\nWINDCHANNEL_VERDICT: ")
                .append(verdict(controlsOk, regions))
                .append('\n');
        send(context, sb.toString());
    }

    /** Index of {@code value} in {@code values}, or {@code -1}. */
    private static int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** One line per region at each analysed scale -- the table a reader actually compares. */
    private static String summary(List<Region> regions) {
        StringBuilder out = new StringBuilder("=== CROSS-REGION SUMMARY ===\n");
        out.append("A wind network needs long coherent runs. Compare 'longest' against the trench\n")
                .append("control's 1024 b at 100% agreement, and note that 25% agreement is chance\n")
                .append("with four axes.\n");
        out.append(String.format(
                "%-20s %7s %8s %7s %9s %8s %9s%n",
                "region", "relief", "scale", "chan%", "longest", "axisAgr", "singles"));
        for (Region r : regions) {
            for (int k = 0; k < COMPONENT_AT.length && k < r.spatials.size(); k++) {
                int d = COMPONENT_AT[k][0];
                int rise = COMPONENT_AT[k][1];
                Classification c = r.classificationAt(d, rise);
                Spatial sp = r.spatials.get(k);
                out.append(String.format(
                        "%-20s %7d %8s %6.2f%% %8d %7.0f%% %4d/%-4d%n",
                        k == 0 ? "(" + r.centreX + "," + r.centreZ + ")" : "",
                        k == 0 ? r.relief() : 0,
                        "d" + d + "/r" + rise,
                        c == null ? Double.NaN : 100.0 * c.channel / c.total(),
                        sp.longestRunBlocks(),
                        sp.largestAgreement(),
                        sp.singleColumnComponents(),
                        sp.components.size()));
            }
        }
        return out.toString();
    }

    private static String verdictBody(boolean controlsOk, List<Region> regions) {
        if (!controlsOk) {
            return "Controls failed, so nothing above is evidence about the terrain.\n";
        }
        StringBuilder out =
                new StringBuilder("controls OK: flat and slope both 100% OPEN, and the trench matched its predicted\n"
                        + "channel count exactly -- so the detector needs constriction and finds it when present.\n\n");
        for (int k = 0; k < COMPONENT_AT.length; k++) {
            int d = COMPONENT_AT[k][0];
            int rise = COMPONENT_AT[k][1];
            int longest = 0;
            double bestAgreement = 0;
            for (Region r : regions) {
                if (k < r.spatials.size()) {
                    longest = Math.max(longest, r.spatials.get(k).longestRunBlocks());
                    bestAgreement = Math.max(bestAgreement, r.spatials.get(k).largestAgreement());
                }
            }
            out.append(String.format(
                    "d=%d rise=%d: best run across %d regions %d blocks, best axis agreement %.0f%%%n",
                    d, rise, regions.size(), longest, bestAgreement));
        }
        out.append("\nRead the runs, not the percentages: scattered hits are not a network however\n")
                .append("many of them there are. And remember this is a surface measurement -- a\n")
                .append("subsurface channel is invisible here, not absent.\n");
        return out.toString();
    }

    private static String verdict(boolean controlsOk, List<Region> regions) {
        if (!controlsOk) {
            return "CONTROL_FAILED | the detector did not pass its own controls";
        }
        if (regions.isEmpty()) {
            return "UNTESTED | no region measured";
        }
        StringBuilder out = new StringBuilder("MEASURED | controls OK | regions " + regions.size());
        for (int k = 0; k < COMPONENT_AT.length; k++) {
            int d = COMPONENT_AT[k][0];
            int rise = COMPONENT_AT[k][1];
            int longest = 0;
            double bestAgreement = 0;
            for (Region r : regions) {
                if (k < r.spatials.size()) {
                    longest = Math.max(longest, r.spatials.get(k).longestRunBlocks());
                    bestAgreement = Math.max(bestAgreement, r.spatials.get(k).largestAgreement());
                }
            }
            out.append(String.format(" | d%d/r%d best run %db agree %.0f%%", d, rise, longest, bestAgreement));
        }
        return out.toString();
    }

    /** One region's measurements. */
    private static final class Region {
        int centreX;
        int centreZ;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int meanHeight;
        Classification[][] grid;
        final List<Spatial> spatials = new ArrayList<>();

        int relief() {
            return maxHeight - minHeight;
        }

        Classification classificationAt(int d, int rise) {
            int di = indexOf(RING_DISTANCES, d);
            int ri = indexOf(RISE_THRESHOLDS, rise);
            return grid == null || di < 0 || ri < 0 ? null : grid[di][ri];
        }
    }

    /** The largest ring distance probed, which is how much halo the grid needs. */
    private static int maxRing() {
        int max = 0;
        for (int d : RING_DISTANCES) {
            max = Math.max(max, d);
        }
        return max;
    }

    /**
     * Materialises the padded height grid once, so the sweep is array reads.
     *
     * <p>Not an optimisation for its own sake: the sweep classifies every column at five ring
     * distances and four rise thresholds, and each classification probes eight neighbours, so an
     * uncached run makes tens of millions of reads and risks outrunning the driver's wait. The grid
     * is padded by {@link #maxRing()} so every probe lands inside it, and a probe that leaves it
     * throws rather than returning a plausible height.
     */
    private static Heights cached(Heights source, int centreX, int centreZ) {
        if (centreX % SAMPLE_STEP != 0 || centreZ % SAMPLE_STEP != 0) {
            throw new IllegalArgumentException("region centre must be a multiple of the sample step");
        }
        int pad = maxRing();
        int loX = centreX - (CLASSIFY_HALF + pad);
        int loZ = centreZ - (CLASSIFY_HALF + pad);
        int side = 2 * (CLASSIFY_HALF + pad) / SAMPLE_STEP + 1;
        int[][] grid = new int[side][side];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                grid[i][j] = source.at(loX + i * SAMPLE_STEP, loZ + j * SAMPLE_STEP);
            }
        }
        return (x, z) -> {
            int i = (x - loX) / SAMPLE_STEP;
            int j = (z - loZ) / SAMPLE_STEP;
            if (i < 0 || j < 0 || i >= side || j >= side) {
                throw new IllegalStateException("probe left the padded grid at " + x + "," + z);
            }
            return grid[i][j];
        };
    }

    /** Every classified column's class at one {@code (d, rise)}. Exhaustive by construction. */
    private static Classification classify(Heights heights, int centreX, int centreZ, int d, int rise) {
        Classification out = new Classification();
        for (int x = centreX - CLASSIFY_HALF; x <= centreX + CLASSIFY_HALF; x += SAMPLE_STEP) {
            for (int z = centreZ - CLASSIFY_HALF; z <= centreZ + CLASSIFY_HALF; z += SAMPLE_STEP) {
                switch (classOf(heights, x, z, d, rise)) {
                    case OPEN -> out.open++;
                    case CHANNEL -> out.channel++;
                    default -> out.bowl++;
                }
            }
        }
        return out;
    }

    /**
     * One column's class, and nothing else reads terrain.
     *
     * <p>{@code min(h(a), h(b)) - h} per axis: positive means both sides rise, so the column is
     * enclosed along it.
     */
    private static int classOf(Heights heights, int x, int z, int d, int rise) {
        int h = heights.at(x, z);
        int minWall = Integer.MAX_VALUE;
        int maxWall = Integer.MIN_VALUE;
        for (int axis = 0; axis < 4; axis++) {
            int wall = wallOf(heights, x, z, d, axis, h);
            minWall = Math.min(minWall, wall);
            maxWall = Math.max(maxWall, wall);
        }
        if (maxWall < rise) {
            return OPEN;
        }
        if (minWall >= rise) {
            return BOWL;
        }
        return CHANNEL;
    }

    /** The least-walled axis, which is the direction air would flow. {@code -1} when not a channel. */
    private static int flowAxisOf(Heights heights, int x, int z, int d, int rise) {
        int h = heights.at(x, z);
        int minWall = Integer.MAX_VALUE;
        int maxWall = Integer.MIN_VALUE;
        int best = NO_AXIS;
        for (int axis = 0; axis < 4; axis++) {
            int wall = wallOf(heights, x, z, d, axis, h);
            if (wall < minWall) {
                minWall = wall;
                best = axis;
            }
            maxWall = Math.max(maxWall, wall);
        }
        if (maxWall < rise || minWall >= rise) {
            return NO_AXIS;
        }
        return best;
    }

    private static int wallOf(Heights heights, int x, int z, int d, int axis, int centre) {
        int dx;
        int dz;
        switch (axis) {
            case 0 -> {
                dx = d;
                dz = 0;
            }
            case 1 -> {
                dx = 0;
                dz = d;
            }
            case 2 -> {
                dx = d;
                dz = d;
            }
            default -> {
                dx = d;
                dz = -d;
            }
        }
        int a = heights.at(x - dx, z - dz);
        int b = heights.at(x + dx, z + dz);
        return Math.min(a, b) - centre;
    }

    /** Connected components of CHANNEL columns, with their length, thickness and axis agreement. */
    private static Spatial components(Heights heights, int centreX, int centreZ, int d, int rise) {
        int side = 2 * CLASSIFY_HALF / SAMPLE_STEP + 1;
        int[][] axes = new int[side][side];
        boolean[][] isChannel = new boolean[side][side];
        int channelCells = 0;
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int x = centreX - CLASSIFY_HALF + i * SAMPLE_STEP;
                int z = centreZ - CLASSIFY_HALF + j * SAMPLE_STEP;
                int axis = flowAxisOf(heights, x, z, d, rise);
                axes[i][j] = axis;
                isChannel[i][j] = axis != NO_AXIS;
                if (axis != NO_AXIS) {
                    channelCells++;
                }
            }
        }

        Spatial out = new Spatial();
        out.channelCells = channelCells;
        boolean[][] seen = new boolean[side][side];
        List<Component> found = new ArrayList<>();
        int[] di = {1, -1, 0, 0};
        int[] dj = {0, 0, 1, -1};
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                if (!isChannel[i][j] || seen[i][j]) {
                    continue;
                }
                Component c = new Component();
                c.minI = i;
                c.maxI = i;
                c.minJ = j;
                c.maxJ = j;
                int[] axisCounts = new int[4];
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[] {i, j});
                seen[i][j] = true;
                while (!queue.isEmpty()) {
                    int[] cell = queue.poll();
                    int ci = cell[0];
                    int cj = cell[1];
                    c.size++;
                    axisCounts[axes[ci][cj]]++;
                    c.minI = Math.min(c.minI, ci);
                    c.maxI = Math.max(c.maxI, ci);
                    c.minJ = Math.min(c.minJ, cj);
                    c.maxJ = Math.max(c.maxJ, cj);
                    for (int k = 0; k < 4; k++) {
                        int ni = ci + di[k];
                        int nj = cj + dj[k];
                        if (ni < 0 || nj < 0 || ni >= side || nj >= side) {
                            continue;
                        }
                        if (isChannel[ni][nj] && !seen[ni][nj]) {
                            seen[ni][nj] = true;
                            queue.add(new int[] {ni, nj});
                        }
                    }
                }
                int modal = 0;
                for (int a = 1; a < 4; a++) {
                    if (axisCounts[a] > axisCounts[modal]) {
                        modal = a;
                    }
                }
                c.axisAgreement = c.size == 0 ? 0 : 100.0 * axisCounts[modal] / c.size;
                c.modalAxis = modal;
                found.add(c);
            }
        }
        found.sort(Comparator.comparingInt((Component c) -> c.size).reversed());
        out.components = found;
        return out;
    }

    private static String verdictBody(boolean controlsOk, Classification[][] grid, List<Spatial> spatials) {
        if (!controlsOk) {
            return "Controls failed, so nothing above is evidence about the terrain.\n";
        }
        StringBuilder out = new StringBuilder("controls OK: flat and slope both 100% OPEN, trench matched its\n"
                + "predicted channel count exactly -- so the detector needs constriction and finds it.\n\n");
        Classification narrow = grid[1][1];
        out.append(String.format(
                "narrow channels (d=%d, rise=%d): %.2f%% of columns%n",
                RING_DISTANCES[1], RISE_THRESHOLDS[1], 100.0 * narrow.channel / narrow.total()));
        if (!spatials.isEmpty()) {
            Spatial first = spatials.get(0);
            out.append(String.format(
                    "longest run there: %d blocks, %d components, %.0f%% axis agreement in the largest%n",
                    first.longestRunBlocks(), first.components.size(), first.largestAgreement()));
        }
        out.append("\nRead the spatial section, not the percentage: a network needs runs, not hits.\n");
        return out.toString();
    }

    private static String verdict(boolean controlsOk, Classification[][] grid, List<Spatial> spatials) {
        if (!controlsOk) {
            return "CONTROL_FAILED | the detector did not pass its own controls";
        }
        if (spatials.isEmpty()) {
            return "UNTESTED | no spatial analysis ran";
        }
        Classification narrow = grid[1][1];
        Spatial first = spatials.get(0);
        return String.format(
                "MEASURED | controls OK | d=%d rise=%d channel %.2f%% | components %d | longest run %d b | axis agree %.0f%%",
                RING_DISTANCES[1],
                RISE_THRESHOLDS[1],
                100.0 * narrow.channel / narrow.total(),
                first.components.size(),
                first.longestRunBlocks(),
                first.largestAgreement());
    }

    /** One {@code (d, rise)}'s exhaustive class counts. */
    private static final class Classification {
        int open;
        int channel;
        int bowl;

        int total() {
            return open + channel + bowl;
        }

        String render(String label) {
            int t = total();
            return String.format(
                    "%-8s open %d (%.2f%%)   channel %d (%.2f%%)   bowl %d (%.2f%%)   total %d%n",
                    label,
                    open,
                    t == 0 ? 0 : 100.0 * open / t,
                    channel,
                    t == 0 ? 0 : 100.0 * channel / t,
                    bowl,
                    t == 0 ? 0 : 100.0 * bowl / t,
                    t);
        }
    }

    private static final class Component {
        int size;
        int minI;
        int maxI;
        int minJ;
        int maxJ;
        int modalAxis;
        double axisAgreement;

        int lengthCells() {
            return Math.max(maxI - minI, maxJ - minJ) + 1;
        }

        int lengthBlocks() {
            return (lengthCells() - 1) * SAMPLE_STEP;
        }

        double thickness() {
            return (double) size / lengthCells();
        }
    }

    private static final class Spatial {
        int channelCells;
        List<Component> components = List.of();

        int longestRunBlocks() {
            int best = 0;
            for (Component c : components) {
                best = Math.max(best, c.lengthBlocks());
            }
            return best;
        }

        double largestAgreement() {
            return components.isEmpty() ? 0 : components.get(0).axisAgreement;
        }

        int singleColumnComponents() {
            int singles = 0;
            for (Component c : components) {
                if (c.size == 1) {
                    singles++;
                }
            }
            return singles;
        }

        String render() {
            StringBuilder out = new StringBuilder();
            out.append("channel columns: ")
                    .append(channelCells)
                    .append("   components: ")
                    .append(components.size())
                    .append('\n');
            if (components.isEmpty()) {
                return out.append("  none\n").toString();
            }
            out.append(String.format(
                    "  %-6s %8s %9s %10s %9s %6s%n", "rank", "cells", "length(b)", "thickness", "axisAgr", "axis"));
            int shown = Math.min(6, components.size());
            for (int i = 0; i < shown; i++) {
                Component c = components.get(i);
                out.append(String.format(
                        "  %-6d %8d %9d %10.2f %8.0f%% %6s%n",
                        i + 1, c.size, c.lengthBlocks(), c.thickness(), c.axisAgreement, axisName(c.modalAxis)));
            }
            int singles = 0;
            for (Component c : components) {
                if (c.size == 1) {
                    singles++;
                }
            }
            out.append("  single-column components: ")
                    .append(singles)
                    .append(" of ")
                    .append(components.size())
                    .append("  (these are noise, not channels)\n");
            return out.toString();
        }
    }

    private static String axisName(int axis) {
        return switch (axis) {
            case 0 -> "E-W";
            case 1 -> "N-S";
            case 2 -> "NE-SW";
            default -> "NW-SE";
        };
    }

    private static void bail(CommandContext context, StringBuilder sb, String why) {
        sb.append("\nWINDCHANNEL_VERDICT: UNTESTED | ").append(why).append('\n');
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

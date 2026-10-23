package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.Optional;
import org.joml.Vector3d;

/**
 * Debug command: {@code /worldgenheight}
 *
 * <p>Two measurements that together decide whether a generation-time residue derivation is
 * affordable and whether it would describe the right terrain.
 *
 * <p><strong>Fidelity.</strong> {@code ChunkGenerator.generateHeight(seed, x, z)} answers for
 * ungenerated positions, but a worldgen pipeline runs populators after the base heightmap — caves,
 * water, prefabs — so the number it returns may describe terrain that no longer exists by the time a
 * player stands on it. Compared here against the <em>topmost solid block actually present</em>, found
 * by scanning the loaded chunk downward. That is ground truth for what a player walks on, which is
 * the thing the derivation has to agree with.
 *
 * <p><strong>Several columns, not one.</strong> A single column can agree by coincidence — the
 * previous probe's apparent 159-against-160 match was exactly that, measured against a stub that
 * returns the player's own elevation for every column rather than a surface. Offsets spread the test
 * across terrain of different heights, and a per-column delta column makes a systematic offset
 * distinguishable from noise.
 *
 * <p><strong>Throughput.</strong> The cost model for the whole plan scales linearly in this number
 * and the only figures so far are six isolated calls spanning 0.6–2.9ms, which is not a throughput
 * measurement. Cold and warm passes over the same coordinates separate real computation from the
 * generator's internal caching, and the two window shapes price the sampling-step decision directly
 * rather than by extrapolation.
 *
 * <p><strong>Every throughput block uses fresh coordinates.</strong> Reusing a centre would measure
 * the cache rather than the generator, which is the one mistake that would make this probe report a
 * cost the real workload never pays.
 *
 * <p>Prints {@code WORLDGENHEIGHT_VERDICT:} as its last line.
 */
public class WorldGenHeightCommand extends AbstractWorldCommand {

    /** Columns probed for fidelity, as offsets from the player. Spread to cross varied terrain. */
    private static final int[][] FIDELITY_OFFSETS = {
        {0, 0}, {16, 0}, {-16, 0}, {0, 64}, {64, 64}, {-64, -64}, {128, -128},
    };

    /** Each throughput block starts here-plus-this, so no block reuses another's cached columns. */
    private static final int COLD_GRID_ORIGIN = 200_000;

    private static final int WARM_GRID_SAMPLES = 200;

    /** Prime-ish stride so grid coordinates do not align with chunk or cell boundaries. */
    private static final int GRID_STRIDE = 97;

    /** Abandon remaining blocks past this, and say so. The world thread is shared. */
    private static final long BUDGET_MS = 20_000;

    public WorldGenHeightCommand() {
        super("worldgenheight", "Validates generateHeight against real terrain and measures its throughput");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- worldgen height fidelity + throughput ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        ChunkGenerator generator;
        long seedLong;
        try {
            WorldConfig config = world.getWorldConfig();
            seedLong = config.getSeed();
            IWorldGen raw = config.getWorldGenProvider().getGenerator();
            if (!(raw instanceof ChunkGenerator cg)) {
                sb.append("generator is ")
                        .append(raw == null ? "null" : raw.getClass().getName())
                        .append(", not a ChunkGenerator\n")
                        .append("WORLDGENHEIGHT_VERDICT: BLOCKED | wrong generator type\n");
                send(context, sb.toString());
                return;
            }
            generator = cg;
        } catch (Exception e) {
            sb.append("could not obtain generator: ").append(describe(e)).append('\n');
            sb.append("WORLDGENHEIGHT_VERDICT: BLOCKED | generator unreachable\n");
            send(context, sb.toString());
            return;
        }
        int seed = (int) seedLong;
        sb.append("seed: ")
                .append(seedLong)
                .append("  (int form used by the cache: ")
                .append(seed)
                .append(")\n");

        var playerRefs = world.getPlayerRefs();
        int playerX = 0;
        int playerY = 0;
        int playerZ = 0;
        String who = null;
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            Vector3d pos = transform.getPosition();
            who = playerRef.getUsername();
            playerX = MathUtil.floor(pos.x);
            playerY = MathUtil.floor(pos.y);
            playerZ = MathUtil.floor(pos.z);
            break;
        }

        long started = System.nanoTime();

        // ---- Fidelity. Needs loaded chunks, so it needs a player.
        if (who == null) {
            sb.append("\nFIDELITY SKIPPED: no player, so no loaded chunk to read true terrain from.\n");
        } else {
            SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);
            sb.append("\nanchor: ")
                    .append(who)
                    .append(" at ")
                    .append(playerX)
                    .append(',')
                    .append(playerY)
                    .append(',')
                    .append(playerZ)
                    .append('\n');
            sb.append("FIDELITY -- generateHeight against the topmost solid block actually present.\n");
            sb.append(String.format(
                    "%12s %9s %10s %10s %7s  %s%n", "column", "genHeight", "topSolid", "topFluid", "delta", "note"));

            int compared = 0;
            int agreeing = 0;
            int deltaSum = 0;
            for (int[] offset : FIDELITY_OFFSETS) {
                int x = playerX + offset[0];
                int z = playerZ + offset[1];
                int genHeight;
                try {
                    genHeight = generator.generateHeight(seed, x, z);
                } catch (Throwable t) {
                    sb.append(String.format(
                            "%12s %9s %10s %10s %7s  %s%n", x + "," + z, "ERR", "-", "-", "-", describe(t)));
                    continue;
                }
                int topSolid = topmostSolid(sample, x, z);
                int topFluid = topmostFluid(sample, x, z);
                if (topSolid < 0) {
                    sb.append(String.format(
                            "%12s %9d %10s %10s %7s  %s%n",
                            x + "," + z, genHeight, "unloaded", "-", "-", "column not resident; no ground truth"));
                    continue;
                }
                int delta = genHeight - topSolid;
                compared++;
                deltaSum += delta;
                if (delta == 0) {
                    agreeing++;
                }
                sb.append(String.format(
                        "%12s %9d %10d %10s %7d  %s%n",
                        x + "," + z,
                        genHeight,
                        topSolid,
                        topFluid < 0 ? "none" : Integer.toString(topFluid),
                        delta,
                        delta == 0 ? "exact" : (topFluid >= 0 ? "submerged column" : "")));
            }
            if (compared == 0) {
                sb.append("no column had ground truth; fidelity unmeasured\n");
            } else {
                sb.append(String.format(
                        "fidelity: %d/%d exact, mean delta %+.2f -- a CONSTANT delta is a convention%n",
                        agreeing, compared, deltaSum / (double) compared));
                sb.append("offset (fix by adding it); a VARYING delta means generateHeight describes\n")
                        .append("different terrain than the world has, and earth/wind derived from it\n")
                        .append("would not match what a player walks on.\n");
            }
        }

        // ---- Throughput. No player needed; every block uses coordinates nothing has touched.
        sb.append("\nTHROUGHPUT -- fresh coordinates per block, so none of these measure the cache\n")
                .append("except the warm pass, which deliberately does.\n");
        sb.append(String.format("%-34s %8s %9s %11s  %s%n", "block", "calls", "ms", "calls/sec", "note"));

        int gridOriginX = COLD_GRID_ORIGIN;
        int gridOriginZ = COLD_GRID_ORIGIN;
        long coldNs = timeGrid(generator, seed, gridOriginX, gridOriginZ, WARM_GRID_SAMPLES, GRID_STRIDE);
        report(sb, "cold grid (never queried)", WARM_GRID_SAMPLES, coldNs, "first touch");
        long warmNs = timeGrid(generator, seed, gridOriginX, gridOriginZ, WARM_GRID_SAMPLES, GRID_STRIDE);
        report(sb, "warm grid (same coordinates)", WARM_GRID_SAMPLES, warmNs, "repeat of the block above");

        boolean truncated = false;
        if (System.nanoTime() - started < BUDGET_MS * 1_000_000L) {
            long step32 = timeWindow(generator, seed, COLD_GRID_ORIGIN + 500_000, COLD_GRID_ORIGIN, 128, 32);
            report(sb, "one candidate window, step 32", 81, step32, "9x9 columns");
        } else {
            truncated = true;
        }
        if (!truncated && System.nanoTime() - started < BUDGET_MS * 1_000_000L) {
            long step8 = timeWindow(generator, seed, COLD_GRID_ORIGIN + 900_000, COLD_GRID_ORIGIN, 128, 8);
            report(sb, "one candidate window, step 8", 1089, step8, "33x33 columns, today's step");
        } else {
            truncated = true;
        }
        if (truncated) {
            sb.append("BUDGET: window blocks skipped after ").append(BUDGET_MS).append("ms\n");
        }

        sb.append("\nREADING THIS. The per-candidate window rows are the directly actionable cost: a\n")
                .append("candidate sits on a 256b lattice and serves 65,536 b-squared, so divide by the\n")
                .append("chunks in that area for the amortised figure. If the warm pass is much faster\n")
                .append("than the cold one, the generator caches and a derivation that walks a window in\n")
                .append("coordinate order will beat these numbers.\n");
        sb.append("WORLDGENHEIGHT_VERDICT: MEASURED | seed=")
                .append(seedLong)
                .append(" | cold ")
                .append(perSecond(WARM_GRID_SAMPLES, coldNs))
                .append("/s, warm ")
                .append(perSecond(WARM_GRID_SAMPLES, warmNs))
                .append("/s\n");
        send(context, sb.toString());
    }

    /** Topmost y whose block is solid, or {@code -1} when the column is not resident. */
    private static int topmostSolid(SectionReaderTerrainSample sample, int x, int z) {
        for (int y = 319; y >= 0; y--) {
            if (!sample.isLoaded(x, y, z)) {
                continue;
            }
            if (sample.isSolidAt(x, y, z)) {
                return y;
            }
        }
        return -1;
    }

    /** Topmost y holding any fluid, or {@code -1} for none. Separates a dry column from a submerged one. */
    private static int topmostFluid(SectionReaderTerrainSample sample, int x, int z) {
        for (int y = 319; y >= 0; y--) {
            if (!sample.isLoaded(x, y, z)) {
                continue;
            }
            Optional<String> fluid = sample.fluidAt(x, y, z);
            if (fluid.isPresent()) {
                return y;
            }
        }
        return -1;
    }

    private static long timeGrid(ChunkGenerator generator, int seed, int originX, int originZ, int n, int stride) {
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            try {
                generator.generateHeight(seed, originX + i * stride, originZ + i * stride);
            } catch (Throwable ignored) {
                // A throwing call still consumed time; excluding it would flatter the measurement.
            }
        }
        return System.nanoTime() - t0;
    }

    private static long timeWindow(ChunkGenerator generator, int seed, int centreX, int centreZ, int radius, int step) {
        long t0 = System.nanoTime();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                try {
                    generator.generateHeight(seed, centreX + dx, centreZ + dz);
                } catch (Throwable ignored) {
                    // See timeGrid.
                }
            }
        }
        return System.nanoTime() - t0;
    }

    private static void report(StringBuilder sb, String label, int calls, long ns, String note) {
        sb.append(String.format(
                "%-34s %8d %9.1f %11s  %s%n", label, calls, ns / 1_000_000.0, perSecond(calls, ns), note));
    }

    private static String perSecond(int calls, long ns) {
        if (ns <= 0) {
            return "-";
        }
        return String.format("%.0f", calls * 1_000_000_000.0 / ns);
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

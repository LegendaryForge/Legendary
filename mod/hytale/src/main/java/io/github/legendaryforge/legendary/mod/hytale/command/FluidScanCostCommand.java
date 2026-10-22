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
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Debug command: {@code /fluidscancost}
 *
 * <p>Measures what a finer {@code sampleStep} actually costs, which is the one number the
 * {@code Physical} sampling fork turns on and the one nobody has.
 *
 * <p>The read COUNTS are arithmetic and already known: over a 48x48x33 box, step 8 is 245 points,
 * step 4 is 1,521, step 2 is 10,625, step 1 is 79,233. The loop overhead is also known and
 * negligible -- measured against a trivial fake at 0.04 ms per candidate for step 2. What is
 * unknown is the per-read cost of {@code SectionReader} against real loaded storage, and the
 * plausible range spans two orders of magnitude: at 100 ns a step-2 run costs 53 ms, at 10 us it
 * costs 5.3 s. The first is free, the second rules the approach out for anything but a manual probe.
 *
 * <p><b>Anchored on a connected player, and it has to be.</b> This command originally read at
 * offsets from the origin, documented as "headless on purpose ... because a client login on this
 * harness needs browser-approved server auth that a FIFO console cannot perform." That premise was
 * falsified by measurement on 2026-10-21: a headless server holds <b>no loaded region at any offset
 * from 0 to 4096</b>, confirmed against {@code signalprobe}'s own logs. So the origin scan could
 * only ever find nothing and return INCONCLUSIVE -- the convenience it bought did not exist, and the
 * session that produced the real figures attached a client anyway.
 *
 * <p>The origin-offset fallback is deliberately <b>not</b> retained. Its success condition was
 * measured not to occur, and a branch that cannot be reached reads to the next author as a supported
 * mode. {@code ResidueSiteProbeCommand} takes the same position for the same reason.
 *
 * <p>This also fixes what the command measured. Anchoring on "wherever storage happens to be
 * resident" answers a different question from "what does a scan cost where the scan would run";
 * under the old anchor the two were only ever the same by accident.
 *
 * <p>Prints {@code FLUIDSCAN_VERDICT:} as its last line.
 */
public class FluidScanCostCommand extends AbstractWorldCommand {

    /** The real neighbourhood: LatticeParameters.defaults() radius 24, vertical span 16. */
    private static final int RADIUS = 24;

    private static final int SPAN = 16;

    private static final int[] STEPS = {8, 4, 2, 1};

    private static final int WARMUP = 3;

    private static final int ITERATIONS = 10;

    /** Candidates per probe run, so the per-run column means something. */
    private static final int CANDIDATES_PER_RUN = 25;

    public FluidScanCostCommand() {
        super("fluidscancost", "Times SectionReader fluid reads at each sampleStep, to settle the sampling fork");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- fluid scan cost ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        ChunkStore chunkStore = world.getChunkStore();
        SectionReader reader = new SectionReader(chunkStore);

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("\nno players connected.\n")
                    .append("A headless server loads no chunk region at all, so every read would be a cheap\n")
                    .append("miss and the timing would come back fast for the wrong reason. Connect a client.\n")
                    .append("FLUIDSCAN_VERDICT: INCONCLUSIVE | no players\n");
            send(context, sb.toString());
            return;
        }

        // Anchor where the question is: the scan this times would run around a player, so measure
        // there. Reading "wherever storage is resident" answers a different question.
        String who = null;
        int anchorX = 0;
        int anchorY = 0;
        int anchorZ = 0;
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                sb.append("[").append(playerRef.getUsername()).append("] TransformComponent ABSENT\n");
                continue;
            }
            Vector3d pos = transform.getPosition();
            who = playerRef.getUsername();
            anchorX = MathUtil.floor(pos.x);
            anchorY = MathUtil.floor(pos.y);
            anchorZ = MathUtil.floor(pos.z);
            break;
        }
        if (who == null) {
            sb.append("no player had a TransformComponent; cannot anchor\n");
            sb.append("FLUIDSCAN_VERDICT: INCONCLUSIVE | no transform\n");
            send(context, sb.toString());
            return;
        }

        // An unloaded anchor would make every read a cheap miss, so say so rather than timing it.
        if (!reader.hasStorageInMemory(anchorX, anchorY, anchorZ)) {
            sb.append("anchor: ")
                    .append(anchorX)
                    .append(',')
                    .append(anchorY)
                    .append(',')
                    .append(anchorZ)
                    .append(" [")
                    .append(who)
                    .append("] has NO storage in memory; cannot measure\n");
            sb.append("FLUIDSCAN_VERDICT: INCONCLUSIVE | anchor unloaded\n");
            send(context, sb.toString());
            return;
        }

        sb.append("anchor: ")
                .append(anchorX)
                .append(',')
                .append(anchorY)
                .append(',')
                .append(anchorZ)
                .append(" [")
                .append(who)
                .append("]\n");

        sb.append(String.format("%-6s %10s %10s %12s %14s%n", "step", "points", "hits", "ms/cand", "ms/run(25)"));
        for (int step : STEPS) {
            for (int w = 0; w < WARMUP; w++) {
                scan(reader, anchorX, anchorY, anchorZ, step);
            }
            long points = 0;
            long hits = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                long packed = scan(reader, anchorX, anchorY, anchorZ, step);
                points = packed >>> 32;
                hits = packed & 0xFFFFFFFFL;
            }
            double msPerCandidate = (System.nanoTime() - t0) / 1e6 / ITERATIONS;
            sb.append(String.format(
                    "%-6d %10d %10d %12.3f %14.1f%n",
                    step, points, hits, msPerCandidate, msPerCandidate * CANDIDATES_PER_RUN));
        }

        sb.append("FLUIDSCAN_VERDICT: COMPLETE\n");
        send(context, sb.toString());
    }

    /** Returns points in the high 32 bits, fluid hits in the low 32. */
    private static long scan(SectionReader reader, int x, int surfaceY, int z, int step) {
        long points = 0;
        long hits = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx += step) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += step) {
                for (int dy = -SPAN; dy <= SPAN; dy += step) {
                    int px = x + dx;
                    int py = surfaceY + dy;
                    int pz = z + dz;
                    points++;
                    if (reader.hasStorageInMemory(px, py, pz)) {
                        if (reader.getFluidId(px, py, pz) != 0) {
                            hits++;
                        }
                    }
                }
            }
        }
        return (points << 32) | hits;
    }

    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

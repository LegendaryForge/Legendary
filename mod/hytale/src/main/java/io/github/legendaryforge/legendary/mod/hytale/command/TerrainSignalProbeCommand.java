package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Debug command: {@code /signalprobe}
 *
 * <p>Settles questions 1 and 2 of {@code 2026-10-20-residue-terrain-derivation-design.md} §9 by
 * execution rather than by signature. Those two decide whether residue derivation can be on-demand
 * or must move to generation time, which is an architectural fork, and both were answered from
 * {@code javap} output on 2026-10-21 — a hypothesis under this project's standing method rule.
 *
 * <p>What it reads, and why each line exists:
 *
 * <ul>
 *   <li>{@code SectionReader.getEnvironment(x, y, z)} — the <em>arbitrary-coordinate</em> environment
 *       read. {@link WeatherConditionProbeCommand} already proves environment is readable at a
 *       position, but it obtains its section ref from {@code transform.getSectionRef()}, so what it
 *       demonstrates is "anywhere inside the section a player occupies". §9 question 2 asks the
 *       broader thing, and this is the API for it.
 *   <li>{@code ChunkLightData.getSkyLight(x, y, z)} via {@link BlockSection} — sky exposure, §5's
 *       chosen signal for Wind and Lightning.
 *   <li>{@code hasStorageInMemory} — the loaded/unloaded discriminator, which is what bounds the
 *       answer to both questions.
 *   <li>{@code canHoldY} — the engine's own world-height predicate, against the measured 320.
 *   <li>{@code supportsCubicSections} — a <em>runtime</em> V1/V2 generator discriminator. Workspace
 *       deferral #18 currently watches a boot-log line for the handover; this is a direct read.
 * </ul>
 *
 * <p><strong>Two things this probe deliberately does not assume.</strong>
 *
 * <p>First, whether {@code getSkyLight} takes world or section-local coordinates. {@code ChunkLightData}
 * is section-scoped (32³), but {@code WeatherConditionProbeCommand} passes <em>world</em> floored
 * coordinates to {@code EnvironmentSection.get} and that works, so these accessors appear to mask
 * internally. "Appear to" is not good enough for a value a derivation would key on, so both forms are
 * reported side by side and the run disambiguates. If they differ, the output says so; if they agree,
 * <em>how much that is worth depends on the value</em>. Sky light saturates — the whole open surface
 * reads 15 and everything deep reads 0 — so agreement at either extreme is weak evidence, because a
 * wrongly-addressed cell would very likely return the same number. Only agreement at an intermediate
 * value discriminates, and the output now labels which case it got. The first version of this line
 * printed "masking confirmed" on a pair of 15s, asserting a conclusion its own data could not carry.
 *
 * <p>Second, which of {@code getGlobalLight()} and {@code getLocalLight()} carries sky. The split was
 * not known when §5 was written, and it matters: §5's argument for sky light is that it is
 * "deterministic, identical for every player, and immune to a player lighting a cave". That claim can
 * only be true of one of these two, so both are read and both {@code has*Light()} guards are reported.
 *
 * <p><strong>The absent case is tested alone and labelled.</strong> A coordinate far outside any loaded
 * region is read as its own block, because a negative result among positives is ambiguous while a
 * negative on its own is not. An unloaded reading is a <em>pass</em> of that sub-test, not a failure —
 * recorded explicitly because a prior probe in this repo graded its own design conflict as an engine
 * defect.
 *
 * <p>Cost is measured rather than asserted: §9 question 1 asks whether the read is <em>cheap</em>, and
 * no signature answers that. The timing loop runs only where storage is in memory.
 *
 * <p>Prints {@code SIGNALPROBE_VERDICT:} as its last line. Read it by name — exit status and line
 * position are both fragile, and this workspace has recorded several occasions where something
 * appended output after a verdict and the reader saw the wrapper instead.
 */
public class TerrainSignalProbeCommand extends AbstractWorldCommand {

    /** Far enough out that no ordinary session has it loaded, and well inside coordinate range. */
    private static final int ABSENT_XZ = 2_000_000;

    private static final int TIMED_READS = 20_000;

    /**
     * Horizontal offsets from the origin, probed for loaded storage. This exists for two reasons.
     *
     * <p>It lets the probe answer §9's questions <em>headless</em>: as written it would only read at
     * player positions, and a client login on this harness needs browser-approved server auth, so a
     * no-player run would have reported {@code INCONCLUSIVE} for a reason unrelated to the questions.
     *
     * <p>And the survey is a measurement in its own right. "On-demand derivation is bounded to loaded
     * chunks" is only actionable with a number attached to *how far* loaded reaches.
     */
    private static final int[] SURVEY_OFFSETS = {0, 32, 128, 256, 512, 1_024, 4_096};

    public TerrainSignalProbeCommand() {
        super("signalprobe", "Reads environment and sky light positionally, to settle the residue derivation fork");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- terrain signal probe (residue §9 q1+q2) ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        ChunkStore chunkStore = world.getChunkStore();
        SectionReader reader = new SectionReader(chunkStore);
        Store<ChunkStore> sectionStore = chunkStore.getStore();

        // --- generator shape, read at runtime rather than from a boot log
        boolean cubic = reader.supportsCubicSections();
        sb.append("supportsCubicSections: ")
                .append(cubic)
                .append(cubic ? "  (cubic -- NOT the V1 shape; check deferral #18)" : "  (V1 column shape)")
                .append('\n');

        // --- the engine's own height bound, against the measured 320
        sb.append("canHoldY: ");
        for (int y : new int[] {-1, 0, 160, 319, 320, 400}) {
            sb.append('y').append(y).append('=').append(reader.canHoldY(y)).append(' ');
        }
        sb.append("\n  (320 blocks tall was measured as EnvironmentSection[10] x 32;")
                .append(" y319 true with y320 false confirms it)\n");

        // --- survey: what is loaded, and how far out
        sb.append("loaded survey at y=64 (offsets from origin):\n  ");
        int firstLoadedX = Integer.MIN_VALUE;
        int loadedCount = 0;
        for (int off : SURVEY_OFFSETS) {
            boolean in = reader.hasStorageInMemory(off, 64, off);
            sb.append(off).append('=').append(in ? "in" : "out").append(' ');
            if (in) {
                loadedCount++;
                if (firstLoadedX == Integer.MIN_VALUE) {
                    firstLoadedX = off;
                }
            }
        }
        sb.append('\n');
        if (loadedCount == 0) {
            sb.append("  (nothing loaded at y=64 -- a headless server may hold no region;\n");
            sb.append("   connect a client, or read the player block below)\n");
        }

        int playersProbed = 0;
        boolean envOk = false;
        boolean skyOk = false;
        long envNanos = -1;
        long skyNanos = -1;

        // --- the headless path: a full reading at the nearest loaded surveyed column
        if (firstLoadedX != Integer.MIN_VALUE) {
            sb.append("headless reading at ")
                    .append(firstLoadedX)
                    .append(",64,")
                    .append(firstLoadedX)
                    .append('\n');
            Reading hr = read(reader, sectionStore, firstLoadedX, 64, firstLoadedX);
            sb.append(hr.describe("    "));
            if (hr.envResolved) {
                envOk = true;
            }
            if (hr.skyResolved) {
                skyOk = true;
            }
            if (hr.loaded) {
                envNanos = timeEnvironment(reader, firstLoadedX, 64, firstLoadedX);
                skyNanos = timeSkyLight(reader, sectionStore, firstLoadedX, 64, firstLoadedX);
            }
        }

        var playerRefs = world.getPlayerRefs();
        sb.append("players: ").append(playerRefs.size()).append('\n');
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                sb.append("  [").append(playerRef.getUsername()).append("] TransformComponent ABSENT\n");
                continue;
            }
            Vector3d pos = transform.getPosition();
            int px = MathUtil.floor(pos.x);
            int py = MathUtil.floor(pos.y);
            int pz = MathUtil.floor(pos.z);
            sb.append("  [")
                    .append(playerRef.getUsername())
                    .append("] at ")
                    .append(px)
                    .append(',')
                    .append(py)
                    .append(',')
                    .append(pz)
                    .append('\n');

            Reading at = read(reader, sectionStore, px, py, pz);
            sb.append(at.describe("    "));
            if (at.envResolved) {
                envOk = true;
            }
            if (at.skyResolved) {
                skyOk = true;
            }

            // Sky light should vary with cover. Sampling above and below the player makes a constant
            // reading visible as a constant rather than being mistaken for a measurement.
            for (int dy : new int[] {12, -12}) {
                Reading r = read(reader, sectionStore, px, py + dy, pz);
                sb.append("    y")
                        .append(dy > 0 ? "+" : "")
                        .append(dy)
                        .append(": ")
                        .append(r.oneLine())
                        .append('\n');
            }

            if (at.loaded && envNanos < 0) {
                envNanos = timeEnvironment(reader, px, py, pz);
                skyNanos = timeSkyLight(reader, sectionStore, px, py, pz);
            }
            playersProbed++;
        }

        if (playerRefs.isEmpty()) {
            sb.append("  (no players connected -- positional reads need a loaded region;\n");
            sb.append("   the absent-case block below still runs, and still means something)\n");
        }

        // --- the absent case, alone and labelled
        sb.append("absent case (expected: NOT in memory) at ")
                .append(ABSENT_XZ)
                .append(",64,")
                .append(ABSENT_XZ)
                .append('\n');
        Reading absent = read(reader, sectionStore, ABSENT_XZ, 64, ABSENT_XZ);
        sb.append(absent.describe("    "));
        boolean absentOk = !absent.loaded;
        sb.append("    -> absent sub-test ")
                .append(absentOk ? "PASS (unloaded, as expected)" : "UNEXPECTED (reported in memory)")
                .append('\n');

        if (envNanos >= 0) {
            sb.append("cost over ").append(TIMED_READS).append(" reads: getEnvironment ");
            appendNanos(sb, envNanos);
            sb.append(" / read, getSkyLight ");
            appendNanos(sb, skyNanos);
            sb.append(" / read\n");
        } else {
            sb.append("cost: not measured (no loaded player position to read)\n");
        }

        sb.append("SIGNALPROBE_VERDICT: ")
                .append(verdict(envOk, skyOk, absentOk, loadedCount > 0 || playersProbed > 0))
                .append(" | env=")
                .append(envOk ? "READ" : "unread")
                .append(" sky=")
                .append(skyOk ? "READ" : "unread")
                .append(" absent=")
                .append(absentOk ? "ok" : "unexpected")
                .append(" cubic=")
                .append(cubic)
                .append(" players=")
                .append(playersProbed)
                .append(" loadedOffsets=")
                .append(loadedCount)
                .append('/')
                .append(SURVEY_OFFSETS.length);
        sendText(context, sb.toString());
    }

    /**
     * A reading from anywhere counts, player or surveyed column — the questions are about the API, not
     * about who triggered the read. {@code INCONCLUSIVE} is reserved for "nothing was loaded anywhere",
     * which is a statement about the harness rather than about the engine, and must not read as a
     * failure of either question.
     */
    private static String verdict(boolean envOk, boolean skyOk, boolean absentOk, boolean anyLoaded) {
        if (!anyLoaded) {
            return "INCONCLUSIVE";
        }
        if (envOk && skyOk && absentOk) {
            return "PASS";
        }
        return envOk || skyOk ? "PARTIAL" : "FAIL";
    }

    /** One position's worth of observations. Fields hold what was seen, never what was expected. */
    private static final class Reading {
        int x;
        int y;
        int z;
        boolean loaded;
        boolean sectionValid;
        int env = Integer.MIN_VALUE;
        boolean envResolved;
        String envName = "-";
        boolean blockSectionPresent;
        boolean hasGlobal;
        boolean hasLocal;
        int skyGlobalWorld = -1;
        int skyGlobalLocal = -1;
        int skyLocalWorld = -1;
        boolean skyResolved;

        String oneLine() {
            if (!loaded) {
                return "not in memory";
            }
            return "env=" + env + " (" + envName + ") skyGlobal(world/local)=" + skyGlobalWorld + "/" + skyGlobalLocal;
        }

        String describe(String indent) {
            StringBuilder b = new StringBuilder();
            b.append(indent)
                    .append("hasStorageInMemory=")
                    .append(loaded)
                    .append(" sectionAt=")
                    .append(sectionValid ? "valid" : "null/invalid")
                    .append('\n');
            if (!loaded) {
                b.append(indent).append("(nothing further is readable here)\n");
                return b.toString();
            }
            b.append(indent)
                    .append("getEnvironment=")
                    .append(env)
                    .append(" (")
                    .append(envName)
                    .append(")")
                    .append(envResolved ? "" : "  <- did NOT resolve to an asset")
                    .append('\n');
            b.append(indent)
                    .append("BlockSection=")
                    .append(blockSectionPresent ? "present" : "ABSENT")
                    .append(" hasGlobalLight=")
                    .append(hasGlobal)
                    .append(" hasLocalLight=")
                    .append(hasLocal)
                    .append('\n');
            b.append(indent)
                    .append("getSkyLight global world-coords=")
                    .append(skyGlobalWorld)
                    .append(" section-local=")
                    .append(skyGlobalLocal)
                    .append(
                            skyGlobalWorld != skyGlobalLocal
                                    ? "  (DIFFER -- world coords are NOT masked; use section-local)"
                                    : skyGlobalWorld == ChunkLightData.MAX_VALUE || skyGlobalWorld == 0
                                            ? "  (agree, but at a SATURATED value -- weak evidence,"
                                                    + " a wrong cell would likely read the same)"
                                            : "  (agree at an intermediate value -- masking likely)")
                    .append('\n');
            b.append(indent)
                    .append("getSkyLight local  world-coords=")
                    .append(skyLocalWorld)
                    .append("   max=")
                    .append(ChunkLightData.MAX_VALUE)
                    .append('\n');
            return b.toString();
        }
    }

    private static Reading read(SectionReader reader, Store<ChunkStore> sectionStore, int x, int y, int z) {
        Reading r = new Reading();
        r.x = x;
        r.y = y;
        r.z = z;
        r.loaded = reader.hasStorageInMemory(x, y, z);
        Ref<ChunkStore> sectionRef = reader.sectionAt(x, y, z);
        r.sectionValid = sectionRef != null && sectionRef.isValid();
        if (!r.loaded) {
            return r;
        }
        r.env = reader.getEnvironment(x, y, z);
        Environment asset = environmentAsset(r.env);
        r.envResolved = asset != null;
        r.envName = asset == null ? "?" : String.valueOf(asset.getId());

        if (!r.sectionValid) {
            return r;
        }
        BlockSection section = sectionStore.getComponent(sectionRef, BlockSection.getComponentType());
        r.blockSectionPresent = section != null;
        if (section == null) {
            return r;
        }
        r.hasGlobal = section.hasGlobalLight();
        r.hasLocal = section.hasLocalLight();
        if (r.hasGlobal) {
            ChunkLightData global = section.getGlobalLight();
            r.skyGlobalWorld = global.getSkyLight(x, y, z);
            r.skyGlobalLocal = global.getSkyLight(x & 31, y & 31, z & 31);
            r.skyResolved = true;
        }
        if (r.hasLocal) {
            r.skyLocalWorld = section.getLocalLight().getSkyLight(x, y, z);
        }
        return r;
    }

    private static long timeEnvironment(SectionReader reader, int x, int y, int z) {
        long sink = 0;
        long start = System.nanoTime();
        for (int i = 0; i < TIMED_READS; i++) {
            sink += reader.getEnvironment(x, y, z);
        }
        long elapsed = System.nanoTime() - start;
        return sink == Long.MIN_VALUE ? -1 : elapsed / TIMED_READS;
    }

    private static long timeSkyLight(SectionReader reader, Store<ChunkStore> sectionStore, int x, int y, int z) {
        Ref<ChunkStore> sectionRef = reader.sectionAt(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return -1;
        }
        BlockSection section = sectionStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null || !section.hasGlobalLight()) {
            return -1;
        }
        ChunkLightData light = section.getGlobalLight();
        long sink = 0;
        long start = System.nanoTime();
        for (int i = 0; i < TIMED_READS; i++) {
            sink += light.getSkyLight(x, y, z);
        }
        long elapsed = System.nanoTime() - start;
        return sink == Long.MIN_VALUE ? -1 : elapsed / TIMED_READS;
    }

    private static void appendNanos(StringBuilder sb, long nanos) {
        if (nanos < 0) {
            sb.append("n/a");
        } else {
            sb.append(nanos).append("ns");
        }
    }

    private static Environment environmentAsset(int index) {
        try {
            return Environment.getAssetMap().getAsset(index);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

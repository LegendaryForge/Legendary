package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.builtin.beam.AttachedBeam;
import com.hypixel.hytale.builtin.beam.BeamComponent;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;

/**
 * Debug command: {@code /beamprobe}
 *
 * <p>Answers the question gating §4 and §5.3 of
 * {@code docs/superpowers/specs/2026-10-20-hytale-0.7.2-capability-assessment-design.md}:
 * <em>can a beam be drawn between arbitrary world positions, or do beams require an entity at
 * each end?</em> Residue currents run between nexus <em>positions</em>. If every endpoint needs
 * an anchor entity, the entity budget, persistence and cleanup costs change the design materially.
 *
 * <p>Signature reading already says positions are supported — {@code AttachedBeam} offers
 * {@code toPosition} and {@code toBlock} beside {@code toEntity}. That is a hypothesis. This
 * command executes it, because the assessment's own rule is that no capability is adopted on
 * signature inspection alone.
 *
 * <p>What is NOT in question: the beam <em>holder</em> is always an entity.
 * {@code BeamComponent.spawn} returns a {@code Ref<EntityStore>}, so something is spawned at the
 * origin regardless. The real question is whether the far end also costs one. A holder carries up
 * to {@code MAX_BEAMS} beams, so the answer decides whether a nexus with N outgoing currents costs
 * 1 entity or N+1.
 *
 * <p>Three tests, each reported independently so one failure cannot be read as "beams are
 * unavailable":
 *
 * <ul>
 *   <li><b>POSITION</b> — one beam from a spawned holder to a bare {@code Vector3d}.
 *   <li><b>BLOCK</b> — one beam to a block coordinate via {@code toBlock}.
 *   <li><b>FAN</b> — four beams from a <em>single</em> holder to four positions. This is the
 *       residue case: one nexus, several currents. If this works, cost is one entity per nexus
 *       rather than one per current.
 * </ul>
 *
 * <p>Beams are spawned with a finite lifetime so the probe cleans up after itself rather than
 * leaving holder entities in the world. Ids are the four shipped assets under
 * {@code Server/Entity/Beams/} in {@code Assets.zip}; each is resolved and reported separately so
 * a bad id cannot be mistaken for a missing capability.
 */
public class BeamProbeCommand extends AbstractWorldCommand {

    /** Shipped beam assets: Server/Entity/Beams/{Basic,Chain,Fire,Rope}.json */
    private static final String[] CANDIDATE_BEAMS = {"Basic", "Chain", "Fire", "Rope"};

    /** Long enough for the operator to look, short enough that the probe self-cleans. */
    private static final float LIFETIME_SECONDS = 60f;

    /** Y used headless, both to force a section load and as the fallback test height. */
    private static final int HEADLESS_PROBE_Y = 100;

    private static final int SCAN_TOP = 160;

    private static final int SCAN_BOTTOM = 20;

    private static final int LOAD_TIMEOUT_SECONDS = 30;

    public BeamProbeCommand() {
        super("beamprobe", "Tests whether beams attach to positions and blocks, or only to entities");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- beam probe (0.7.2) ---\n");
        sb.append("MAX_BEAMS=").append(BeamComponent.MAX_BEAMS);
        sb.append(" NO_LIFETIME=").append(BeamComponent.NO_LIFETIME);
        sb.append(" lifetime used=").append(LIFETIME_SECONDS).append("s\n");

        int beamIndex = -1;
        String beamId = null;
        sb.append("beam assets:\n");
        for (String id : CANDIDATE_BEAMS) {
            int idx;
            try {
                idx = Beam.getAssetMap().getIndex(id);
            } catch (RuntimeException e) {
                sb.append("  ")
                        .append(id)
                        .append(" -> THREW ")
                        .append(e.getClass().getSimpleName())
                        .append("\n");
                continue;
            }
            sb.append("  ").append(id).append(" -> index ").append(idx).append("\n");
            if (idx >= 0 && beamIndex < 0) {
                beamIndex = idx;
                beamId = id;
            }
        }
        if (beamIndex < 0) {
            sendText(
                    context,
                    sb.append("no beam asset resolved -- cannot test attachment\n")
                            .toString());
            return;
        }
        sb.append("using '")
                .append(beamId)
                .append("' (index ")
                .append(beamIndex)
                .append(")\n\n");
        final String chosenBeam = beamId;

        Vector3d playerOrigin = playerOrigin(world, store);
        if (playerOrigin != null) {
            runAll(context, sb, store, chosenBeam, playerOrigin);
            return;
        }

        // Headless. SectionReader reads only resident sections, so a column scan here finds
        // nothing until something loads the chunk -- force one, then scan.
        sb.append("no connected player -- forcing a section load at x=0 z=0\n");
        world.getChunkStore()
                .getChunkSectionReferenceAtBlockAsync(0, HEADLESS_PROBE_Y, 0)
                .orTimeout(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenAcceptAsync(
                        ref -> {
                            sb.append("  section ref ")
                                    .append(ref == null ? "null" : (ref.isValid() ? "VALID" : "invalid"))
                                    .append("\n");
                            Vector3d origin = groundScan(world, sb);
                            if (origin == null) {
                                origin = new Vector3d(0.5, HEADLESS_PROBE_Y, 0.5);
                                sb.append("  no ground resolved; testing at y=")
                                        .append(HEADLESS_PROBE_Y)
                                        .append(" regardless -- beams do not require ground, and the\n")
                                        .append("  question is whether the server accepts a positional attachment\n");
                            }
                            runAll(context, sb, store, chosenBeam, origin);
                        },
                        world)
                .exceptionally(t -> {
                    sb.append("force-load THREW ")
                            .append(t.getClass().getSimpleName())
                            .append(": ")
                            .append(t.getMessage())
                            .append("\n");
                    sendText(context, sb.toString());
                    return null;
                });
    }

    /** The three attachment tests, plus the closing note. Called once an origin is known. */
    private void runAll(
            CommandContext context, StringBuilder sb, Store<EntityStore> store, String chosenBeam, Vector3d origin) {
        sb.append(String.format("origin: %.1f %.1f %.1f%n%n", origin.x, origin.y, origin.z));

        // POSITION -- the question that gates the design.
        runTest(sb, "POSITION", store, new Vector3d(origin.x + 2, origin.y + 1, origin.z), () -> new AttachedBeam[] {
            AttachedBeam.toPosition(
                    beamIndexOf(chosenBeam), 1.0f, null, new Vector3d(origin.x + 12, origin.y + 3, origin.z))
        });

        // BLOCK -- the same question against a block coordinate.
        runTest(sb, "BLOCK", store, new Vector3d(origin.x + 2, origin.y + 1, origin.z + 4), () -> new AttachedBeam[] {
            AttachedBeam.toBlock(
                    beamIndexOf(chosenBeam),
                    1.0f,
                    null,
                    (int) Math.floor(origin.x) + 12,
                    (int) Math.floor(origin.y) + 3,
                    (int) Math.floor(origin.z) + 4)
        });

        // FAN -- one holder, four beams. Decides entity cost per nexus.
        runTest(sb, "FAN(4)", store, new Vector3d(origin.x + 2, origin.y + 1, origin.z - 4), () -> {
            double[][] bearings = {{10, 0}, {0, 10}, {-10, 0}, {0, -10}};
            AttachedBeam[] beams = new AttachedBeam[bearings.length];
            for (int i = 0; i < bearings.length; i++) {
                beams[i] = AttachedBeam.toPosition(
                        beamIndexOf(chosenBeam),
                        1.0f,
                        null,
                        new Vector3d(origin.x + 2 + bearings[i][0], origin.y + 2, origin.z - 4 + bearings[i][1]));
            }
            return beams;
        });

        // --- disambiguation 2x2 ---------------------------------------------------------
        // The first run gave POSITION(1 beam, z+0) null and FAN(4 beams, z-4) valid. Those two
        // differ in BOTH beam count and position, so neither explains the other. These two cells
        // complete the matrix: same position as POSITION with 4 beams, same position as FAN with
        // 1 beam. Whichever factor tracks the null is the cause.
        runTest(sb, "POS x4  ", store, new Vector3d(origin.x + 2, origin.y + 1, origin.z), () -> {
            AttachedBeam[] beams = new AttachedBeam[4];
            for (int i = 0; i < 4; i++) {
                beams[i] = AttachedBeam.toPosition(
                        beamIndexOf(chosenBeam), 1.0f, null, new Vector3d(origin.x + 12, origin.y + 3 + i, origin.z));
            }
            return beams;
        });
        runTest(
                sb,
                "FANPOS x1",
                store,
                new Vector3d(origin.x + 2, origin.y + 1, origin.z - 4),
                () -> new AttachedBeam[] {
                    AttachedBeam.toPosition(
                            beamIndexOf(chosenBeam),
                            1.0f,
                            null,
                            new Vector3d(origin.x + 12, origin.y + 2, origin.z - 4))
                });

        sb.append("\nLook at the origin. A reported 'holder VALID' with nothing visible means the\n");
        sb.append("server accepted the beam and the client did not draw it -- a different failure.\n");
        sendText(context, sb.toString());
    }

    private int beamIndexOf(String id) {
        return Beam.getAssetMap().getIndex(id);
    }

    /** Spawns one holder carrying the supplied beams and reports what came back. */
    private void runTest(StringBuilder sb, String label, Store<EntityStore> store, Vector3d at, BeamSupplier supplier) {
        sb.append(label).append(" at ").append(String.format("%.1f %.1f %.1f", at.x, at.y, at.z));
        AttachedBeam[] beams;
        try {
            beams = supplier.get();
        } catch (RuntimeException e) {
            sb.append(" | BUILD THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n");
            return;
        }
        sb.append(" | beams=").append(beams.length);
        int validBeams = 0;
        for (AttachedBeam b : beams) {
            if (b != null && b.isValid()) {
                validBeams++;
            }
        }
        sb.append(" isValid=").append(validBeams).append("/").append(beams.length);
        try {
            Ref<EntityStore> holder = BeamComponent.spawn(store, at, LIFETIME_SECONDS, beams);
            if (holder == null) {
                sb.append(" | spawn returned null\n");
                return;
            }
            sb.append(" | holder ")
                    .append(holder.isValid() ? "VALID" : "invalid")
                    .append("\n");
        } catch (RuntimeException e) {
            sb.append(" | SPAWN THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n");
        }
    }

    @FunctionalInterface
    private interface BeamSupplier {
        AttachedBeam[] get();
    }

    private Vector3d playerOrigin(World world, Store<EntityStore> store) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                return new Vector3d(transform.getPosition());
            }
        }
        return null;
    }

    /**
     * Scans down the column at x=0 z=0 for the first non-air block.
     *
     * <p>Distinguishes "not resident" from "air": {@code SectionReader.getBlock} returns 0 for an
     * unloaded section rather than throwing, so {@code hasStorageInMemory} is checked first.
     * Without it the two are the same value and the scan reports open sky over a chunk it simply
     * cannot see.
     */
    private Vector3d groundScan(World world, StringBuilder sb) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        int unreadable = 0;
        for (int y = SCAN_TOP; y >= SCAN_BOTTOM; y--) {
            if (!reader.hasStorageInMemory(0, y, 0)) {
                unreadable++;
                continue;
            }
            int b;
            try {
                b = reader.getBlock(0, y, 0);
            } catch (RuntimeException e) {
                unreadable++;
                continue;
            }
            if (b != 0) {
                sb.append("  ground id=").append(b).append(" at y=").append(y).append("\n");
                return new Vector3d(0.5, y + 1, 0.5);
            }
        }
        sb.append("  column x=0 z=0: ")
                .append(unreadable)
                .append(" of ")
                .append(SCAN_TOP - SCAN_BOTTOM + 1)
                .append(" heights not resident, remainder air\n");
        return null;
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

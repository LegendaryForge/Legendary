package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Debug command: {@code /prefabprobe}
 *
 * <p>Can a plugin paste a multi-block prefab into the world at runtime, and can it do so where no
 * player is standing? Monuments are prefabs — Act IV's Circle, and the workshop at the Grand
 * Convergence, which sits 512–2048 m from origin.
 *
 * <p>An earlier run established that {@code PrefabUtil.paste} writes only into chunks already in
 * memory, silently skipping the rest: an 862-block prefab wrote 26, 3 and 51 cells at three sites
 * near spawn and exactly 0 at five sites further out. Every one of those runs was headless with no
 * player in the world, so only spawn-adjacent chunks were resident — the result says nothing about
 * distance and everything about residency.
 *
 * <p>This version runs a controlled comparison at the <em>same</em> far-from-spawn distance:
 *
 * <ul>
 *   <li><b>Plain</b> — the 8-arg overload the shipped {@code PastePrefabEffect} calls, with its
 *       {@code (1, 4)} arguments read from its bytecode. Expected to write nothing.
 *   <li><b>Region</b> — {@code loadPasteRegionAsync} first, then the overload taking that
 *       {@code PasteRegion}.
 * </ul>
 *
 * <p><strong>Changed in 0.7.2 (Update 7 / Chapter I).</strong> {@code waitForFutureWithoutLock} —
 * which existed precisely so the world thread could wait on its own futures without deadlocking —
 * was <em>removed with no replacement</em>; a jar-wide search finds no reference to it and no
 * successor. The engine composes instead, so this probe now does too: the three attempts run as a
 * sequential {@link CompletableFuture} chain, each stage hopped back onto {@code world} (which is
 * an {@link java.util.concurrent.Executor}) so pastes and snapshots stay on the world thread.
 *
 * <p>The practical consequence for a reader: <strong>{@code /prefabprobe} no longer prints when it
 * returns.</strong> The command returns immediately and the report arrives as a later message. An
 * empty console is a run in progress, not a failure — and if the chain stalls, the timeout prints a
 * partial report rather than nothing at all.
 *
 * <p>If Region writes and Plain does not, remote placement is available and a structure can be
 * materialised anywhere. If neither writes, placement is bounded to loaded chunks and the design
 * must materialise on arrival — which is how ordinary worldgen behaves anyway.
 */
public class PrefabPasteProbeCommand extends AbstractWorldCommand {

    private static final String FIRE_PILLAR = "Monuments/Unique/Elemental_Circles/Fire/Pillar_Forward/"
            + "Unique_Fire_Pillar_Forward_Firelands_StoneCircle_Forward_001.prefab.json";

    /** Name form is the path under {@code Server/Prefabs/} WITH the suffix; bare names return null. */
    private static final String DRUID_CIRCLE =
            "Monuments/Unique/Elemental_Circles/Earth/01/Unique_Earth_01_Druid_Circles_1_001.prefab.json";

    /** Sized from the Fire pillar's real extents (x -2..4, y 4..33, z -13..13), with margin. */
    private static final int R_XZ = 20;

    private static final int Y_LO = -30;
    private static final int Y_HI = 40;

    /** Far enough out that no chunk is resident on a headless server. */
    private static final int FAR = 600;

    /** The two ints the shipped PastePrefabEffect passes, read from its bytecode. */
    private static final int PASTE_ARG_A = 1;

    private static final int PASTE_ARG_B = 4;

    /** Matches the bound PrefabRemoveAction puts on the same call in the 0.7.2 jar. */
    private static final int REGION_TIMEOUT_SECONDS = 30;

    /** Six region awaits plus paste work; generous so a slow disk reports late, not never. */
    private static final int CHAIN_TIMEOUT_SECONDS = 240;

    public PrefabPasteProbeCommand() {
        super("prefabprobe", "Compares plain vs PasteRegion prefab pasting far from any player");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- prefab paste probe (0.7.2) ---\n");

        Vector3d origin = resolveOrigin(world, store, sb);
        if (origin == null) {
            sendText(context, sb.toString());
            return;
        }

        PrefabStore prefabStore = PrefabStore.get();
        sb.append("PrefabStore: ").append(prefabStore == null ? "null" : "ok").append("\n\n");
        if (prefabStore == null) {
            sendText(context, sb.toString());
            return;
        }

        String[] names = {FIRE_PILLAR, DRUID_CIRCLE};
        int oy = (int) Math.floor(origin.y);

        // Sequential async chain. REGION awaits a chunk-load future, so the whole run is now
        // composed rather than executed inline -- see the 0.7.2 note in this class's Javadoc.
        // Every stage is hopped back onto `world` (which is itself an Executor) because the paste
        // and the snapshots must run on the world thread.
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            chain = chain.thenComposeAsync(
                    v -> {
                        PrefabBuffer buffer = load(prefabStore, names[idx], sb);
                        if (buffer == null) {
                            return CompletableFuture.completedFuture(null);
                        }
                        // Two sites, same distance band, neither touched by any earlier run.
                        return attempt(
                                        world,
                                        store,
                                        sb,
                                        buffer,
                                        new Vector3i(FAR + 128 * idx, oy, FAR + 128),
                                        "PLAIN ",
                                        "PLAIN")
                                .thenComposeAsync(
                                        a -> attempt(
                                                world,
                                                store,
                                                sb,
                                                buffer,
                                                new Vector3i(FAR + 128 * idx, oy, FAR + 192),
                                                "REGION",
                                                "REGION"),
                                        world)
                                .thenComposeAsync(
                                        b -> attempt(
                                                world,
                                                store,
                                                sb,
                                                buffer,
                                                new Vector3i(FAR + 128 * idx, oy, FAR + 256),
                                                "LEGACY",
                                                "LEGACY"),
                                        world);
                    },
                    world);
        }

        chain.orTimeout(CHAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenCompleteAsync(
                        (v, t) -> {
                            if (t != null) {
                                sb.append("\n!! chain did not complete: ")
                                        .append(t.getClass().getSimpleName())
                                        .append(": ")
                                        .append(t.getMessage())
                                        .append("\n");
                            }
                            sendText(context, sb.toString());
                        },
                        world);
    }

    private CompletableFuture<Void> attempt(
            World world,
            Store<EntityStore> store,
            StringBuilder sb,
            PrefabBuffer buffer,
            Vector3i at,
            String label,
            String mode) {
        sb.append("  ")
                .append(label)
                .append(" at ")
                .append(at.x)
                .append(" ")
                .append(at.y)
                .append(" ")
                .append(at.z);

        int[] before = snapshot(world, at);
        int solid = 0;
        int unreadable = 0;
        for (int id : before) {
            if (id < 0) {
                unreadable++;
            } else if (id != 0) {
                solid++;
            }
        }
        sb.append(" | before solid=").append(solid).append(" unreadable=").append(unreadable);

        try {
            if ("REGION".equals(mode)) {
                // 0.7.2 removed World.waitForFutureWithoutLock, which this probe used to block the
                // world thread on its own chunk-load future. Nothing replaces it -- the engine
                // composes instead. Pattern copied from PrefabRemoveAction: hop back onto `world`
                // for the paste, with the same 30s bound it uses.
                return PrefabUtil.loadPasteRegionAsync(
                                buffer.newAccess(), world, at, PrefabRotation.ROTATION_0, PASTE_ARG_B)
                        .orTimeout(REGION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .thenAcceptAsync(
                                region -> {
                                    sb.append(" | region=").append(region == null ? "null" : "ok");
                                    if (region == null) {
                                        sb.append("\n");
                                        return;
                                    }
                                    PrefabUtil.paste(
                                            buffer.newAccess(),
                                            world,
                                            at,
                                            Rotation.None,
                                            new Random(1L),
                                            PASTE_ARG_A,
                                            PASTE_ARG_B,
                                            region,
                                            PrefabUtil.NOOP_BLOCK_ENTITY_CONSUMER,
                                            PrefabUtil.NOOP_ENTITY_CONSUMER,
                                            store);
                                    finish(world, sb, at, before);
                                },
                                world)
                        .exceptionally(t -> {
                            sb.append(" | THREW ")
                                    .append(t.getClass().getSimpleName())
                                    .append(": ")
                                    .append(t.getMessage())
                                    .append("\n");
                            return null;
                        });
            } else if ("LEGACY".equals(mode)) {
                // The 6-arg overload every earlier run used, at a distance the 8-arg form handles
                // fine. Isolates the overload as the variable, rather than distance or residency.
                PrefabUtil.paste(buffer.newAccess(), world, at, Rotation.None, new Random(1L), store);
            } else {
                PrefabUtil.paste(
                        buffer.newAccess(), world, at, Rotation.None, new Random(1L), PASTE_ARG_A, PASTE_ARG_B, store);
            }
        } catch (Exception e) {
            sb.append(" | THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n");
            return CompletableFuture.completedFuture(null);
        }

        finish(world, sb, at, before);
        return CompletableFuture.completedFuture(null);
    }

    /** Re-snapshots the box and reports how many cells differ from {@code before}. */
    private void finish(World world, StringBuilder sb, Vector3i at, int[] before) {
        int[] after = snapshot(world, at);
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) {
                changed++;
            }
        }
        sb.append(" | CHANGED CELLS ").append(changed).append("\n");
    }

    private PrefabBuffer load(PrefabStore prefabStore, String name, StringBuilder sb) {
        Path path;
        try {
            path = prefabStore.findAssetPrefabPath(name);
        } catch (RuntimeException e) {
            sb.append(name)
                    .append(" -> findAssetPrefabPath THREW ")
                    .append(e.getClass().getSimpleName())
                    .append("\n");
            return null;
        }
        if (path == null) {
            sb.append(name).append(" -> null\n");
            return null;
        }
        sb.append(name).append("\n");
        try {
            PrefabBuffer buffer = PrefabBufferUtil.loadBuffer(path);
            if (buffer == null) {
                sb.append("  loadBuffer returned null\n");
            }
            return buffer;
        } catch (Exception e) {
            sb.append("  loadBuffer THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n");
            return null;
        }
    }

    private Vector3d resolveOrigin(World world, Store<EntityStore> store, StringBuilder sb) {
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
        sb.append("no connected player -- headless fallback at x=0 z=0\n");
        SectionReader reader = new SectionReader(world.getChunkStore());
        for (int y = 160; y >= 20; y--) {
            Integer b = read(reader, 0, y, 0);
            if (b == null) {
                break;
            }
            if (b != 0) {
                sb.append("  ground at y=").append(y).append("\n");
                return new Vector3d(0.5, y + 1, 0.5);
            }
        }
        sb.append("  no ground in column x=0 z=0\n");
        return null;
    }

    /**
     * Snapshots every block id in a box around the paste point.
     *
     * <p>Counting only <em>solid</em> blocks cannot see a paste that lands inside terrain: the
     * prefab overwrites rock with rock and the count does not move. At one site the box was 63%
     * solid and a successful paste read as zero. Compare ids cell by cell instead.
     */
    private int[] snapshot(World world, Vector3i at) {
        // Constructed per snapshot rather than cached on the command: a REGION attempt loads
        // sections between the before- and after-reads, and a fresh reader removes any question
        // about whether a cached one observes them.
        SectionReader reader = new SectionReader(world.getChunkStore());
        int span = 2 * R_XZ + 1;
        int[] ids = new int[span * span * (Y_HI - Y_LO + 1)];
        int i = 0;
        for (int dx = -R_XZ; dx <= R_XZ; dx++) {
            for (int dz = -R_XZ; dz <= R_XZ; dz++) {
                for (int dy = Y_LO; dy <= Y_HI; dy++) {
                    Integer b = read(reader, at.x + dx, at.y + dy, at.z + dz);
                    ids[i++] = b == null ? -1 : b;
                }
            }
        }
        return ids;
    }

    /**
     * Reads a block id, returning null when the position cannot be read.
     *
     * <p>The {@code hasStorageInMemory} guard is load-bearing and must not be removed as
     * redundant. {@code SectionReader.getBlock} returns <strong>0 for a section that is not
     * resident</strong> — it neither loads the section nor throws — so without this check an
     * unloaded chunk is indistinguishable from air, and every caller here treats 0 as air.
     * {@code hasStorageInMemory} consults the same {@code blockSectionAt()} lookup
     * {@code getBlock} does, so the two cannot disagree.
     *
     * <p>This differs from the pre-0.7.2 {@code World.getBlock}, which loaded on demand. Caught by
     * running /beamprobe on 2026-10-20: a headless column scan that found ground at y=107 in
     * August reported "no ground" against an identical world.
     */
    private Integer read(SectionReader reader, int x, int y, int z) {
        try {
            if (!reader.hasStorageInMemory(x, y, z)) {
                return null;
            }
            return reader.getBlock(x, y, z);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

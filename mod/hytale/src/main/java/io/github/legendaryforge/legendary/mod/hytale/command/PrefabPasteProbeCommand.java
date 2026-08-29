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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
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
 *   <li><b>Region</b> — {@code loadPasteRegionAsync} first, awaited via
 *       {@code IWorldChunks.waitForFutureWithoutLock} (which exists precisely so the world thread
 *       can wait on its own futures without deadlocking), then the overload taking that
 *       {@code PasteRegion}.
 * </ul>
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

    public PrefabPasteProbeCommand() {
        super("prefabprobe", "Compares plain vs PasteRegion prefab pasting far from any player");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- prefab paste probe (0.6.1) ---\n");

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
        for (int i = 0; i < names.length; i++) {
            PrefabBuffer buffer = load(prefabStore, names[i], sb);
            if (buffer == null) {
                continue;
            }
            int oy = (int) Math.floor(origin.y);
            // Two sites, same distance band, neither touched by any earlier run.
            attempt(world, store, sb, buffer, new Vector3i(FAR + 128 * i, oy, FAR + 128), "PLAIN ", "PLAIN");
            attempt(world, store, sb, buffer, new Vector3i(FAR + 128 * i, oy, FAR + 192), "REGION", "REGION");
            attempt(world, store, sb, buffer, new Vector3i(FAR + 128 * i, oy, FAR + 256), "LEGACY", "LEGACY");
        }

        sendText(context, sb.toString());
    }

    private void attempt(
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
                CompletableFuture<PrefabUtil.PasteRegion> future = PrefabUtil.loadPasteRegionAsync(
                        buffer.newAccess(), world, at, PrefabRotation.ROTATION_0, PASTE_ARG_B);
                PrefabUtil.PasteRegion region = world.waitForFutureWithoutLock(future);
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
            return;
        }

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
        for (int y = 160; y >= 20; y--) {
            Integer b = read(world, 0, y, 0);
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
        int span = 2 * R_XZ + 1;
        int[] ids = new int[span * span * (Y_HI - Y_LO + 1)];
        int i = 0;
        for (int dx = -R_XZ; dx <= R_XZ; dx++) {
            for (int dz = -R_XZ; dz <= R_XZ; dz++) {
                for (int dy = Y_LO; dy <= Y_HI; dy++) {
                    Integer b = read(world, at.x + dx, at.y + dy, at.z + dz);
                    ids[i++] = b == null ? -1 : b;
                }
            }
        }
        return ids;
    }

    private Integer read(World world, int x, int y, int z) {
        try {
            return world.getBlock(x, y, z);
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

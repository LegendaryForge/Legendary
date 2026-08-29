package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.nio.file.Path;
import java.util.Random;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Debug command: {@code /prefabprobe}
 *
 * <p>Answers the second half of the placement question: <em>can a plugin paste a multi-block
 * prefab into the world at runtime?</em> {@code /blockprobe} settled single blocks. Monuments —
 * Act IV's Circle, and the idea of standing in for the missing Storm Elemental Circles — are
 * prefabs, and the residue specs record runtime prefab placement as unproven, with worldgen
 * (deferral #18) as the only alternative.
 *
 * <p>The call chain was read out of {@code PastePrefabEffect}'s bytecode rather than guessed,
 * the same way {@code ParticleUtil.spawnParticleEffect} was found for {@code /residueprobe}:
 * {@code PrefabStore.get().findAssetPrefabPath(name)} then
 * {@code PrefabBufferUtil.loadBuffer(path)} then {@link PrefabUtil#paste}. All three are public.
 *
 * <p>Name resolution is the part most likely to fail, so several forms are tried and each is
 * reported. A null path is "wrong name form", not "prefabs are unavailable" — the distinction
 * this probe exists to preserve.
 */
public class PrefabPasteProbeCommand extends AbstractWorldCommand {

    /** Shipped Elemental Circle monuments, under Server/Prefabs/Monuments/Unique/Elemental_Circles/. */
    private static final String CACHED = "Monuments/Unique/Elemental_Circles/Fire/Pillar_Forward/"
            + "Unique_Fire_Pillar_Forward_Firelands_StoneCircle_Forward_001";

    /**
     * Base-game prefabs are extracted to {@code .cache/prefabs/<pack>/Server/Prefabs/} as
     * {@code .prefab.json.lpf}, and {@code findAssetPrefabPath} resolves against that directory
     * with {@code Files.exists} — so only prefabs actually on disk can be found. The cache holds
     * only what worldgen references: of 175 Elemental Circle prefabs, exactly 4 are cached, all
     * {@code Fire/Pillar_Forward}. The last entry is deliberately an UNCACHED prefab, so the
     * cached/uncached distinction is measured rather than assumed.
     */
    private static final String[] CANDIDATE_NAMES = {
        CACHED + ".prefab.json",
        CACHED,
        "Server/Prefabs/" + CACHED + ".prefab.json",
        "Monuments/Unique/Elemental_Circles/Earth/01/Unique_Earth_01_Druid_Circles_1_001.prefab.json",
    };

    public PrefabPasteProbeCommand() {
        super("prefabprobe", "Loads a shipped monument prefab and pastes it, to test runtime prefab placement");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- prefab paste probe (0.6.1) ---\n");

        Vector3d origin = null;
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                origin = new Vector3d(transform.getPosition());
                break;
            }
        }
        if (origin == null) {
            sb.append("no connected player -- headless fallback at x=0 z=0\n");
            int groundY = -1;
            for (int y = 160; y >= 20; y--) {
                Integer b = read(world, 0, y, 0);
                if (b == null) {
                    break;
                }
                if (b != 0) {
                    groundY = y;
                    break;
                }
            }
            if (groundY < 0) {
                sendText(context, sb.append("  no ground in column x=0 z=0\n").toString());
                return;
            }
            origin = new Vector3d(0.5, groundY + 1, 0.5);
            sb.append("  ground at y=").append(groundY).append("\n");
        }

        PrefabStore prefabStore;
        try {
            prefabStore = PrefabStore.get();
        } catch (RuntimeException e) {
            sendText(
                    context,
                    sb.append("PrefabStore.get() THREW ")
                            .append(e.getClass().getSimpleName())
                            .append(": ")
                            .append(e.getMessage())
                            .append("\n")
                            .toString());
            return;
        }
        sb.append("PrefabStore: ").append(prefabStore == null ? "null" : "ok").append("\n");

        int lane = 0;
        for (String name : CANDIDATE_NAMES) {
            Path path;
            try {
                path = prefabStore == null ? null : prefabStore.findAssetPrefabPath(name);
            } catch (RuntimeException e) {
                sb.append("  ")
                        .append(name)
                        .append(" -> findAssetPrefabPath THREW ")
                        .append(e.getClass().getSimpleName())
                        .append("\n");
                continue;
            }
            sb.append("  ").append(name).append("\n    -> ").append(path).append("\n");
            if (path == null) {
                continue;
            }

            PrefabBuffer buffer;
            try {
                buffer = PrefabBufferUtil.loadBuffer(path);
            } catch (Exception e) {
                sb.append("    loadBuffer THREW ")
                        .append(e.getClass().getSimpleName())
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
                continue;
            }
            if (buffer == null) {
                sb.append("    loadBuffer returned null\n");
                continue;
            }

            lane++;
            Vector3i at = new Vector3i(
                    (int) Math.floor(origin.x) + 16 * lane,
                    (int) Math.floor(origin.y),
                    (int) Math.floor(origin.z) - 16 * lane);
            int[] before = snapshot(world, at);
            try {
                PrefabUtil.paste(buffer.newAccess(), world, at, Rotation.None, new Random(1L), store);
            } catch (Exception e) {
                sb.append("    paste THREW ")
                        .append(e.getClass().getSimpleName())
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
                continue;
            }
            int[] after = snapshot(world, at);
            int changed = changedCells(before, after);
            int air = 0;
            int solid = 0;
            int unreadable = 0;
            for (int id : before) {
                if (id < 0) {
                    unreadable++;
                } else if (id == 0) {
                    air++;
                } else {
                    solid++;
                }
            }
            sb.append("    region before: solid=")
                    .append(solid)
                    .append(" air=")
                    .append(air)
                    .append(" unreadable=")
                    .append(unreadable)
                    .append("\n");
            sb.append("    pasted at ")
                    .append(at.x)
                    .append(" ")
                    .append(at.y)
                    .append(" ")
                    .append(at.z)
                    .append(" | CHANGED CELLS ")
                    .append(changed)
                    .append("\n");
        }

        sendText(context, sb.toString());
    }

    // The Fire pillar prefab is 862 blocks spanning x -2..4, y 4..33, z -13..13 with anchor
    // (1,21,4). An earlier +/-8 x 0..16 box caught 3 cells of it and read as a near-total no-op.
    // Size the box from the prefab, generously, and under either anchor convention.
    private static final int R_XZ = 20;
    private static final int Y_LO = -30;
    private static final int Y_HI = 40;

    /**
     * Snapshots every block id in a box around the paste point.
     *
     * <p>An earlier version counted only <em>solid</em> blocks, which cannot see a paste that
     * lands inside terrain: the prefab overwrites rock with rock and the count does not move. At
     * one site the box was 63% solid and a successful paste read as {@code delta 0}. Compare ids
     * cell by cell instead -- insensitive to how buried the site is.
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

    private int changedCells(int[] before, int[] after) {
        int n = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) {
                n++;
            }
        }
        return n;
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

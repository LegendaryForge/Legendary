package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;

/**
 * Debug command: {@code /residueprobe}
 *
 * <p>Answers one question, N3 of the residue-network design: <em>can a plugin emit a world
 * particle at an arbitrary position?</em> Sub-project 3 — crystals and their density expression —
 * rests on the answer, and static reachability is not proof.
 *
 * <p>{@code ActionSpawnParticles} gained a {@code float scale} in 0.6.1, but it is an NPC action
 * driven by role assets and its {@code emitWorldParticle} is private. Reading its bytecode shows
 * the terminal call is {@link ParticleUtil#spawnParticleEffect}, which is a <em>public static</em>
 * utility taking a raw position — so no NPC should be required. This command calls that path
 * directly and places a marker line of particles the operator can look at.
 *
 * <p>A null result here is not "particles are unavailable" — it is "this id or this overload is
 * wrong", which is why several ids are tried and each is named in the output.
 *
 * <p>Markers are placed on three <em>different bearings</em> at three heights each. The first
 * version put all three in a line east and two were hidden — by terrain, and by each other. A
 * marker that cannot be seen is indistinguishable from one that never rendered, so the layout is
 * part of the instrument, not cosmetic.
 */
public class ResidueParticleProbeCommand extends AbstractWorldCommand {

    /**
     * Ids taken from shipped configs rather than guessed: {@code Rain_Heavy} is the world particle
     * on {@code Zone1_Storm}, the other two are named by the Memories block of the default
     * {@code GameplayConfig}.
     */
    private static final String[] CANDIDATE_IDS = {"Rain_Heavy", "Memory_Catch_Rune", "MemoryRecordedStatue"};

    public ResidueParticleProbeCommand() {
        super("residueprobe", "Emits world particles at fixed offsets to test plugin-side particle spawning");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- residue particle probe ---\n");

        List<Ref<EntityStore>> viewers = new ArrayList<>();
        Vector3d origin = null;

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) {
                continue;
            }
            viewers.add(ref);
            if (origin == null) {
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    origin = new Vector3d(transform.getPosition());
                }
            }
        }

        if (viewers.isEmpty() || origin == null) {
            sendText(
                    context,
                    sb.append("no connected player with a transform -- join first\n")
                            .toString());
            return;
        }

        sb.append("origin: ")
                .append(String.format("%.1f %.1f %.1f", origin.x, origin.y, origin.z))
                .append(" | viewers: ")
                .append(viewers.size())
                .append("\n");

        // Three distinct compass bearings at a fixed radius, well above the player's feet.
        // The first layout put all three in a line east, where terrain and each other hid two of
        // them -- co-linear markers cannot be told apart from markers that failed to render.
        double[][] bearings = {{1.0, 0.0}, {0.0, 1.0}, {-1.0, 0.0}};
        String[] names = {"EAST", "NORTH", "WEST"};
        double radius = 6.0;

        for (int i = 0; i < CANDIDATE_IDS.length; i++) {
            String id = CANDIDATE_IDS[i];
            double x = origin.x + bearings[i][0] * radius;
            double z = origin.z + bearings[i][1] * radius;
            int emitted = 0;
            StringBuilder err = new StringBuilder();
            // Stack three heights: if one is buried in a hill, the others clear it.
            for (double dy : new double[] {1.0, 3.0, 5.0}) {
                try {
                    ParticleUtil.spawnParticleEffect(id, x, origin.y + dy, z, viewers, store);
                    emitted++;
                } catch (RuntimeException e) {
                    if (err.length() == 0) {
                        err.append(e.getClass().getSimpleName()).append(": ").append(String.valueOf(e.getMessage()));
                    }
                }
            }
            sb.append("  ")
                    .append(names[i])
                    .append(" (")
                    .append(id)
                    .append(") -> ")
                    .append(emitted)
                    .append("/3 at ")
                    .append(String.format("%.0f %.0f %.0f", x, origin.y + 3.0, z));
            if (err.length() > 0) {
                sb.append(" THREW ").append(err);
            }
            sb.append("\n");
        }

        sb.append("dispatched != rendered. Turn a full circle; markers are 6 blocks out.\n");
        sendText(context, sb.toString());
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

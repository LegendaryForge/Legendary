package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Debug command: {@code /blockprobe}
 *
 * <p>Answers the question the residue design actually rests on: <em>can a plugin place a
 * harvestable block at an arbitrary position at runtime?</em> Act III literacy is reading crystal
 * density, and Class C crystals are harvested, depleted and cultivated — so they are blocks, not
 * particles. {@code /residueprobe} proved particle emission and that is what the network spec
 * cites as unblocking world expression; particles are not harvestable, so the cited evidence does
 * not reach the claim.
 *
 * <p>The alternative path is worldgen, which is deferral #18 and blocked, so a negative result
 * here is a design constraint rather than a bug.
 *
 * <p>Written against 0.6.1 signatures read from the installed jar, NOT from
 * {@code docs/integration/hytale-capability-audit.md}, whose row for this capability names
 * {@code world.accessor.BlockAccessor} with {@code placeBlock} / {@code testPlaceBlock} and eight
 * {@code setBlock} overloads. That class does not exist in 0.6.1. The block API lives on
 * {@code IChunkAccessorSync}, which {@link World} implements, and it has two {@code setBlock}
 * overloads and no {@code placeBlock}.
 *
 * <p>Ids are shipped block items with an embedded {@code BlockType}, taken from
 * {@code Server/Item/Items/Rock/Crystal/}. Each reports independently so one bad id cannot be read
 * as "placement is unavailable".
 */
public class BlockPlacementProbeCommand extends AbstractWorldCommand {

    private static final String[] CANDIDATE_BLOCKS = {
        "Rock_Crystal_Yellow_Small",
        "Rock_Crystal_Yellow_Medium",
        "Rock_Crystal_Yellow_Large",
        "Rock_Crystal_White_Medium",
        "Rock_Crystal_Iridescent_Medium",
    };

    public BlockPlacementProbeCommand() {
        super("blockprobe", "Places, reads back and breaks shipped crystal blocks to test runtime block placement");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- block placement probe (0.6.1) ---\n");

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
        // Headless fallback. Block placement is verifiable by reading the world back, unlike
        // particles, which needed a pair of eyes -- so this probe should not require a client.
        // Scans down the column at the origin for the first non-air block and works just above it.
        if (origin == null) {
            sb.append("no connected player -- using headless fallback at x=0 z=0\n");
            Integer ground = null;
            int groundY = -1;
            for (int y = 160; y >= 20; y--) {
                Integer b = read(world, 0, y, 0);
                if (b == null) {
                    sb.append("  getBlock THREW at y=").append(y).append(" -- column unreadable\n");
                    break;
                }
                if (b != 0) {
                    ground = b;
                    groundY = y;
                    break;
                }
            }
            if (ground == null) {
                sendText(
                        context,
                        sb.append("  no solid block found in column x=0 z=0 (y 160..20);")
                                .append(" chunk probably not loaded\n")
                                .toString());
                return;
            }
            sb.append("  ground id=")
                    .append(ground)
                    .append(" at y=")
                    .append(groundY)
                    .append("\n");
            origin = new Vector3d(0.5, groundY + 1, 0.5);
        }

        int oy = (int) Math.floor(origin.y);
        sb.append("origin: ")
                .append(String.format("%.1f %.1f %.1f", origin.x, origin.y, origin.z))
                .append("\n\n");

        // Spread across bearings so terrain cannot hide a placement, and so two failures cannot
        // be mistaken for one. Same reasoning as the particle probe's layout.
        double[][] bearings = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}};
        int radius = 4;

        for (int i = 0; i < CANDIDATE_BLOCKS.length; i++) {
            String id = CANDIDATE_BLOCKS[i];
            int x = (int) Math.floor(origin.x + bearings[i][0] * radius);
            int z = (int) Math.floor(origin.z + bearings[i][1] * radius);
            sb.append(id).append("\n");

            // Try feet level and one above: one of the two is normally air above solid ground.
            for (int dy : new int[] {0, 1}) {
                int y = oy + dy;
                sb.append(String.format("  (%d %d %d) ", x, y, z));

                Integer before = read(world, x, y, z);
                sb.append("before=").append(before == null ? "THREW" : before);

                String setErr = null;
                try {
                    world.setBlock(x, y, z, id);
                } catch (RuntimeException e) {
                    setErr = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                if (setErr != null) {
                    sb.append(" | setBlock THREW ").append(setErr).append("\n");
                    continue;
                }

                Integer after = read(world, x, y, z);
                sb.append(" | after=").append(after == null ? "THREW" : after);
                sb.append(" | type=").append(typeId(world, x, y, z));
                boolean changed = before != null && after != null && !before.equals(after);
                sb.append(changed ? " | CHANGED" : " | no-change");
                sb.append("\n");
            }
        }

        // Break the first placement and confirm the world reports it gone.
        int bx = (int) Math.floor(origin.x + radius);
        int bz = (int) Math.floor(origin.z);
        sb.append("\nbreakBlock at (")
                .append(bx)
                .append(" ")
                .append(oy + 1)
                .append(" ")
                .append(bz)
                .append("): ");
        try {
            boolean broke = world.breakBlock(bx, oy + 1, bz, 0);
            sb.append("returned ").append(broke).append(" | now=").append(read(world, bx, oy + 1, bz));
        } catch (RuntimeException e) {
            sb.append("THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage());
        }
        sb.append("\n\nPERSISTENCE IS NOT COVERED by this probe -- re-read after a restart.\n");
        sendText(context, sb.toString());
    }

    private Integer read(World world, int x, int y, int z) {
        try {
            return world.getBlock(x, y, z);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String typeId(World world, int x, int y, int z) {
        try {
            BlockType type = world.getBlockType(x, y, z);
            return type == null ? "null" : String.valueOf(type.getId());
        } catch (RuntimeException e) {
            return "THREW " + e.getClass().getSimpleName();
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

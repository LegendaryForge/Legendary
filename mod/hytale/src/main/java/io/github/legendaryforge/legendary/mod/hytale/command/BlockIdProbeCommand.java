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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Debug command: {@code /blockidprobe}
 *
 * <p>Prints {@code getBlock} through the player's column so the id that means air can be read off
 * ground truth rather than assumed.
 *
 * <p><strong>Why this exists as its own probe.</strong> Earth counts solid points and Wind counts
 * open ones, so a wrong air id inverts <em>both</em> elements at once while still compiling and
 * still producing plausible-looking anchors. {@code SectionReaderTerrainSample.isSolidAt} therefore
 * throws until this has been run — a guess that compiles is how that ships unnoticed.
 *
 * <p>Anchored on a connected player because a headless server holds no loaded region at any offset
 * from 0 to 4096 (measured 2026-10-21), so every read would be a cheap miss and the answer would
 * come back uniform for the wrong reason. {@code hasStorageInMemory} is printed per row for the same
 * reason: an id read from unloaded storage is not evidence about anything.
 *
 * <p>Sky light is deliberately absent. Reading it needs a {@code BlockSection} component walk via
 * {@code reader.sectionAt}, and this probe's question does not involve exposure — adding the walk
 * would be more surface for no answer.
 *
 * <p>Prints {@code BLOCKID_VERDICT:} as its last line.
 */
public class BlockIdProbeCommand extends AbstractWorldCommand {

    public BlockIdProbeCommand() {
        super("blockidprobe", "Prints getBlock through the player's column, to identify the air block id");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- block id probe ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players connected; a headless server loads no region, so every read\n")
                    .append("would be a cheap miss and come back uniform for the wrong reason.\n")
                    .append("BLOCKID_VERDICT: INCONCLUSIVE | no players\n");
            send(context, sb.toString());
            return;
        }

        SectionReader reader = new SectionReader(world.getChunkStore());
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                sb.append("[").append(playerRef.getUsername()).append("] TransformComponent ABSENT\n");
                continue;
            }
            Vector3d pos = transform.getPosition();
            int x = MathUtil.floor(pos.x);
            int z = MathUtil.floor(pos.z);
            int feet = MathUtil.floor(pos.y);

            sb.append("player ")
                    .append(playerRef.getUsername())
                    .append(" at ")
                    .append(x)
                    .append(',')
                    .append(feet)
                    .append(',')
                    .append(z)
                    .append('\n');
            sb.append("  y   getBlock  getFluidId  loaded\n");
            for (int y = 0; y <= 319; y += 16) {
                boolean loaded = reader.canHoldY(y) && reader.hasStorageInMemory(x, y, z);
                sb.append(String.format(
                        "  %3d %9s %11s %7b%n",
                        y,
                        loaded ? Integer.toString(reader.getBlock(x, y, z)) : "-",
                        loaded ? Integer.toString(reader.getFluidId(x, y, z)) : "-",
                        loaded));
            }
            // The player's own feet level, printed separately because it is the one row whose
            // answer is known with certainty: you are standing here, so it is not solid.
            boolean feetLoaded = reader.canHoldY(feet) && reader.hasStorageInMemory(x, feet, z);
            sb.append("  ---\n");
            sb.append(String.format(
                    "  %3d %9s %11s %7b   <- your feet: known to be AIR%n",
                    feet,
                    feetLoaded ? Integer.toString(reader.getBlock(x, feet, z)) : "-",
                    feetLoaded ? Integer.toString(reader.getFluidId(x, feet, z)) : "-",
                    feetLoaded));

            sb.append("\nEXPECTED, from the fact that you are standing on ground in the open:\n")
                    .append("  y=319      air    (far above any terrain)\n")
                    .append("  feet row   air    (you are standing in it)\n")
                    .append("  y=8        solid  (near bedrock)\n")
                    .append("The air id is the value the first two agree on AND the third differs from.\n")
                    .append("If no single value satisfies all three, STOP — getBlock does not mean what\n")
                    .append("this probe assumes, and isSolidAt must not be written from it.\n");
            sb.append("BLOCKID_VERDICT: READ | compare the three expected rows\n");
            send(context, sb.toString());
            return;
        }
        sb.append("no player had a TransformComponent; cannot anchor\n")
                .append("BLOCKID_VERDICT: INCONCLUSIVE | no transform\n");
        send(context, sb.toString());
    }

    /**
     * Mirrors the private helper in the sibling probe commands.
     *
     * <p>{@code AbstractWorldCommand} supplies no {@code send}, which is easy to assume from reading
     * a sibling and finding one — it is private to that class. Copied rather than extracted because
     * hoisting it into a shared base is a refactor of every probe command, not part of this change.
     */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

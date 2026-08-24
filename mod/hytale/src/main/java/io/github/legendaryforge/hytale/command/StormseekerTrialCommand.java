package io.github.legendaryforge.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import io.github.legendaryforge.hytale.stormseeker.HytaleStormseekerHost;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;

/**
 * Debug command: /ss-trial
 *
 * <p>Enters the Anchored Trial for the executing player.
 * The trial requires the player to stand still for ~40 ticks to earn Sigil B.
 * Use /stormseeker to check if Sigil B was granted.
 */
public class StormseekerTrialCommand extends AbstractPlayerCommand {

    private final HytaleStormseekerHost host;

    public StormseekerTrialCommand(HytaleStormseekerHost host) {
        super("ss-trial", "Enter Anchored Trial (debug)");
        this.host = host;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        String playerId = playerRef.getUuid().toString();
        StormseekerProgress progress = host.progress(playerId);

        if (progress.hasSigilB()) {
            sendText(context, "[Stormseeker] You already have Sigil B (Anchored).");
            return;
        }

        boolean entered = StormseekerWiring.enterAnchoredTrial(playerId, progress);
        if (entered) {
            sendText(context, "[Stormseeker] Entered Anchored Trial. Stand still to prove control...");
        } else {
            sendText(context, "[Stormseeker] Could not enter Anchored Trial. Already participating or denied.");
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

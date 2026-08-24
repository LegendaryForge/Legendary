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
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;

/**
 * Debug command: /ss-advance
 *
 * <p>Advances the player's quest phase (calls advanceIfEligible).
 * For testing only — skips normal progression requirements for early phases.
 */
public class StormseekerAdvanceCommand extends AbstractPlayerCommand {

    private final HytaleStormseekerHost host;

    public StormseekerAdvanceCommand(HytaleStormseekerHost host) {
        super("ss-advance", "Advance Stormseeker quest phase (debug)");
        this.host = host;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        String playerId = playerRef.getUuid().toString();
        StormseekerProgress progress = host.progress(playerId);

        var before = progress.phase();
        progress.advanceIfEligible();
        var after = progress.phase();

        if (before == after) {
            sendText(context, "[Stormseeker] Cannot advance from " + before
                    + " — exit conditions not met (need both sigils for Phase 2 exit).");
        } else {
            sendText(context, "[Stormseeker] Advanced: " + before + " -> " + after);
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

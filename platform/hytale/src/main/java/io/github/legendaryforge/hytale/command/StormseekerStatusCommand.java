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
import io.github.legendaryforge.hytale.stormseeker.HytaleWeatherReader;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;

/**
 * Debug command: /stormseeker
 *
 * <p>Shows quest state, motion, position, weather, and Flowing Trial status.
 */
public class StormseekerStatusCommand extends AbstractPlayerCommand {

    private final HytaleStormseekerHost host;

    public StormseekerStatusCommand(HytaleStormseekerHost host) {
        super("stormseeker", "Shows Stormseeker quest debug info");
        this.host = host;
    }

    @Override
    protected void execute(CommandContext context, Store<EntityStore> store,
                           Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        String playerId = playerRef.getUuid().toString();
        String username = playerRef.getUsername();

        StormseekerProgress progress = host.progress(playerId);
        MotionSample motion = host.motionSample(playerId);

        StringBuilder sb = new StringBuilder();
        sb.append("--- Stormseeker Status: ").append(username).append(" ---\n");
        sb.append("Phase: ").append(progress.phase()).append("\n");
        sb.append("Sigil A (Flowing): ").append(progress.hasSigilA() ? "GRANTED" : "not earned").append("\n");
        sb.append("Sigil B (Anchored): ").append(progress.hasSigilB() ? "GRANTED" : "not earned").append("\n");
        sb.append("Motion: dx=").append(String.format("%.3f", motion.dx()));
        sb.append(" dy=").append(String.format("%.3f", motion.dy()));
        sb.append(" dz=").append(String.format("%.3f", motion.dz()));
        sb.append(" moving=").append(motion.moving()).append("\n");

        var pos = playerRef.getTransform();
        if (pos != null) {
            var p = pos.getPosition();
            sb.append("Position: ").append(String.format("%.1f, %.1f, %.1f", p.getX(), p.getY(), p.getZ())).append("\n");
        }

        // Weather
        String weatherId = HytaleWeatherReader.getWeatherId(playerRef);
        int weatherIndex = HytaleWeatherReader.getWeatherIndex(playerRef);
        boolean isStorm = HytaleWeatherReader.isStorm(playerRef);
        sb.append("Weather: ").append(weatherId).append(" (index=").append(weatherIndex).append(")");
        sb.append(isStorm ? " [STORM]" : "").append("\n");

        // Flowing Trial
        FlowingTrialSessionStep flowStep = host.lastFlowingStep(playerId);
        if (flowStep != null) {
            sb.append("--- Flowing Trial ---\n");
            sb.append("Trial Status: ").append(flowStep.status()).append("\n");
            sb.append("Hint: intensity=").append(String.format("%.2f", flowStep.hint().intensity()));
            sb.append(" stability=").append(String.format("%.2f", flowStep.hint().stability()));
            sb.append(" dir=").append(String.format("%.2f", flowStep.hint().directionHintStrength())).append("\n");
            if (flowStep.sigilGrantedThisTick()) {
                sb.append(">>> SIGIL A GRANTED THIS TICK <<<\n");
            }
        } else {
            sb.append("Flowing Trial: not active\n");
        }

        sendText(context, sb.toString());
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

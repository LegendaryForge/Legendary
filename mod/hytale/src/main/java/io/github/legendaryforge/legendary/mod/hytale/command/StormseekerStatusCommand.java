package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherKinds;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.HytaleStormseekerHost;
import io.github.legendaryforge.legendary.mod.hytale.weather.HytaleWeatherBackend;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;

/**
 * Debug command: /stormseeker
 *
 * <p>Shows quest state, motion, position, weather, and Flowing Trial status.
 */
public class StormseekerStatusCommand extends AbstractPlayerCommand {

    private final HytaleStormseekerHost host;
    private final WeatherGate weatherGate;
    private final HytaleWeatherBackend weatherBackend;

    public StormseekerStatusCommand(
            HytaleStormseekerHost host, WeatherGate weatherGate, HytaleWeatherBackend weatherBackend) {
        super("stormseeker", "Shows Stormseeker quest debug info");
        this.host = host;
        this.weatherGate = weatherGate;
        this.weatherBackend = weatherBackend;
    }

    @Override
    protected void execute(
            CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        String playerId = playerRef.getUuid().toString();
        String username = playerRef.getUsername();

        StormseekerProgress progress = host.progress(playerId);
        MotionSample motion = host.motionSample(playerId);

        StringBuilder sb = new StringBuilder();
        sb.append("--- Stormseeker Status: ").append(username).append(" ---\n");
        sb.append("Phase: ").append(progress.phase()).append("\n");
        sb.append("the Coursing Lodestone (Flowing): ")
                .append(progress.hasCoursingLodestone() ? "GRANTED" : "not earned")
                .append("\n");
        sb.append("the Standing Lodestone (Anchored): ")
                .append(progress.hasStandingLodestone() ? "GRANTED" : "not earned")
                .append("\n");
        sb.append("Motion: dx=").append(String.format("%.3f", motion.dx()));
        sb.append(" dy=").append(String.format("%.3f", motion.dy()));
        sb.append(" dz=").append(String.format("%.3f", motion.dz()));
        sb.append(" moving=").append(motion.moving()).append("\n");

        var pos = playerRef.getTransform();
        if (pos != null) {
            var p = pos.getPosition();
            sb.append("Position: ")
                    .append(String.format("%.1f, %.1f, %.1f", p.x(), p.y(), p.z()))
                    .append("\n");
        }

        // Weather
        // Through the port. The raw index comes from the adapter directly because core
        // deliberately holds no asset-map ordinal -- it is debug output, not a contract.
        var subject = new io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef(playerId);
        var weather = weatherGate.effective(subject);
        sb.append("Weather: ")
                .append(weather.map(WeatherId::value).orElse("unavailable"))
                .append(" (index=")
                .append(weatherBackend.rawIndex(playerId))
                .append(")");
        sb.append(weather.map(WeatherKinds::isStorm).orElse(false) ? " [STORM]" : "")
                .append("\n");

        // Flowing Trial
        FlowingTrialSessionStep flowStep = host.lastFlowingStep(playerId);
        if (flowStep != null) {
            sb.append("--- Flowing Trial ---\n");
            sb.append("Trial Status: ").append(flowStep.status()).append("\n");
            sb.append("Hint: intensity=")
                    .append(String.format("%.2f", flowStep.hint().intensity()));
            sb.append(" stability=")
                    .append(String.format("%.2f", flowStep.hint().stability()));
            sb.append(" dir=")
                    .append(String.format("%.2f", flowStep.hint().directionHintStrength()))
                    .append("\n");
            if (flowStep.lodestoneGrantedThisTick()) {
                sb.append(">>> COURSING LODESTONE GRANTED THIS TICK <<<\n");
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

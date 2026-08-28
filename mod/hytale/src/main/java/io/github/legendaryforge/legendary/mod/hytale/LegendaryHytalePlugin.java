package io.github.legendaryforge.legendary.mod.hytale;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.legendaryforge.legendary.mod.hytale.command.ResidueParticleProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.StormseekerAdvanceCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.StormseekerStatusCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.StormseekerTrialCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.WeatherConditionProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.HytaleStormseekerHost;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.StormseekerStartLineInteraction;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.StormseekerTickSystem;
import io.github.legendaryforge.legendary.quests.stormseeker.persistence.PropertiesProgressStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public class LegendaryHytalePlugin extends JavaPlugin {

    private HytaleStormseekerHost stormseekerHost;

    public LegendaryHytalePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        getLogger().atInfo().log("LegendaryHytale plugin initializing...");
    }

    @Override
    protected void setup() {
        super.setup();

        // Must happen here, not in start(): setup() runs before assets are parsed, and the
        // Furniture_Stormseeker_Inscription_Five block asset references "StormseekerStartLine"
        // by Type. Registering it any later would leave the asset with an unresolvable
        // interaction type. Confirmed against the first-party ObjectiveReputationPlugin,
        // which registers its codecs in setup() for the same reason.
        Interaction.CODEC.register(
                "StormseekerStartLine", StormseekerStartLineInteraction.class, StormseekerStartLineInteraction.CODEC);
        getLogger().atInfo().log("Registered interaction type: StormseekerStartLine");
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("LegendaryHytale plugin enabled!");

        Path dataDir = Path.of("plugins", "LegendaryHytale", "data", "stormseeker");
        PropertiesProgressStore progressStore = new PropertiesProgressStore(dataDir);
        getLogger().atInfo().log("Progress store: " + dataDir.toAbsolutePath());

        stormseekerHost = new HytaleStormseekerHost(progressStore);
        getLogger().atInfo().log("Stormseeker host runtime created.");

        StormseekerTickSystem tickSystem = new StormseekerTickSystem(stormseekerHost);
        getEntityStoreRegistry().registerSystem(tickSystem);
        getLogger().atInfo().log("Stormseeker tick system registered.");

        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getLogger().atInfo().log("Player event listeners registered.");

        getCommandRegistry().registerCommand(new StormseekerStatusCommand(stormseekerHost));
        getCommandRegistry().registerCommand(new StormseekerAdvanceCommand(stormseekerHost));
        getCommandRegistry().registerCommand(new StormseekerTrialCommand(stormseekerHost));
        getCommandRegistry().registerCommand(new WeatherConditionProbeCommand());
        getCommandRegistry().registerCommand(new ResidueParticleProbeCommand());
        getLogger()
                .atInfo()
                .log("Commands registered: /stormseeker, /ss-advance, /ss-trial, /weatherprobe, /residueprobe");
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        String playerId = playerRef.getUuid().toString();
        String username = playerRef.getUsername();

        stormseekerHost.addPlayer(playerId, playerRef);
        getLogger().atInfo().log("Player joined: " + username + " (" + playerId + ") — quest tracking started.");
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        String playerId = playerRef.getUuid().toString();
        String username = playerRef.getUsername();

        stormseekerHost.removePlayer(playerId);
        getLogger().atInfo().log("Player left: " + username + " (" + playerId + ") — quest tracking paused.");
    }

    @Override
    protected void shutdown() {
        if (stormseekerHost != null) {
            stormseekerHost.saveAll();
        }
        getLogger().atInfo().log("LegendaryHytale plugin disabled.");
    }
}

package io.github.legendaryforge.legendary.mod.hytale;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultPlayerWeatherController;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultWeatherGate;
import io.github.legendaryforge.legendary.core.internal.weather.OverrideRegistry;
import io.github.legendaryforge.legendary.mod.hytale.command.BeamProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.BlockPlacementProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.BlockSpoofProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.PointStoreProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.PrefabPasteProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.ResidueParticleProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.StormseekerAdvanceCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.StormseekerStatusCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.StormseekerTrialCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.WeatherConditionProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.WeatherOverrideProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.command.WeatherPortProbeCommand;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.HytaleStormseekerHost;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.StormseekerStartLineInteraction;
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.StormseekerTickSystem;
import io.github.legendaryforge.legendary.mod.hytale.weather.HytaleWeatherBackend;
import io.github.legendaryforge.legendary.quests.stormseeker.persistence.PropertiesProgressStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;

public class LegendaryHytalePlugin extends JavaPlugin {

    private HytaleStormseekerHost stormseekerHost;
    private HytaleWeatherBackend weatherBackend;
    private WeatherGate weatherGate;
    private PlayerWeatherController playerWeather;

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

        weatherBackend = new HytaleWeatherBackend();
        // ONE registry instance shared by both halves. Given separate ones nothing fails --
        // effective() simply stops seeing claims and quietly reports world weather instead.
        OverrideRegistry overrideRegistry = new OverrideRegistry();
        weatherGate = new DefaultWeatherGate(weatherBackend, overrideRegistry);
        playerWeather = new DefaultPlayerWeatherController(weatherBackend, overrideRegistry);
        getLogger().atInfo().log("Weather port wired (WeatherGate + PlayerWeatherController).");

        stormseekerHost = new HytaleStormseekerHost(progressStore);
        getLogger().atInfo().log("Stormseeker host runtime created.");

        StormseekerTickSystem tickSystem = new StormseekerTickSystem(stormseekerHost, weatherBackend);
        getEntityStoreRegistry().registerSystem(tickSystem);
        getLogger().atInfo().log("Stormseeker tick system registered.");

        getEventRegistry().registerGlobal(PlayerConnectEvent.class, this::onPlayerConnect);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getLogger().atInfo().log("Player event listeners registered.");

        getCommandRegistry()
                .registerCommand(new StormseekerStatusCommand(stormseekerHost, weatherGate, weatherBackend));
        getCommandRegistry().registerCommand(new StormseekerAdvanceCommand(stormseekerHost));
        getCommandRegistry().registerCommand(new StormseekerTrialCommand(stormseekerHost));
        getCommandRegistry().registerCommand(new WeatherConditionProbeCommand());
        getCommandRegistry().registerCommand(new WeatherOverrideProbeCommand());
        getCommandRegistry().registerCommand(new WeatherPortProbeCommand());
        getCommandRegistry().registerCommand(new ResidueParticleProbeCommand());
        getCommandRegistry().registerCommand(new BlockPlacementProbeCommand());
        getCommandRegistry().registerCommand(new BlockSpoofProbeCommand());
        getCommandRegistry().registerCommand(new PrefabPasteProbeCommand());
        getCommandRegistry().registerCommand(new BeamProbeCommand());
        getCommandRegistry().registerCommand(new PointStoreProbeCommand());
        getLogger()
                .atInfo()
                .log(
                        "Commands registered: /stormseeker, /ss-advance, /ss-trial, /weatherprobe, /weatherportprobe, /residueprobe, /blockprobe, /blockspoofprobe, /prefabprobe, /beamprobe, /pointprobe");
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        String playerId = playerRef.getUuid().toString();
        String username = playerRef.getUsername();

        stormseekerHost.addPlayer(playerId, playerRef);
        weatherBackend.track(playerId, playerRef);
        getLogger().atInfo().log("Player joined: " + username + " (" + playerId + ") — quest tracking started.");
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        String playerId = playerRef.getUuid().toString();
        String username = playerRef.getUsername();

        stormseekerHost.removePlayer(playerId);
        // Both halves together, because they are one lifecycle: untrack drops the engine ref,
        // forget drops our owner registry entry. Dropping only the first leaks an owner per
        // departed player, after which the next claim on a recycled id is wrongly refused.
        weatherBackend.untrack(playerId);
        playerWeather.forget(new io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef(playerId));
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

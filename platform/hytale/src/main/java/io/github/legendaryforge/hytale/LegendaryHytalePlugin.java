package io.github.legendaryforge.hytale;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import io.github.legendaryforge.hytale.command.StormseekerAdvanceCommand;
import io.github.legendaryforge.hytale.command.StormseekerStatusCommand;
import io.github.legendaryforge.hytale.command.StormseekerTrialCommand;
import io.github.legendaryforge.hytale.stormseeker.HytaleStormseekerHost;
import io.github.legendaryforge.hytale.stormseeker.StormseekerProgressStore;
import io.github.legendaryforge.hytale.stormseeker.StormseekerTickSystem;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class LegendaryHytalePlugin extends JavaPlugin {

    private HytaleStormseekerHost stormseekerHost;

    public LegendaryHytalePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        getLogger().atInfo().log("LegendaryHytale plugin initializing...");
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("LegendaryHytale plugin enabled!");

        Path dataDir = Path.of("plugins", "LegendaryHytale", "data", "stormseeker");
        StormseekerProgressStore progressStore = new StormseekerProgressStore(dataDir);
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
        getLogger().atInfo().log("Commands registered: /stormseeker, /ss-advance, /ss-trial");
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

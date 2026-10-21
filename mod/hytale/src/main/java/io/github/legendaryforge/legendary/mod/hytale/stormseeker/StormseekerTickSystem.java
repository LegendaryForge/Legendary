package io.github.legendaryforge.legendary.mod.hytale.stormseeker;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.mod.hytale.weather.HytaleWeatherBackend;
import io.github.legendaryforge.legendary.quests.stormseeker.StormseekerWiring;

public final class StormseekerTickSystem extends TickingSystem<EntityStore> {

    private final HytaleStormseekerHost host;
    private final HytaleWeatherBackend weatherBackend;

    public StormseekerTickSystem(HytaleStormseekerHost host, HytaleWeatherBackend weatherBackend) {
        this.host = host;
        this.weatherBackend = weatherBackend;
    }

    @Override
    public void tick(float dt, int tickCount, Store<EntityStore> store) {
        // The only route to a Store<EntityStore> in this process -- PluginBase and JavaPlugin
        // expose none. Without this the weather port cannot resolve the unoverridden reading at
        // all. A questline-named tick system feeding a generic port is acceptable for one
        // questline and wrong for two; when questline #2 arrives this becomes its own
        // WeatherTickSystem, registered independently.
        weatherBackend.onTick(store);
        host.updateAllPositions();
        StormseekerWiring.tick(host);
    }
}

package io.github.legendaryforge.legendary.mod.hytale.stormseeker;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.quests.stormseeker.StormseekerWiring;

public final class StormseekerTickSystem extends TickingSystem<EntityStore> {

    private final HytaleStormseekerHost host;

    public StormseekerTickSystem(HytaleStormseekerHost host) {
        this.host = host;
    }

    @Override
    public void tick(float dt, int tickCount, Store<EntityStore> store) {
        host.updateAllPositions();
        StormseekerWiring.tick(host);
    }
}

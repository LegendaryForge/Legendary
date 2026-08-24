package io.github.legendaryforge.legendary.core.api.platform;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.identity.PlayerDirectory;
import io.github.legendaryforge.legendary.core.api.lifecycle.Lifecycle;
import io.github.legendaryforge.legendary.core.api.lifecycle.ServiceRegistry;
import io.github.legendaryforge.legendary.core.api.registry.RegistryAccess;
import java.time.Clock;
import java.util.Optional;

/**
 * Platform-provided runtime access point for LegendaryCore.
 *
 * <p>Platforms (e.g. Hytale) are responsible for creating and wiring an implementation of this
 * interface. Legendary mods should depend only on this interface and other public {@code core.api.*}
 * types, not on platform-specific classes.</p>
 */
public interface CoreRuntime {

    RegistryAccess registries();

    Lifecycle lifecycle();

    ServiceRegistry services();

    EventBus events();

    EncounterManager encounters();

    /**
     * Optional platform-provided player identity directory.
     *
     * <p>This is intentionally optional in v0.1 to avoid forcing platform coupling early.</p>
     */
    default Optional<PlayerDirectory> players() {
        return Optional.empty();
    }

    /**
     * Optional platform-provided party membership directory.
     *
     * <p>This is intentionally optional in v0.1 to avoid forcing platform coupling early.</p>
     */
    default Optional<PartyDirectory> parties() {
        return Optional.empty();
    }

    /**
     * Canonical time source for runtime services and telemetry.
     *
     * <p>Defaults to UTC system clock. Platforms may override to control time behavior.
     */
    default Clock clock() {
        return Clock.systemUTC();
    }
}

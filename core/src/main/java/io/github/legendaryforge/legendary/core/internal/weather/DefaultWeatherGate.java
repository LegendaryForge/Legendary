package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import java.util.Objects;
import java.util.Optional;

public final class DefaultWeatherGate implements WeatherGate {

    private final WeatherBackend backend;
    private final OverrideRegistry registry;

    public DefaultWeatherGate(WeatherBackend backend, OverrideRegistry registry) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Computed, not read. The engine has no override getter, so the claimed weather comes from our
     * own registry; without a claim the player sees what the world gives them.
     *
     * <p>Sound because the port owns every legitimate claim — it is blind only to a caller using
     * the engine's unchecked setters directly, which is what the port exists to prevent.
     */
    @Override
    public Optional<WeatherId> effective(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        if (!backend.hasTracker(subject)) {
            return Optional.empty();
        }
        Optional<WeatherId> claimed = registry.claimedWeather(subject);
        return claimed.isPresent() ? claimed : backend.unoverriddenWeather(subject);
    }

    @Override
    public Optional<WeatherId> unoverridden(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return backend.hasTracker(subject) ? backend.unoverriddenWeather(subject) : Optional.empty();
    }
}

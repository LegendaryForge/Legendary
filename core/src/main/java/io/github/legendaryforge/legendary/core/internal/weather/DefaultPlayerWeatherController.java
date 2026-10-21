package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimResult;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherReleaseStatus;
import java.util.Objects;
import java.util.Optional;

public final class DefaultPlayerWeatherController implements PlayerWeatherController {

    private final WeatherBackend backend;
    private final OverrideRegistry registry;

    public DefaultPlayerWeatherController(WeatherBackend backend, OverrideRegistry registry) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public WeatherClaimResult claim(PlayerRef subject, WeatherId weather, OverrideOwner owner) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(weather, "weather");
        Objects.requireNonNull(owner, "owner");

        if (!backend.hasTracker(subject)) {
            return WeatherClaimResult.of(WeatherClaimStatus.SUBJECT_UNAVAILABLE);
        }

        // Rejected before the engine is touched. Claiming the sentinel returns true and does
        // nothing, so sending it would hand the caller a success it did not get.
        switch (backend.resolve(weather)) {
            case UNKNOWN -> {
                return WeatherClaimResult.of(WeatherClaimStatus.UNKNOWN_WEATHER);
            }
            case SENTINEL -> {
                return WeatherClaimResult.of(WeatherClaimStatus.UNSUPPORTED_WEATHER);
            }
            case OK -> {
                // proceed
            }
        }

        OverrideOwner current = registry.owner(subject).orElse(null);
        if (current != null && !current.equals(owner)) {
            return WeatherClaimResult.refusedHeldBy(current);
        }

        if (!backend.applyOverride(subject, weather)) {
            return WeatherClaimResult.of(WeatherClaimStatus.SUBJECT_UNAVAILABLE);
        }

        registry.put(subject, owner, weather);
        return WeatherClaimResult.of(current == null ? WeatherClaimStatus.GRANTED : WeatherClaimStatus.UPDATED);
    }

    @Override
    public WeatherReleaseStatus release(PlayerRef subject, OverrideOwner owner) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(owner, "owner");

        OverrideOwner current = registry.owner(subject).orElse(null);
        if (current == null) {
            return WeatherReleaseStatus.NOT_HELD;
        }
        if (!current.equals(owner)) {
            return WeatherReleaseStatus.REFUSED_NOT_OWNER;
        }
        if (!backend.hasTracker(subject)) {
            registry.remove(subject);
            return WeatherReleaseStatus.SUBJECT_UNAVAILABLE;
        }

        backend.clearOverride(subject);
        registry.remove(subject);
        return WeatherReleaseStatus.RELEASED;
    }

    @Override
    public Optional<OverrideOwner> holder(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return registry.owner(subject);
    }

    @Override
    public void forget(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        registry.remove(subject);
    }
}

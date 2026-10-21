package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Programmable {@link WeatherBackend} for core tests. No engine, no platform types. */
final class FakeWeatherBackend implements WeatherBackend {

    private final Set<String> tracked = new HashSet<>();
    private final Map<String, WeatherId> unoverridden = new HashMap<>();
    private final Map<WeatherId, WeatherResolution> resolutions = new HashMap<>();
    private final Map<String, WeatherId> applied = new HashMap<>();

    private int applyCalls;
    private int clearCalls;
    private boolean failNextApply;

    void withTracker(String id) {
        tracked.add(id);
    }

    void withoutTracker(String id) {
        tracked.remove(id);
    }

    void setUnoverridden(String id, WeatherId weather) {
        unoverridden.put(id, weather);
    }

    /** Anything not registered resolves UNKNOWN, so a test must opt in to a usable weather. */
    void register(WeatherId weather, WeatherResolution resolution) {
        resolutions.put(weather, resolution);
    }

    void failNextApply() {
        failNextApply = true;
    }

    WeatherId overrideOf(String id) {
        return applied.get(id);
    }

    int applyCalls() {
        return applyCalls;
    }

    int clearCalls() {
        return clearCalls;
    }

    @Override
    public boolean hasTracker(PlayerRef subject) {
        return tracked.contains(subject.id());
    }

    @Override
    public Optional<WeatherId> unoverriddenWeather(PlayerRef subject) {
        return Optional.ofNullable(unoverridden.get(subject.id()));
    }

    @Override
    public WeatherResolution resolve(WeatherId weather) {
        return resolutions.getOrDefault(weather, WeatherResolution.UNKNOWN);
    }

    @Override
    public boolean applyOverride(PlayerRef subject, WeatherId weather) {
        applyCalls++;
        if (failNextApply) {
            failNextApply = false;
            return false;
        }
        applied.put(subject.id(), weather);
        return true;
    }

    @Override
    public boolean clearOverride(PlayerRef subject) {
        clearCalls++;
        applied.remove(subject.id());
        return true;
    }

    @Override
    public void onTick(Object worldStore) {
        // no-op: this fake needs no store
    }
}

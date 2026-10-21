package io.github.legendaryforge.legendary.harness.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Harness-owned {@link WeatherBackend} that records the calls a consumer causes.
 *
 * <p>Separate from core's {@code FakeWeatherBackend} on purpose: test sources are not shared
 * between modules here, and this module already owns its doubles ({@code
 * RecordingStormseekerHostRuntime}). A consumer-side test should also be free to assert on call
 * ORDER, which this records and core's fake does not.
 */
public final class RecordingWeatherBackend implements WeatherBackend {

    private final List<String> calls = new ArrayList<>();
    private final Map<String, WeatherId> applied = new HashMap<>();
    private final Map<String, WeatherId> environment = new HashMap<>();

    public void setEnvironmentWeather(String playerId, WeatherId weather) {
        environment.put(playerId, weather);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public boolean hasTracker(PlayerRef subject) {
        calls.add("hasTracker:" + subject.id());
        return true;
    }

    @Override
    public Optional<WeatherId> unoverriddenWeather(PlayerRef subject) {
        calls.add("unoverriddenWeather:" + subject.id());
        return Optional.ofNullable(environment.get(subject.id()));
    }

    @Override
    public WeatherResolution resolve(WeatherId weather) {
        calls.add("resolve:" + weather.value());
        return WeatherResolution.OK;
    }

    @Override
    public boolean applyOverride(PlayerRef subject, WeatherId weather) {
        calls.add("applyOverride:" + subject.id() + ":" + weather.value());
        applied.put(subject.id(), weather);
        return true;
    }

    @Override
    public boolean clearOverride(PlayerRef subject) {
        calls.add("clearOverride:" + subject.id());
        applied.remove(subject.id());
        return true;
    }

    @Override
    public void onTick(Object worldStore) {
        calls.add("onTick");
    }
}

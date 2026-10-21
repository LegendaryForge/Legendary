package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who holds a per-player override, and what they claimed.
 *
 * <p>Shared by {@link DefaultWeatherGate} and {@link DefaultPlayerWeatherController}, and that
 * sharing is <em>forced rather than chosen</em>: Hytale's {@code WeatherTracker} has no override
 * getter, so the only way to answer "what weather is this player actually seeing" is to remember
 * what we set. The controller is the only writer.
 *
 * <p>Consequence worth stating plainly: if the gate and the controller are ever constructed with
 * <em>different</em> registry instances, nothing fails — {@code effective()} simply stops seeing
 * claims and quietly reports the world's weather instead.
 */
public final class OverrideRegistry {

    private record Entry(OverrideOwner owner, WeatherId weather) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Optional<OverrideOwner> owner(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return Optional.ofNullable(entries.get(subject.id())).map(Entry::owner);
    }

    public Optional<WeatherId> claimedWeather(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return Optional.ofNullable(entries.get(subject.id())).map(Entry::weather);
    }

    public void put(PlayerRef subject, OverrideOwner owner, WeatherId weather) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(weather, "weather");
        entries.put(subject.id(), new Entry(owner, weather));
    }

    public void remove(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        entries.remove(subject.id());
    }
}

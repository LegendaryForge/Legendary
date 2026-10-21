package io.github.legendaryforge.legendary.core.api.weather;

import java.util.Locale;

/**
 * Storm classification over a {@link WeatherId}.
 *
 * <p>These predicates were string-{@code contains} heuristics inside {@code mod/hytale}, where
 * nothing can test them — that module compiles nothing on CI. They are moved here unchanged in
 * behaviour and tested in both directions.
 */
public final class WeatherKinds {

    private WeatherKinds() {}

    public static boolean isStorm(WeatherId weather) {
        return lower(weather).contains("storm");
    }

    public static boolean isThunderStorm(WeatherId weather) {
        String value = lower(weather);
        return value.contains("thunder_storm") || value.contains("thunderstorm");
    }

    private static String lower(WeatherId weather) {
        return weather == null ? "" : weather.value().toLowerCase(Locale.ROOT);
    }
}

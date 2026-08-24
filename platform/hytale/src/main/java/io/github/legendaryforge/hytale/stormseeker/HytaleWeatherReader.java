package io.github.legendaryforge.hytale.stormseeker;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Reads weather state from Hytale's weather system for Stormseeker integration.
 *
 * <p>Weather index maps to a weather config asset with a string ID (e.g., "clear", "rain", "storm").
 * We consider any weather whose ID contains "storm" or "thunder" to be a storm.
 */
public final class HytaleWeatherReader {

    private HytaleWeatherReader() {}

    /**
     * Returns the weather ID string for the player's current weather, or "unknown" if unavailable.
     */
    public static String getWeatherId(PlayerRef playerRef) {
        try {
            WeatherTracker tracker = playerRef.getComponent(WeatherTracker.getComponentType());
            if (tracker == null) {
                return "unknown";
            }
            int index = tracker.getWeatherIndex();
            var assetMap = Weather.getAssetMap();
            if (assetMap == null) {
                return "index:" + index;
            }
            Weather weather = assetMap.getAsset(index);
            if (weather == null) {
                return "index:" + index;
            }
            return weather.getId();
        } catch (Exception e) {
            return "error";
        }
    }

    /**
     * Returns the raw weather index for the player, or -1 if unavailable.
     */
    public static int getWeatherIndex(PlayerRef playerRef) {
        try {
            WeatherTracker tracker = playerRef.getComponent(WeatherTracker.getComponentType());
            if (tracker == null) {
                return -1;
            }
            return tracker.getWeatherIndex();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns true if the player is currently experiencing a storm-like weather state.
     * Detection is based on the weather ID containing storm-related keywords.
     */
    public static boolean isStorm(PlayerRef playerRef) {
        String id = getWeatherId(playerRef).toLowerCase();
        return id.contains("storm");
    }

    /**
     * Returns true if the player is experiencing a thunder/electrical storm.
     * This is the specific storm type relevant to Stormseeker's elemental identity.
     */
    public static boolean isThunderStorm(PlayerRef playerRef) {
        String id = getWeatherId(playerRef).toLowerCase();
        return id.contains("thunder_storm") || id.contains("thunderstorm");
    }
}

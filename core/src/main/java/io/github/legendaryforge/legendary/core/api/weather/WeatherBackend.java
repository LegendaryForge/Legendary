package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import java.util.Optional;

/**
 * Host-implemented SPI for one engine's weather system. The only interface {@code mod/hytale}
 * implements for this port.
 *
 * <p><b>No method here mentions an index.</b> {@link #resolve} is what lets {@code core} reject the
 * index-0 sentinel while the ordinal stays inside the adapter.
 *
 * <p><b>There is deliberately no {@code effectiveWeather}.</b> It cannot be implemented: Hytale's
 * {@code WeatherTracker} has no override getter, and the only readable per-player value is
 * {@code getWeatherIndex()}, a send-log that returns the last value sent to the client rather than
 * the player's current weather. {@code WeatherGate.effective()} is therefore computed in
 * {@code core} from the port's own claim registry.
 *
 * <p><b>Reads must not use the engine's last-sent value.</b> Measured 2026-10-20: a read
 * immediately after a claim returns the pre-claim value. Implementations resolve through the
 * engine's unoverridden accessor instead.
 *
 * <p><b>This interface is internal to the port.</b> Hytale's
 * {@code setOverrideWeatherIndex}/{@code clearOverrideWeatherIndex} are public and perform no
 * ownership check at all — measured: an unclaimed caller set an override and a non-owner cleared
 * one. {@link #applyOverride} and {@link #clearOverride} are their unchecked equivalents and are
 * never exposed on {@link PlayerWeatherController}.
 */
public interface WeatherBackend {

    boolean hasTracker(PlayerRef subject);

    /** Weather the subject would experience with no override applied. */
    Optional<WeatherId> unoverriddenWeather(PlayerRef subject);

    WeatherResolution resolve(WeatherId weather);

    /** @return false if the engine refused or the subject vanished between check and call */
    boolean applyOverride(PlayerRef subject, WeatherId weather);

    boolean clearOverride(PlayerRef subject);

    /**
     * Hand the adapter the per-world store it needs, once per tick.
     *
     * <p>{@code Object} because {@code core} cannot name {@code Store<EntityStore>} —
     * {@code checkNoPlatformImports} fails the build on it. The adapter casts. This is the single
     * place the port pays for the module boundary.
     *
     * <p>Required because Hytale's unoverridden resolution needs a store, and neither
     * {@code PluginBase} nor {@code JavaPlugin} exposes a route to one — only
     * {@code TickingSystem.tick} does. Measured 2026-10-20: the store and its weather resource are
     * the same instances across invocations, so caching them is safe within a boot.
     *
     * <p>Backends with no such requirement implement this as a no-op.
     */
    void onTick(Object worldStore);
}

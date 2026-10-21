package io.github.legendaryforge.legendary.mod.hytale.weather;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only platform code in this port. Supersedes {@code HytaleWeatherReader}.
 *
 * <p>Deliberately NOT in the {@code stormseeker} package, where its predecessor lived: reading
 * weather is generic, and a second questline must not inherit a dependency on a package named for
 * the first.
 *
 * <p><b>Never calls {@code getWeatherIndex()}.</b> That is a send-log — it returns the last value
 * actually sent to the client, so a read immediately after a claim returns the pre-claim value
 * (measured 2026-10-20). The predecessor read it in all three of its accessors, so every caller was
 * reading a stale field.
 *
 * <p><b>Cannot answer "what weather is this player seeing".</b> {@link WeatherTracker} has no
 * override getter, so {@code WeatherGate.effective()} is computed in {@code core} from the port's
 * own registry. This class answers only the unoverridden reading.
 *
 * <p><b>Needs a store, which only a tick can supply.</b> {@code PluginBase} and {@code JavaPlugin}
 * expose no route to a {@code Store<EntityStore>}; {@code TickingSystem.tick} does. {@link #onTick}
 * caches it. Measured by {@code /weatherportprobe}: the store and its {@link WeatherResource} are
 * the same instances across invocations, so caching is safe within a boot. Not verified across a
 * world unload — see the port spec's open question 4.
 *
 * <p>{@code getComponentConcurrent}, not {@code getComponent}: 0.7.2 deprecated the latter, whose
 * bytecode reads the {@code holder} field twice, so a concurrent null between check and use is an
 * NPE. Do not "simplify" this back.
 */
public final class HytaleWeatherBackend implements WeatherBackend {

    /**
     * Engine refs by player id. Its own map rather than borrowing {@code HytaleStormseekerHost}'s,
     * which is questline-scoped state. Driven from the plugin's connect/disconnect handlers — the
     * same hook that calls {@code forget}, so the two lifecycles cannot drift apart.
     */
    private final Map<String, com.hypixel.hytale.server.core.universe.PlayerRef> refs = new ConcurrentHashMap<>();

    private volatile Store<EntityStore> store;

    public void track(String playerId, com.hypixel.hytale.server.core.universe.PlayerRef ref) {
        refs.put(playerId, ref);
    }

    public void untrack(String playerId) {
        refs.remove(playerId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onTick(Object worldStore) {
        this.store = (Store<EntityStore>) worldStore;
    }

    /**
     * Raw engine index, for debug output only — not on the SPI, because {@code core} must never
     * hold an asset-map ordinal. Returns -1 when unavailable.
     */
    public int rawIndex(String playerId) {
        return unoverriddenIndex(playerId);
    }

    @Override
    public boolean hasTracker(io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject) {
        return store != null && trackerFor(subject.id()) != null;
    }

    @Override
    public Optional<WeatherId> unoverriddenWeather(
            io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject) {
        int index = unoverriddenIndex(subject.id());
        return index < 0 ? Optional.empty() : weatherIdOf(index);
    }

    @Override
    public WeatherResolution resolve(WeatherId weather) {
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return WeatherResolution.UNKNOWN;
        }
        // Measured: a missing key returns Integer.MIN_VALUE, not 0 -- which is the only reason
        // UNKNOWN and the index-0 sentinel are distinguishable here.
        int index = map.getIndex(weather.value());
        if (index < 0) {
            return WeatherResolution.UNKNOWN;
        }
        return index == 0 ? WeatherResolution.SENTINEL : WeatherResolution.OK;
    }

    @Override
    public boolean applyOverride(
            io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject, WeatherId weather) {
        WeatherTracker tracker = trackerFor(subject.id());
        Ref<EntityStore> claimant = claimantFor(subject.id());
        if (tracker == null || claimant == null) {
            return false;
        }
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return false;
        }
        int index = map.getIndex(weather.value());
        if (index <= 0) {
            return false;
        }
        return tracker.tryClaimOverride(claimant, index);
    }

    @Override
    public boolean clearOverride(io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject) {
        WeatherTracker tracker = trackerFor(subject.id());
        Ref<EntityStore> claimant = claimantFor(subject.id());
        if (tracker == null || claimant == null) {
            return false;
        }
        return tracker.releaseOverride(claimant);
    }

    /** -1 when unavailable. Resolves; never reads the send-log. */
    private int unoverriddenIndex(String playerId) {
        Store<EntityStore> current = store;
        WeatherTracker tracker = trackerFor(playerId);
        if (current == null || tracker == null) {
            return -1;
        }
        WeatherResource resource = current.getResource(WeatherResource.getResourceType());
        Ref<EntityStore> self = claimantFor(playerId);
        if (resource == null || self == null) {
            return -1;
        }
        // Nullable by design: resolveUnoverriddenWeatherIndex tolerates a null transform and
        // reuses the last known environment, which /weatheroverrideprobe relies on too.
        TransformComponent transform = current.getComponent(self, TransformComponent.getComponentType());
        return tracker.resolveUnoverriddenWeatherIndex(resource, transform, current);
    }

    private WeatherTracker trackerFor(String playerId) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = refs.get(playerId);
        if (ref == null) {
            return null;
        }
        try {
            return ref.getComponentConcurrent(WeatherTracker.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ownership is reference identity on the engine side, and {@code getReference()} returns a
     * cached field (measured), so re-deriving here and on release yields the same instance the
     * engine compares.
     */
    private Ref<EntityStore> claimantFor(String playerId) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = refs.get(playerId);
        return ref == null ? null : ref.getReference();
    }

    private static Optional<WeatherId> weatherIdOf(int index) {
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return Optional.empty();
        }
        Weather weather = map.getAsset(index);
        return weather == null ? Optional.empty() : Optional.of(new WeatherId(weather.getId()));
    }
}

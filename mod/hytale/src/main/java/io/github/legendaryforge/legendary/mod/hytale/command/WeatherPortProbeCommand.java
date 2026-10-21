package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Debug command: {@code /weatherportprobe}
 *
 * <p>Probes the three mechanisms the rank-1 weather port design depends on and could not establish
 * by signature inspection. Written after three guessed signatures in that design were all wrong:
 * {@code getEnvironmentIndex()} does not exist, {@code getEffectiveWeatherIndex} is not on
 * {@link WeatherTracker} at all, and {@code resolveUnoverriddenWeatherIndex} takes three arguments
 * rather than an int. Standing caution: signature inspection is a hypothesis; a probe executed on
 * the current build is confirmation.
 *
 * <h2>Question 1 — can the asset map distinguish an unknown id from the index-0 sentinel?</h2>
 *
 * <p>{@code WeatherBackend.resolve(WeatherId)} has to return {@code UNKNOWN} for an id the engine
 * does not have and {@code SENTINEL} for one that resolves to index 0, because
 * {@code tryClaimOverride(ref, 0)} returns <b>true and does nothing</b>. If
 * {@link IndexedLookupTableAssetMap#getIndex} returns {@code 0} rather than a negative value for a
 * missing key, those two cases are indistinguishable and {@code resolve} cannot be written as
 * designed.
 *
 * <h2>Question 2 — is the per-player override readable at all?</h2>
 *
 * <p>{@link WeatherTracker} exposes {@code setOverrideWeatherIndex} and
 * {@code clearOverrideWeatherIndex} but <b>no getter</b>, and the only readable per-player value is
 * {@code getWeatherIndex()}, which §5.1 measured to be a send-log. If that holds, the port's
 * {@code WeatherGate.effective()} cannot be served by the adapter and must be computed from the
 * port's own claim registry — which makes the read side depend on the write side's bookkeeping, a
 * structural change to the design rather than a detail.
 *
 * <h2>Question 3 — can a {@code Store} captured in a tick be used later?</h2>
 *
 * <p>{@code resolveUnoverriddenWeatherIndex} needs a {@link WeatherResource}, a
 * {@link TransformComponent} and a {@code ComponentAccessor<EntityStore>} — all reachable only from
 * a {@code Store<EntityStore>}. {@code PluginBase} and {@code JavaPlugin} expose no route to one;
 * {@code TickingSystem.tick(float, int, Store)} does, and so does this command. So the adapter must
 * be fed a store from a tick and cache it. This probe caches on first run and reads through the
 * cached reference on the second, which is the only way to find out whether that is safe.
 *
 * <p><b>Run it twice.</b> The first run reports {@code PENDING} for question 3 by construction and
 * says so; a single run is not a result.
 *
 * <h2>Instrument validation</h2>
 *
 * <p>Question 2's conclusion rests on a non-change — the unoverridden index staying put while an
 * override is active. Per the convention adopted 2026-10-20, a probe whose verdict rests on a delta
 * must prove its own instrument moves: test 2d forces a world weather change and asserts the
 * unoverridden reading follows it. Without that, "did not change under override" is equally
 * consistent with "this reading never changes".
 */
public class WeatherPortProbeCommand extends AbstractWorldCommand {

    /** Captured on the first run; read through on the second. Static so it survives invocations. */
    private static Store<EntityStore> cachedStore;

    private static WeatherResource cachedResource;

    private int failed;
    private int skipped;

    public WeatherPortProbeCommand() {
        super("weatherportprobe", "Probes the mechanisms the rank-1 weather port design needs");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        failed = 0;
        skipped = 0;

        StringBuilder sb = new StringBuilder();
        sb.append("--- weather port mechanism probe (0.7.2) ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        WeatherResource resource = store.getResource(WeatherResource.getResourceType());
        if (resource == null) {
            sb.append("WeatherResource: ABSENT -- nothing to probe\n");
            sb.append("WEATHER_PORT_VERDICT: SKIPPED | WeatherResource absent\n");
            sendText(context, sb.toString());
            return;
        }

        probeAssetMap(sb);
        probeStoreCaching(sb, store, resource);
        probeOverrideReadability(sb, world, store, resource);

        sb.append("\nWEATHER_PORT_VERDICT: ");
        if (failed > 0) {
            sb.append("FAIL");
        } else if (skipped > 0) {
            sb.append("PARTIAL");
        } else {
            sb.append("PASS");
        }
        sb.append(" | ").append(skipped).append(" skipped, ").append(failed).append(" failed\n");
        sendText(context, sb.toString());
    }

    // --- 1. asset map: UNKNOWN must be distinguishable from SENTINEL --------------------

    private void probeAssetMap(StringBuilder sb) {
        sb.append("\n[1] asset map -- can resolve() tell UNKNOWN from SENTINEL?\n");

        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            sb.append("  SKIP  1. Weather.getAssetMap() is null\n");
            skipped++;
            return;
        }

        Weather atZero = map.getAsset(0);
        sb.append("  INFO  1a. getAsset(0) = ")
                .append(atZero == null ? "null" : atZero.getId())
                .append(" -- index 0 is the 'no override' sentinel; this is what occupies it\n");

        int missing = map.getIndex("Definitely_Not_A_Weather_Id_zzz");
        boolean distinguishable = missing < 0;
        check(
                sb,
                "1b. getIndex(unknown key) returns a NEGATIVE index",
                distinguishable,
                "returned " + missing
                        + (distinguishable
                                ? " -- so UNKNOWN and SENTINEL are distinguishable and resolve() works as designed"
                                : " -- UNKNOWN is INDISTINGUISHABLE from the index-0 sentinel; resolve()"
                                        + " cannot be written as designed and the SPI needs a different shape"));

        if (atZero != null) {
            int roundTrip = map.getIndex(atZero.getId());
            check(
                    sb,
                    "1c. getIndex(getAsset(0).getId()) round-trips to 0",
                    roundTrip == 0,
                    "returned " + roundTrip + ", expected 0 -- confirms getIndex is the inverse of getAsset");
        } else {
            sb.append("  SKIP  1c. nothing at index 0 to round-trip\n");
            skipped++;
        }
    }

    // --- 2. can a Store captured earlier still be used? --------------------------------

    // Reference equality is the POINT here, not an oversight: the question is whether the
    // adapter may hold the same Store instance between ticks. Rewriting these as .equals()
    // would silently change what is measured.
    @SuppressWarnings("ReferenceEquality")
    private void probeStoreCaching(StringBuilder sb, Store<EntityStore> store, WeatherResource resource) {
        sb.append("\n[2] store caching -- may the adapter hold a Store between ticks?\n");

        if (cachedStore == null) {
            cachedStore = store;
            cachedResource = resource;
            sb.append("  PEND  2. first run -- store and resource cached, nothing read through them yet.\n");
            sb.append("        RUN THIS COMMAND AGAIN. A single run is not a result for this question.\n");
            skipped++;
            return;
        }

        check(
                sb,
                "2a. the cached Store is the same instance as this invocation's",
                cachedStore == store,
                cachedStore == store
                        ? "same instance -- one store per world, stable across invocations, so the adapter"
                                + " may cache it"
                        : "DIFFERENT instance -- the store is per-invocation or per-tick, so the adapter must"
                                + " NOT cache it and reads have to happen inside the tick");

        check(
                sb,
                "2b. the cached Store still answers getResource()",
                cachedStore.getResource(WeatherResource.getResourceType()) != null,
                "a cached store that cannot resolve a resource is unusable to the adapter");

        check(
                sb,
                "2c. the cached WeatherResource is the same instance as this invocation's",
                cachedResource == resource,
                cachedResource == resource
                        ? "same instance -- the resource may be cached alongside the store"
                        : "DIFFERENT instance -- resolve the resource per read, not once");
    }

    // --- 3. override readability, and whether unoverridden is a live reading ------------

    private void probeOverrideReadability(
            StringBuilder sb, World world, Store<EntityStore> store, WeatherResource resource) {
        sb.append("\n[3] override readability -- can the adapter serve WeatherGate.effective()?\n");

        PlayerRef player = firstPlayer(world);
        if (player == null) {
            sb.append("  SKIP  3. no connected player -- the override is per-player state and needs one.\n");
            sb.append("        This is no run, not a degraded one.\n");
            skipped++;
            return;
        }

        WeatherTracker tracker = player.getComponentConcurrent(WeatherTracker.getComponentType());
        if (tracker == null) {
            sb.append("  SKIP  3. player has no WeatherTracker\n");
            skipped++;
            return;
        }

        Ref<EntityStore> self = player.getReference();
        if (self == null) {
            sb.append("  SKIP  3. getReference() null -- cannot claim an override for this player\n");
            skipped++;
            return;
        }

        TransformComponent transform = store.getComponent(self, TransformComponent.getComponentType());
        int env = tracker.getEnvironmentId();

        int unoverriddenBefore = tracker.resolveUnoverriddenWeatherIndex(resource, transform, store);
        int sendLogBefore = tracker.getWeatherIndex();
        int worldEffective = resource.getEffectiveWeatherIndex(env);

        sb.append("  INFO  3a. environmentId=")
                .append(env)
                .append("  unoverridden=")
                .append(unoverriddenBefore)
                .append("  sendLog=")
                .append(sendLogBefore)
                .append("  resource.effective=")
                .append(worldEffective)
                .append("  forced=")
                .append(resource.getForcedWeatherIndex())
                .append('\n');

        int target = pickClaimTarget(unoverriddenBefore);
        if (target < 0) {
            sb.append("  SKIP  3. could not find a second usable weather index to claim\n");
            skipped++;
            return;
        }

        boolean claimed = tracker.tryClaimOverride(self, target);
        check(sb, "3b. claimed an override to index " + target, claimed, "returned " + claimed);

        int unoverriddenAfter = tracker.resolveUnoverriddenWeatherIndex(resource, transform, store);
        int sendLogAfter = tracker.getWeatherIndex();

        check(
                sb,
                "3c. resolveUnoverriddenWeatherIndex is UNAFFECTED by the override",
                unoverriddenAfter == unoverriddenBefore,
                "before=" + unoverriddenBefore + " after=" + unoverriddenAfter
                        + " -- this is the reading WeatherGate.unoverridden() uses; it must ignore overrides");

        check(
                sb,
                "3d. no public read path returns the claimed index",
                sendLogAfter != target && unoverriddenAfter != target,
                "sendLog=" + sendLogAfter + " unoverridden=" + unoverriddenAfter + " claimed=" + target
                        + (sendLogAfter != target && unoverriddenAfter != target
                                ? " -- CONFIRMED: the override is write-only. WeatherGate.effective() must be"
                                        + " computed from the port's own claim registry, not read from the engine"
                                : " -- a public read DOES return the claimed index; effective() can be served"
                                        + " by the adapter after all and the SPI should keep effectiveWeather()"));

        // Instrument check. 3c's verdict is a non-change, which is worthless unless this reading
        // demonstrably moves when the thing it reports actually changes.
        int forcedBefore = resource.getForcedWeatherIndex();
        Weather other = otherWeatherThan(unoverriddenBefore);
        if (other == null) {
            sb.append("  SKIP  3e. INSTRUMENT CHECK -- no alternative weather asset to force\n");
            sb.append("        3c therefore reports a non-change on an unvalidated instrument.\n");
            skipped++;
        } else {
            resource.setForcedWeather(other.getId());
            int unoverriddenForced = tracker.resolveUnoverriddenWeatherIndex(resource, transform, store);
            check(
                    sb,
                    "3e. INSTRUMENT CHECK -- the unoverridden reading moves when world weather is forced",
                    unoverriddenForced != unoverriddenBefore,
                    "before=" + unoverriddenBefore + " forced-to=" + other.getId() + " now=" + unoverriddenForced
                            + (unoverriddenForced != unoverriddenBefore
                                    ? " -- so 3c's non-change is a real absence of effect"
                                    : " -- the reading did NOT move, so 3c measures nothing and must be"
                                            + " discarded rather than believed"));
            // Restore. setForcedWeather(null) is how the engine clears it; if the forced index was
            // already set when we arrived, leave that alone rather than guessing its id back.
            if (forcedBefore == 0) {
                resource.setForcedWeather(null);
            }
        }

        tracker.releaseOverride(self);
        sb.append("  INFO  3f. override released; player restored to the world's own weather\n");
    }

    // --- helpers -----------------------------------------------------------------------

    private static PlayerRef firstPlayer(World world) {
        for (PlayerRef candidate : world.getPlayerRefs()) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /** An index that is usable as an override: non-zero, resolvable, and not what they already see. */
    private static int pickClaimTarget(int avoid) {
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return -1;
        }
        for (int i = 1; i < map.getNextIndex(); i++) {
            if (i != avoid && map.getAsset(i) != null) {
                return i;
            }
        }
        return -1;
    }

    private static Weather otherWeatherThan(int avoid) {
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return null;
        }
        for (int i = 1; i < map.getNextIndex(); i++) {
            Weather candidate = map.getAsset(i);
            if (i != avoid && candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private void check(StringBuilder sb, String label, boolean ok, String detail) {
        sb.append(ok ? "  PASS  " : "  FAIL  ").append(label).append('\n');
        sb.append("        ").append(detail).append('\n');
        if (!ok) {
            failed++;
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

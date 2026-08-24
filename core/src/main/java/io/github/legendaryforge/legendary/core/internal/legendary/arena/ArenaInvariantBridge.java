package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Internal bridge that forwards encounter lifecycle signals to arena invariants.
 *
 * <p>Key behavior: cleanup routing is deterministic without expanding public events.
 * Cleanup events only include instanceId, so we remember instanceId -> definitionId from start.
 */
public final class ArenaInvariantBridge {

    private static boolean always(UUID id) {
        return true;
    }

    private static void noop(UUID id) {}

    private final ArenaInvariantRegistry registry;
    private final Predicate<UUID> applyFilter;
    private final Consumer<UUID> onCleanupPost;

    // Cleanup event only carries instanceId; this mapping routes cleanup to invariants.
    private final ConcurrentHashMap<UUID, ResourceId> instanceToDefinition = new ConcurrentHashMap<>();

    public ArenaInvariantBridge(ArenaInvariantRegistry registry) {
        this(registry, ArenaInvariantBridge::always, ArenaInvariantBridge::noop);
    }

    public ArenaInvariantBridge(
            ArenaInvariantRegistry registry, Predicate<UUID> applyFilter, Consumer<UUID> onCleanupPost) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.applyFilter = Objects.requireNonNull(applyFilter, "applyFilter");
        this.onCleanupPost = Objects.requireNonNull(onCleanupPost, "onCleanupPost");
    }

    public void onStarted(EncounterStartedEvent event) {
        Objects.requireNonNull(event, "event");
        if (!applyFilter.test(event.instanceId())) {
            return;
        }
        instanceToDefinition.put(event.instanceId(), event.definitionId());
        for (ArenaInvariant inv : registry.invariantsFor(event.definitionId())) {
            inv.onStart(event.instanceId());
        }
    }

    public void onEnded(EncounterEndedEvent event) {
        Objects.requireNonNull(event, "event");
        if (!applyFilter.test(event.instanceId())) {
            return;
        }
        for (ArenaInvariant inv : registry.invariantsFor(event.definitionId())) {
            inv.onEnd(event.instanceId());
        }
    }

    public void onCleanup(EncounterCleanupEvent event) {
        Objects.requireNonNull(event, "event");
        UUID instanceId = event.instanceId();
        ResourceId defId = instanceToDefinition.remove(instanceId);
        if (defId == null) {
            onCleanupPost.accept(instanceId);
            return;
        }
        if (applyFilter.test(instanceId)) {
            for (ArenaInvariant inv : registry.invariantsFor(defId)) {
                inv.onCleanup(instanceId);
            }
        }
        onCleanupPost.accept(instanceId);
    }

    public static List<Subscription> bind(EventBus bus, ArenaInvariantRegistry registry) {
        return bind(bus, registry, ArenaInvariantBridge::always, ArenaInvariantBridge::noop);
    }

    public static List<Subscription> bind(
            EventBus bus, ArenaInvariantRegistry registry, Predicate<UUID> applyFilter, Consumer<UUID> onCleanupPost) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(registry, "registry");

        ArenaInvariantBridge bridge = new ArenaInvariantBridge(registry, applyFilter, onCleanupPost);
        List<Subscription> subs = new ArrayList<>(3);

        subs.add(bus.subscribe(EncounterStartedEvent.class, bridge::onStarted));
        subs.add(bus.subscribe(EncounterEndedEvent.class, bridge::onEnded));
        subs.add(bus.subscribe(EncounterCleanupEvent.class, bridge::onCleanup));

        return subs;
    }
}

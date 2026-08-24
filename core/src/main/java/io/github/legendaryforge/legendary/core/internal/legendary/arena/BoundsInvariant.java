package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ArenaBoundsViolatedEvent;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal invariant that reacts to arena bounds violations.
 *
 * <p>Signal-only: emits internal events indicating participation should be revoked.
 */
public final class BoundsInvariant implements ArenaInvariant {

    private static final ResourceId OUT_OF_BOUNDS_REASON = new ResourceId("legendary", "out_of_bounds");

    private final EventBus bus;

    // Tracks whether the instance is currently ACTIVE.
    private final Set<UUID> activeInstances = ConcurrentHashMap.newKeySet();

    // Dedup per (instanceId, playerId) so we emit revoke once.
    private final Map<UUID, Set<UUID>> revoked = new ConcurrentHashMap<>();

    public BoundsInvariant(EventBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
        bus.subscribe(ArenaBoundsViolatedEvent.class, this::onBoundsViolated);
    }

    private void onBoundsViolated(ArenaBoundsViolatedEvent event) {
        Objects.requireNonNull(event, "event");
        UUID instanceId = event.instanceId();
        if (!activeInstances.contains(instanceId)) {
            return;
        }
        Set<UUID> revokedPlayers = revoked.computeIfAbsent(instanceId, id -> ConcurrentHashMap.newKeySet());
        if (revokedPlayers.add(event.playerId())) {
            bus.post(new ParticipationRevokedEvent(instanceId, event.playerId(), OUT_OF_BOUNDS_REASON));
        }
    }

    @Override
    public void onStart(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        activeInstances.add(instanceId);
    }

    @Override
    public void onEnd(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        activeInstances.remove(instanceId);
    }

    @Override
    public void onCleanup(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        activeInstances.remove(instanceId);
        revoked.remove(instanceId);
    }
}

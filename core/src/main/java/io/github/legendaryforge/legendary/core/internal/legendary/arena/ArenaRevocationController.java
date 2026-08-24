package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Internal, signal-driven entrypoint for revoking arena participation.
 *
 * <p>This mutates only core revocation state and emits an event; platform adapters decide
 * how to react (kick, UI feedback, moderation, etc.).
 */
public final class ArenaRevocationController {

    private final ArenaRevocationTracker tracker;
    private final EventBus bus;
    private final Predicate<UUID> isLegendaryInstance;

    public ArenaRevocationController(
            ArenaRevocationTracker tracker, EventBus bus, Predicate<UUID> isLegendaryInstance) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.isLegendaryInstance = Objects.requireNonNull(isLegendaryInstance, "isLegendaryInstance");
    }

    public void revoke(UUID instanceId, UUID playerId, ResourceId reasonId) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(reasonId, "reasonId");

        if (!isLegendaryInstance.test(instanceId)) {
            return;
        }

        tracker.revoke(instanceId, playerId);
        bus.post(new ParticipationRevokedEvent(instanceId, playerId, reasonId));
    }
}

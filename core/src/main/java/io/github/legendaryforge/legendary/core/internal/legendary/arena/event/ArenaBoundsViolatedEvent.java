package io.github.legendaryforge.legendary.core.internal.legendary.arena.event;

import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.geom.Vec3d;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal signal emitted when a player is detected outside arena bounds for an encounter instance. */
public record ArenaBoundsViolatedEvent(UUID instanceId, UUID playerId, Optional<Vec3d> position) implements Event {
    public ArenaBoundsViolatedEvent {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(position, "position");
    }

    public static ArenaBoundsViolatedEvent withoutPosition(UUID instanceId, UUID playerId) {
        return new ArenaBoundsViolatedEvent(instanceId, playerId, Optional.empty());
    }
}

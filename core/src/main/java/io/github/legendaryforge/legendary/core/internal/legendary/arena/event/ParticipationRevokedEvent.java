package io.github.legendaryforge.legendary.core.internal.legendary.arena.event;

import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

/**
 * Internal signal that a player participation was revoked for a specific arena instance.
 *
 * <p>Platforms may listen to this to apply UI, kicks, moderation actions, etc.
 */
public record ParticipationRevokedEvent(UUID instanceId, UUID playerId, ResourceId reasonId) implements Event {}

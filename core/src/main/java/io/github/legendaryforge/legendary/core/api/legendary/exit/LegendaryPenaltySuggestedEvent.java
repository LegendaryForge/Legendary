package io.github.legendaryforge.legendary.core.api.legendary.exit;

import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

/**
 * Advisory event indicating a Legendary penalty is suggested for a player.
 *
 * <p>Platform adapters decide how (or whether) to implement the penalty (debuff, lockout, etc.).
 */
public record LegendaryPenaltySuggestedEvent(
        UUID instanceId, UUID playerId, ResourceId reasonId, LegendaryPenaltySuggestion suggestion) implements Event {}

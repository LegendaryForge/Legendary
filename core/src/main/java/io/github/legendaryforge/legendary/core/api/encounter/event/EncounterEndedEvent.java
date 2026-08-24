package io.github.legendaryforge.legendary.core.api.encounter.event;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

/**
 * Fired when an encounter instance is ended.
 */
public record EncounterEndedEvent(
        EncounterKey key, UUID instanceId, ResourceId definitionId, EncounterAnchor anchor, EndReason reason)
        implements Event {}

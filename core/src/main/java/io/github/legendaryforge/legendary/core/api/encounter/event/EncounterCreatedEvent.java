package io.github.legendaryforge.legendary.core.api.encounter.event;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

/**
 * Fired when a new encounter instance is created.
 */
public record EncounterCreatedEvent(EncounterKey key, UUID instanceId, ResourceId definitionId, EncounterAnchor anchor)
        implements Event {}

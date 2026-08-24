package io.github.legendaryforge.legendary.core.api.encounter.event;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

/**
 * Fired when an existing encounter instance is reused for a create request.
 */
public record EncounterReusedEvent(EncounterKey key, UUID instanceId, ResourceId definitionId, EncounterAnchor anchor)
        implements Event {}

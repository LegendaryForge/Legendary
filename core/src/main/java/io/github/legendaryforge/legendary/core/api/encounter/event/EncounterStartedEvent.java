package io.github.legendaryforge.legendary.core.api.encounter.event;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

public record EncounterStartedEvent(
        EncounterKey key, UUID instanceId, ResourceId definitionId, EncounterAnchor anchor, UUID triggeringPlayerId)
        implements io.github.legendaryforge.legendary.core.api.event.Event {}

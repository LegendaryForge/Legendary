package io.github.legendaryforge.legendary.core.api.encounter;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform-agnostic anchor for an encounter.
 *
 * <p>This identifies the conceptual "place" an encounter occurs, without assuming any particular
 * coordinate system. Platforms may map locations, zones, or objects to a stable anchor id.
 */
public record EncounterAnchor(ResourceId worldId, Optional<ResourceId> anchorId, Optional<UUID> partyId) {

    public EncounterAnchor {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(anchorId, "anchorId");
        Objects.requireNonNull(partyId, "partyId");
    }

    public static EncounterAnchor of(ResourceId worldId) {
        return new EncounterAnchor(worldId, Optional.empty(), Optional.empty());
    }

    public static EncounterAnchor of(ResourceId worldId, ResourceId anchorId) {
        return new EncounterAnchor(worldId, Optional.of(anchorId), Optional.empty());
    }

    public static EncounterAnchor of(ResourceId worldId, ResourceId anchorId, UUID partyId) {
        return new EncounterAnchor(worldId, Optional.of(anchorId), Optional.of(partyId));
    }
}

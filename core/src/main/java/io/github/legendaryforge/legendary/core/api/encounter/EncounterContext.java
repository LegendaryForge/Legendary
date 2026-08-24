package io.github.legendaryforge.legendary.core.api.encounter;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EncounterContext {

    /**
     * Platform-agnostic anchor for the encounter.
     */
    EncounterAnchor anchor();

    /**
     * The party that owns the encounter, if applicable.
     */
    default Optional<UUID> partyId() {
        return anchor().partyId();
    }

    /**
     * World identifier (implementation-defined).
     */
    default ResourceId worldId() {
        return anchor().worldId();
    }

    /**
     * Implementation-defined metadata for platform/mod use. No stable schema is guaranteed by the core API.
     */
    Map<String, Object> metadata();
}

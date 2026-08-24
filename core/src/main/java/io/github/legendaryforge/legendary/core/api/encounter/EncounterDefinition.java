package io.github.legendaryforge.legendary.core.api.encounter;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;

public interface EncounterDefinition {

    ResourceId id();

    String displayName();

    EncounterAccessPolicy accessPolicy();

    SpectatorPolicy spectatorPolicy();

    /**
     * Maximum participants. A value <= 0 means "no explicit limit" (implementation-defined).
     */
    int maxParticipants();

    /**
     * Maximum spectators. A value <= 0 means "no explicit limit" (implementation-defined).
     */
    int maxSpectators();
}

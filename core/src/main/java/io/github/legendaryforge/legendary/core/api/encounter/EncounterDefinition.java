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

    /**
     * Whether creating an instance of this encounter should capture the activating party's
     * membership at start. Default false; richer encounter models override.
     */
    default boolean capturesOwnerParty() {
        return false;
    }
}

package io.github.legendaryforge.legendary.core.api.identity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Platform-provided access to party/group membership information.
 *
 * <p>Parties are optional: an encounter may or may not be owned by a party.</p>
 */
public interface PartyDirectory {

    /**
     * Returns true if the given party id is known to the platform.
     */
    boolean isKnown(UUID partyId);

    /**
     * Returns the current members of a party, if known.
     *
     * <p>This is a snapshot view and may change between calls.</p>
     */
    Optional<Set<UUID>> members(UUID partyId);

    /**
     * Returns true if the player is currently a member of the party.
     *
     * <p>Default implementation uses {@link #members(UUID)} when available.</p>
     */
    default boolean isMember(UUID partyId, UUID playerId) {
        return members(partyId).map(m -> m.contains(playerId)).orElse(false);
    }
}

package io.github.legendaryforge.legendary.core.internal.legendary.instance;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Internal-only view for Legendary encounter ownership metadata.
 *
 * <p>Do not expose from public API. This exists to avoid reflection while keeping Legendary wiring
 * out of the base encounter contracts.</p>
 */
public interface LegendaryEncounterInstanceView {

    Optional<UUID> ownerPartyId();

    Set<UUID> ownerPartyMembersAtStart();
}

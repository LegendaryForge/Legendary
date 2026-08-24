package io.github.legendaryforge.legendary.core.internal.legendary.penalty;

import java.util.UUID;

/**
 * Internal provider for party-scoped Legendary penalty state.
 *
 * <p>Platform adapters may supply an implementation backed by their own debuffs/flags/lockouts.
 */
public interface LegendaryPenaltyStatus {

    boolean anyPartyMemberHasActivePenalty(UUID partyId);
}

package io.github.legendaryforge.legendary.core.internal.legendary.penalty;

import java.util.UUID;

/** Default internal implementation: assumes no active penalties are present. */
public final class NoopLegendaryPenaltyStatus implements LegendaryPenaltyStatus {

    @Override
    public boolean anyPartyMemberHasActivePenalty(UUID partyId) {
        return false;
    }
}

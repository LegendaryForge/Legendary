package io.github.legendaryforge.legendary.core.internal.legendary.start;

import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterId;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal start-gate request for beginning a Legendary Encounter attempt.
 *
 * <p>Platform adapters supply current-party facts (e.g., penalty present) without leaking platform types.
 */
public final class LegendaryStartRequest {

    private final LegendaryEncounterId encounterId;
    private final UUID partyId;
    private final boolean anyPartyMemberHasActivePenalty;

    private LegendaryStartRequest(
            LegendaryEncounterId encounterId, UUID partyId, boolean anyPartyMemberHasActivePenalty) {
        this.encounterId = Objects.requireNonNull(encounterId, "encounterId");
        this.partyId = Objects.requireNonNull(partyId, "partyId");
        this.anyPartyMemberHasActivePenalty = anyPartyMemberHasActivePenalty;
    }

    public static LegendaryStartRequest of(
            LegendaryEncounterId encounterId, UUID partyId, boolean anyPartyMemberHasActivePenalty) {
        return new LegendaryStartRequest(encounterId, partyId, anyPartyMemberHasActivePenalty);
    }

    public LegendaryEncounterId encounterId() {
        return encounterId;
    }

    public UUID partyId() {
        return partyId;
    }

    public boolean anyPartyMemberHasActivePenalty() {
        return anyPartyMemberHasActivePenalty;
    }
}

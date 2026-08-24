package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface EncounterAccessDecisionPolicy {

    JoinDecision decide(
            EncounterInstance encounter,
            EncounterContext context,
            UUID requesterPlayerId,
            Optional<UUID> requesterPartyId);

    static EncounterAccessDecisionPolicy requireNonNull(EncounterAccessDecisionPolicy p) {
        return Objects.requireNonNull(p, "policy");
    }
}

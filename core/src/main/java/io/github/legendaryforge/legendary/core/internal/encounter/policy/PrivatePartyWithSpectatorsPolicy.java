package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterState;
import java.util.Optional;
import java.util.UUID;

public final class PrivatePartyWithSpectatorsPolicy implements EncounterAccessDecisionPolicy {

    @Override
    public JoinDecision decide(
            EncounterInstance encounter,
            EncounterContext context,
            UUID requesterPlayerId,
            Optional<UUID> requesterPartyId) {
        Optional<UUID> ownerParty = context.partyId();

        // Party members join if capacity allows
        if (ownerParty.isPresent()
                && requesterPartyId.isPresent()
                && ownerParty.get().equals(requesterPartyId.get())) {
            if (encounter.participants().size() < encounter.definition().maxParticipants()) {
                return JoinDecision.ALLOW_JOIN;
            } else {
                return JoinDecision.DENY;
            }
        }

        // Non-party: spectate if encounter is RUNNING and spectator capacity allows
        if (encounter.state() == EncounterState.RUNNING) {
            if (encounter.spectators().size() < encounter.definition().maxSpectators()) {
                // Spectator promotion: check if participant slots are free
                if (encounter.participants().size() < encounter.definition().maxParticipants()) {
                    return JoinDecision.ALLOW_JOIN; // Promote from spectator
                } else {
                    return JoinDecision.ALLOW_SPECTATE;
                }
            } else {
                return JoinDecision.DENY;
            }
        }

        // Encounter ended or other case: deny
        return JoinDecision.DENY;
    }
}

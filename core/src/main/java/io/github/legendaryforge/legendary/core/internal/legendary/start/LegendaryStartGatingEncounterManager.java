package io.github.legendaryforge.legendary.core.internal.legendary.start;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import io.github.legendaryforge.legendary.core.internal.legendary.penalty.LegendaryPenaltyStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal wrapper that enforces {@link LegendaryStartPolicy} before creating a Legendary encounter instance.
 */
public final class LegendaryStartGatingEncounterManager implements EncounterManager {

    private final EncounterManager delegate;
    private final LegendaryStartPolicy startPolicy;
    private final LegendaryPenaltyStatus penaltyStatus;

    public LegendaryStartGatingEncounterManager(
            EncounterManager delegate, LegendaryStartPolicy startPolicy, LegendaryPenaltyStatus penaltyStatus) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.startPolicy = Objects.requireNonNull(startPolicy, "startPolicy");
        this.penaltyStatus = Objects.requireNonNull(penaltyStatus, "penaltyStatus");
    }

    @Override
    public EncounterInstance create(EncounterDefinition definition, EncounterContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");

        if (definition instanceof LegendaryEncounterDefinition led) {
            Optional<UUID> partyId = context.partyId();
            if (partyId.isEmpty()) {
                // Legendary encounters are party-owned. Fail closed.
                throw new LegendaryStartDeniedException("party_required");
            }

            boolean anyPenalty = penaltyStatus.anyPartyMemberHasActivePenalty(partyId.get());
            LegendaryStartRequest req = LegendaryStartRequest.of(led.legendaryId(), partyId.get(), anyPenalty);

            LegendaryStartDecision decision = startPolicy.evaluate(req);
            if (!decision.allowed()) {
                throw new LegendaryStartDeniedException(decision.reason().orElse("denied"));
            }
        }

        return delegate.create(definition, context);
    }

    @Override
    public JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role) {
        return delegate.join(playerId, instance, role);
    }

    @Override
    public void leave(UUID playerId, EncounterInstance instance) {
        delegate.leave(playerId, instance);
    }

    @Override
    public void end(EncounterInstance instance, EndReason reason) {
        delegate.end(instance, reason);
    }

    @Override
    public Optional<EncounterInstance> byInstanceId(UUID instanceId) {
        return delegate.byInstanceId(instanceId);
    }

    @Override
    public Optional<EncounterInstance> byKey(EncounterKey key) {
        return delegate.byKey(key);
    }
}

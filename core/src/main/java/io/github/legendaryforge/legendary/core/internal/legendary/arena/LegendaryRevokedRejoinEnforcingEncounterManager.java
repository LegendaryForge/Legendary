package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Internal wrapper that prevents revoked players from rejoining as PARTICIPANT while an arena is ACTIVE.
 *
 * <p>Legendary-only and signal-driven. Platforms enforce behavior; Core gates participation state.
 */
public final class LegendaryRevokedRejoinEnforcingEncounterManager implements EncounterManager {

    private final EncounterManager delegate;
    private final Predicate<UUID> isLegendaryInstance;
    private final PhaseGateInvariant phaseGate;
    private final ArenaRevocationTracker revocations;

    public LegendaryRevokedRejoinEnforcingEncounterManager(
            EncounterManager delegate,
            Predicate<UUID> isLegendaryInstance,
            PhaseGateInvariant phaseGate,
            ArenaRevocationTracker revocations) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.isLegendaryInstance = Objects.requireNonNull(isLegendaryInstance, "isLegendaryInstance");
        this.phaseGate = Objects.requireNonNull(phaseGate, "phaseGate");
        this.revocations = Objects.requireNonNull(revocations, "revocations");
    }

    @Override
    public EncounterInstance create(
            io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition definition,
            io.github.legendaryforge.legendary.core.api.encounter.EncounterContext context) {
        return delegate.create(definition, context);
    }

    @Override
    public JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(role, "role");

        UUID instanceId = instance.instanceId();
        if (role == ParticipationRole.PARTICIPANT
                && isLegendaryInstance.test(instanceId)
                && phaseGate.phaseOf(instanceId).orElse(null) == ArenaPhase.ACTIVE
                && revocations.isRevoked(instanceId, playerId)) {
            return JoinResult.DENIED_STATE;
        }

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

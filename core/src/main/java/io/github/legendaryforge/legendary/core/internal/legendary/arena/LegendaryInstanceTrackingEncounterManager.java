package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Internal decorator that tracks which encounter instance ids are legendary based on runtime create() calls.
 *
 * <p>This provides a deterministic, bounded gating mechanism for internal arena invariants without introducing
 * new public Core APIs or registries.
 */
public final class LegendaryInstanceTrackingEncounterManager implements EncounterManager {

    private final EncounterManager delegate;
    private final Set<UUID> legendaryInstanceIds;

    public LegendaryInstanceTrackingEncounterManager(EncounterManager delegate, Set<UUID> legendaryInstanceIds) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.legendaryInstanceIds = Objects.requireNonNull(legendaryInstanceIds, "legendaryInstanceIds");
    }

    public boolean isLegendary(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return legendaryInstanceIds.contains(instanceId);
    }

    @Override
    public EncounterInstance create(EncounterDefinition definition, EncounterContext context) {
        Objects.requireNonNull(definition, "definition");
        EncounterInstance instance = delegate.create(definition, context);
        if (definition instanceof LegendaryEncounterDefinition) {
            legendaryInstanceIds.add(instance.instanceId());
        }
        return instance;
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

package io.github.legendaryforge.legendary.core.internal.legendary.manager;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.legendary.access.LegendaryAccessDecision;
import io.github.legendaryforge.legendary.core.api.legendary.access.LegendaryAccessLevel;
import io.github.legendaryforge.legendary.core.api.legendary.access.LegendaryAccessPolicy;
import io.github.legendaryforge.legendary.core.api.legendary.access.LegendaryAccessRequest;
import io.github.legendaryforge.legendary.core.api.legendary.access.LegendaryVisibilityMode;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import io.github.legendaryforge.legendary.core.internal.legendary.instance.LegendaryEncounterInstanceView;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal wrapper that enforces {@link LegendaryAccessPolicy} for Legendary encounters.
 *
 * <p>This keeps the base encounter engine free of Legendary-specific policy dependencies while still
 * wiring the public Legendary access contract into runtime behavior.</p>
 */
public final class LegendaryAccessEnforcingEncounterManager implements EncounterManager {

    private final EncounterManager delegate;
    private final LegendaryAccessPolicy accessPolicy;

    public LegendaryAccessEnforcingEncounterManager(EncounterManager delegate, LegendaryAccessPolicy accessPolicy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    @Override
    public EncounterInstance create(
            EncounterDefinition definition,
            io.github.legendaryforge.legendary.core.api.encounter.EncounterContext context) {
        return delegate.create(definition, context);
    }

    @Override
    public JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(role, "role");

        EncounterDefinition def = instance.definition();
        if (def instanceof LegendaryEncounterDefinition led) {
            LegendaryEncounterInstanceView view;
            if (instance instanceof LegendaryEncounterInstanceView v) {
                view = v;
            } else {
                // Fail closed: Legendary encounters must be backed by a core instance that exposes
                // Legendary ownership metadata.
                return JoinResult.DENIED_POLICY;
            }

            Optional<UUID> ownerPartyId = view.ownerPartyId();
            if (ownerPartyId.isEmpty()) {
                // Fail closed: Legendary encounters are party-owned.
                return JoinResult.DENIED_POLICY;
            }

            UUID owner = ownerPartyId.get();
            boolean isOwnerMemberAtStart = view.ownerPartyMembersAtStart().contains(playerId);
            // If a player is not in the start snapshot, they are treated as a late joiner.
            boolean lateJoiner = !isOwnerMemberAtStart;

            LegendaryVisibilityMode visibilityMode = mapVisibility(def.spectatorPolicy());

            LegendaryAccessRequest req =
                    LegendaryAccessRequest.of(led.legendaryId(), playerId, owner, owner, lateJoiner, visibilityMode);

            LegendaryAccessDecision decision = accessPolicy.evaluate(req);
            if (decision.level() == LegendaryAccessLevel.DENY) {
                return JoinResult.DENIED_POLICY;
            }

            ParticipationRole effectiveRole =
                    switch (decision.level()) {
                        case PARTICIPATE -> ParticipationRole.PARTICIPANT;
                        case SPECTATE -> ParticipationRole.SPECTATOR;
                        case DENY -> role; // unreachable due to check above
                    };

            return delegate.join(playerId, instance, effectiveRole);
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
    public Optional<EncounterInstance> byKey(io.github.legendaryforge.legendary.core.api.encounter.EncounterKey key) {
        return delegate.byKey(key);
    }

    private static LegendaryVisibilityMode mapVisibility(SpectatorPolicy spectatorPolicy) {
        return spectatorPolicy == SpectatorPolicy.ALLOW_VIEW_ONLY
                ? LegendaryVisibilityMode.WORLD_VISIBLE
                : LegendaryVisibilityMode.INSTANCE_VISIBLE;
    }
}

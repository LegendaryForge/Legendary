package io.github.legendaryforge.legendary.core.internal.encounter;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterState;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.identity.PlayerDirectory;
import io.github.legendaryforge.legendary.core.internal.encounter.policy.DefaultEncounterJoinPolicy;
import io.github.legendaryforge.legendary.core.internal.encounter.policy.DefaultEncounterReusePolicy;
import io.github.legendaryforge.legendary.core.internal.encounter.policy.EncounterJoinPolicy;
import io.github.legendaryforge.legendary.core.internal.encounter.policy.EncounterReusePolicy;
import io.github.legendaryforge.legendary.core.internal.legendary.instance.LegendaryEncounterInstanceView;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal, platform-agnostic internal reference implementation of {@link EncounterManager}.
 *
 * <p>Policy is injected to keep the core testable and platform-agnostic. This class remains in-memory only.</p>
 */
public final class DefaultEncounterManager implements EncounterManager {

    private final Map<UUID, DefaultEncounterInstance> instances = new ConcurrentHashMap<>();
    private final Map<EncounterKey, DefaultEncounterInstance> instancesByKey = new ConcurrentHashMap<>();

    private final EncounterJoinPolicy joinPolicy;
    private final EncounterReusePolicy reusePolicy;

    private final Optional<PlayerDirectory> players;
    private final Optional<PartyDirectory> parties;
    private final Optional<EventBus> events;

    /**
     * Convenience ctor used by runtime and most tests: (players, parties, events).
     */
    public DefaultEncounterManager(
            Optional<PlayerDirectory> players, Optional<PartyDirectory> parties, Optional<EventBus> events) {
        this(new DefaultEncounterJoinPolicy(), new DefaultEncounterReusePolicy(), players, parties, events);
    }

    /**
     * Full ctor used by tests for custom policies.
     */
    public DefaultEncounterManager(
            EncounterJoinPolicy joinPolicy,
            EncounterReusePolicy reusePolicy,
            Optional<PlayerDirectory> players,
            Optional<PartyDirectory> parties,
            Optional<EventBus> events) {
        this.joinPolicy = Objects.requireNonNull(joinPolicy, "joinPolicy");
        this.reusePolicy = Objects.requireNonNull(reusePolicy, "reusePolicy");
        this.players = Objects.requireNonNull(players, "players");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public EncounterInstance create(EncounterDefinition definition, EncounterContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");

        // Deterministic key lookup (how tests reason about reuse).
        EncounterKey key = EncounterKey.of(definition, context);

        // Atomic reuse: compute ensures one winner under concurrency.
        DefaultEncounterInstance instance = instancesByKey.compute(key, (k, existing) -> {
            if (existing != null && existing.state != EncounterState.ENDED && reusePolicy.shouldReuse(k, existing)) {
                post(new EncounterReusedEvent(k, existing.instanceId(), definition.id(), context.anchor()));
                return existing;
            }
            UUID instanceId = UUID.randomUUID();
            java.util.Optional<java.util.UUID> ownerPartyId = java.util.Optional.empty();
            java.util.Set<java.util.UUID> ownerPartyMembersAtStart = java.util.Set.of();

            if (definition
                    instanceof
                    io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition) {
                ownerPartyId = context.partyId();
                if (ownerPartyId.isPresent() && parties.isPresent()) {
                    ownerPartyMembersAtStart =
                            parties.get().members(ownerPartyId.get()).orElse(java.util.Set.of());
                }
            }

            DefaultEncounterInstance created = new DefaultEncounterInstance(
                    instanceId, k, definition, context, ownerPartyId, ownerPartyMembersAtStart);
            instances.put(instanceId, created);
            post(new EncounterCreatedEvent(k, instanceId, definition.id(), context.anchor()));
            return created;
        });

        return instance;
    }

    @Override
    public JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(role, "role");

        if (!(instance instanceof DefaultEncounterInstance dei)) {
            return JoinResult.DENIED_POLICY;
        }
        if (dei.state == EncounterState.ENDED) {
            return JoinResult.DENIED_STATE;
        }

        // Policy check (minimal; policy can be expanded later).
        JoinResult policyResult = joinPolicy.evaluate(playerId, dei.definition, dei.context, role, players, parties);
        if (policyResult != JoinResult.SUCCESS) {
            return policyResult;
        }

        boolean startedNow = false;
        JoinResult result = JoinResult.SUCCESS;

        synchronized (dei) {
            switch (role) {
                case PARTICIPANT -> {
                    int max = dei.definition.maxParticipants();
                    if (!dei.participants.contains(playerId) && dei.participants.size() >= max) {
                        result = JoinResult.DENIED_FULL;
                        break;
                    }
                    dei.participants.add(playerId);
                    dei.spectators.remove(playerId);
                    if (dei.state == EncounterState.CREATED) {
                        dei.state = EncounterState.RUNNING;
                        startedNow = true;
                    }
                }
                case SPECTATOR -> {
                    int max = dei.definition.maxSpectators();
                    if (!dei.spectators.contains(playerId) && dei.spectators.size() >= max) {
                        result = JoinResult.DENIED_FULL;
                        break;
                    }
                    dei.spectators.add(playerId);
                    dei.participants.remove(playerId);
                }
            }
        }

        if (result != JoinResult.SUCCESS) {
            return result;
        }

        if (startedNow) {
            post(new EncounterStartedEvent(
                    dei.key, dei.instanceId, dei.definition.id(), dei.context.anchor(), playerId));
        }

        return JoinResult.SUCCESS;
    }

    @Override
    public void leave(UUID playerId, EncounterInstance instance) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(instance, "instance");

        if (instance instanceof DefaultEncounterInstance dei) {
            dei.participants.remove(playerId);
            dei.spectators.remove(playerId);
        }
    }

    @Override
    public void end(EncounterInstance instance, EndReason reason) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(reason, "reason");

        if (instance instanceof DefaultEncounterInstance dei) {
            boolean endedNow = false;

            synchronized (dei) {
                if (dei.state != EncounterState.ENDED) {
                    dei.state = EncounterState.ENDED;
                    endedNow = true;
                }
            }

            if (endedNow) {
                post(new EncounterEndedEvent(
                        dei.key, dei.instanceId, dei.definition.id(), dei.context.anchor(), reason));
                post(new EncounterCleanupEvent(dei.instanceId));
            }
        }
    }

    @Override
    public Optional<EncounterInstance> byInstanceId(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return Optional.ofNullable(instances.get(instanceId));
    }

    @Override
    public Optional<EncounterInstance> byKey(EncounterKey key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(instancesByKey.get(key));
    }

    private void post(io.github.legendaryforge.legendary.core.api.event.Event event) {
        events.ifPresent(bus -> bus.post(event));
    }

    private static final class DefaultEncounterInstance implements EncounterInstance, LegendaryEncounterInstanceView {

        private final UUID instanceId;
        private final EncounterKey key;
        private final EncounterDefinition definition;
        private final EncounterContext context;

        // Legendary-only metadata (populated when definition is a LegendaryEncounterDefinition)
        private final java.util.Optional<java.util.UUID> ownerPartyId;
        private final java.util.Set<java.util.UUID> ownerPartyMembersAtStart;

        private volatile EncounterState state;
        private final Set<UUID> participants = new LinkedHashSet<>();
        private final Set<UUID> spectators = new LinkedHashSet<>();

        private DefaultEncounterInstance(
                UUID instanceId,
                EncounterKey key,
                EncounterDefinition definition,
                EncounterContext context,
                java.util.Optional<java.util.UUID> ownerPartyId,
                java.util.Set<java.util.UUID> ownerPartyMembersAtStart) {
            this.instanceId = instanceId;
            this.key = key;
            this.definition = definition;
            this.context = context;
            this.ownerPartyId = java.util.Objects.requireNonNull(ownerPartyId, "ownerPartyId");
            this.ownerPartyMembersAtStart = java.util.Set.copyOf(
                    java.util.Objects.requireNonNull(ownerPartyMembersAtStart, "ownerPartyMembersAtStart"));
            this.state = EncounterState.CREATED;
        }

        @Override
        public UUID instanceId() {
            return instanceId;
        }

        @Override
        public EncounterDefinition definition() {
            return definition;
        }

        @Override
        public EncounterState state() {
            return state;
        }

        @Override
        public Set<UUID> participants() {
            return participants;
        }

        @Override
        public java.util.Optional<java.util.UUID> ownerPartyId() {
            return ownerPartyId;
        }

        @Override
        public java.util.Set<java.util.UUID> ownerPartyMembersAtStart() {
            return ownerPartyMembersAtStart;
        }

        @Override
        public Set<UUID> spectators() {
            return spectators;
        }
    }
}

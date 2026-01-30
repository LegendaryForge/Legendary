package io.github.legendaryforge.legendary.content.encounter.toystorm;

import io.github.legendaryforge.legendary.content.EncounterScript;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ToyStormScript
        implements EncounterScript, io.github.legendaryforge.legendary.content.EncounterCleanupHandler {

    private record State(PhaseMachine phases, int participants) {
        State {
            Objects.requireNonNull(phases, "phases");
        }

        State withParticipants(int next) {
            return new State(phases, next);
        }
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ToyStormEndSummary> endSummaries = new ConcurrentHashMap<>();

    @Override
    public void onStart(EncounterInstance instance, UUID triggeringPlayerId) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(triggeringPlayerId, "triggeringPlayerId");

        UUID id = instance.instanceId();
        State state = states.computeIfAbsent(id, k -> new State(new PhaseMachine(), 0));
        state.phases().enter(EncounterPhase.CHARGE, p -> {});
    }

    @Override
    public void onJoin(EncounterInstance instance, UUID playerId, ParticipationRole role) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");

        if (role != ParticipationRole.PARTICIPANT) {
            return;
        }

        UUID id = instance.instanceId();
        states.compute(id, (k, prev) -> {
            State current = prev == null ? new State(new PhaseMachine(), 0) : prev;
            int nextParticipants = current.participants() + 1;

            if (nextParticipants == 1) {
                current.phases().enter(EncounterPhase.DISCHARGE, p -> {});
            } else if (nextParticipants == 2) {
                current.phases().enter(EncounterPhase.RECOVERY, p -> {});
            }

            return current.withParticipants(nextParticipants);
        });
    }

    @Override
    public void onEnd(EncounterInstance instance) {
        Objects.requireNonNull(instance, "instance");

        UUID id = instance.instanceId();
        endSummaries.computeIfAbsent(id, ignored -> {
            EncounterPhase phase = phaseFor(id);
            int participantsAtEnd = instance.participants().size();
            return new ToyStormEndSummary(id, participantsAtEnd, phase);
        });
    }

    public EncounterPhase phaseFor(UUID instanceId) {
        State state = states.get(instanceId);
        return state == null ? null : state.phases().current();
    }

    public ToyStormEndSummary endSummaryFor(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return endSummaries.get(instanceId);
    }

    public void cleanup(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        states.remove(instanceId);
    }

    @Override
    public void onCleanup(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        cleanup(instanceId);
    }
}

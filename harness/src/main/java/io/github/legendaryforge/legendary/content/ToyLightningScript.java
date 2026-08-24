package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Toy script that models a simple "charge-up" encounter.
 *
 * <p>Deterministic rules:
 * <ul>
 *   <li>Participant join: +2 charge</li>
 *   <li>Spectator join: +0 charge (view-only)</li>
 *   <li>Reward tier at end depends only on (participants at end) and (charge)</li>
 * </ul>
 */
public final class ToyLightningScript implements EncounterScript, EncounterCleanupHandler {

    public enum RewardTier {
        NONE,
        MINOR,
        MAJOR
    }

    private static final class State {
        private int charge;
        private int starts;
        private RewardTier rewardTier = RewardTier.NONE;
        private boolean ended;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ToyLightningEndSummary> endSummaries = new ConcurrentHashMap<>();

    @Override
    public void onStart(EncounterInstance instance, UUID triggeringPlayerId) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(triggeringPlayerId, "triggeringPlayerId");
        State s = states.computeIfAbsent(instance.instanceId(), id -> new State());
        if (!s.ended) {
            s.starts++;
        }
    }

    @Override
    public void onJoin(EncounterInstance instance, UUID playerId, ParticipationRole role) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");

        State s = states.computeIfAbsent(instance.instanceId(), id -> new State());
        if (s.ended) {
            return;
        }

        if (role == ParticipationRole.PARTICIPANT) {
            s.charge += 2;
        }
    }

    @Override
    public void onEnd(EncounterInstance instance) {
        Objects.requireNonNull(instance, "instance");

        UUID id = instance.instanceId();
        State s = states.computeIfAbsent(id, ignored -> new State());
        if (s.ended) {
            return;
        }

        s.ended = true;

        int participants = instance.participants().size();
        int score = participants + s.charge;

        // Deterministic tier thresholds.
        if (participants <= 0) {
            s.rewardTier = RewardTier.NONE;
        } else if (score >= 8) {
            s.rewardTier = RewardTier.MAJOR;
        } else {
            s.rewardTier = RewardTier.MINOR;
        }

        endSummaries.putIfAbsent(id, new ToyLightningEndSummary(id, participants, s.charge, s.starts, s.rewardTier));
    }

    public int chargeFor(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        State s = states.get(instanceId);
        return s == null ? 0 : s.charge;
    }

    public int startsFor(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        State s = states.get(instanceId);
        return s == null ? 0 : s.starts;
    }

    public RewardTier rewardTierFor(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        State s = states.get(instanceId);
        return s == null ? RewardTier.NONE : s.rewardTier;
    }

    public ToyLightningEndSummary endSummaryFor(UUID instanceId) {
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

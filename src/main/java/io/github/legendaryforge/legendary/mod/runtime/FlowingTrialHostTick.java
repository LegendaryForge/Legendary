package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1Outcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase D host tick seam: drives the Flowing Trial per-player loop without engine assumptions.
 *
 * <p>Maintains one {@link FlowingTrialSession} per player ID for the lifetime of this instance.
 * The host owns persistence; this seam simply orchestrates per-tick stepping and hint emission.
 */
public final class FlowingTrialHostTick {

    private final Map<String, FlowingTrialSession> sessions = new HashMap<>();

    /** Advances all participating players by one tick. */
    public void tick(StormseekerHostRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");

        for (String playerId : runtime.playerIds()) {
            Objects.requireNonNull(playerId, "playerId");

            StormseekerProgress progress = runtime.progress(playerId);
            boolean hadSigilA = progress.hasSigilA();

            FlowingTrialSession session = sessions.computeIfAbsent(playerId, id -> new FlowingTrialSession(progress));

            var step = session.step(runtime.motionSample(playerId));

            boolean hasSigilANow = progress.hasSigilA();
            if (!hadSigilA && hasSigilANow) {
                runtime.emitPhase1Outcome(new StormseekerPhase1Outcome(playerId, true));
            }

            runtime.emitFlowHint(playerId, step.hint());
            runtime.onFlowingTrialStep(playerId, step);
        }
    }

    /**
     * Removes a player's session (host can call this when a player leaves the trial or disconnects).
     *
     * @return true if a session existed and was removed; false otherwise.
     */
    public boolean removePlayer(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return sessions.remove(playerId) != null;
    }

    /** Removes all sessions. */
    public void clear() {
        sessions.clear();
    }

    /**
     * Retains sessions only for the provided active player IDs.
     *
     * <p>Convenience for long-lived hosts that want to avoid session accumulation.
     */
    public void retainOnly(Iterable<String> activePlayerIds) {
        Objects.requireNonNull(activePlayerIds, "activePlayerIds");

        Set<String> keep = new HashSet<>();
        for (String id : activePlayerIds) {
            Objects.requireNonNull(id, "playerId");
            keep.add(id);
        }

        sessions.keySet().retainAll(keep);
    }

    /** Visible for testing. */
    FlowingTrialSession sessionForTesting(String playerId) {
        return sessions.get(playerId);
    }
}

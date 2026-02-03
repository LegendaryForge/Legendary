package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
            FlowingTrialSession session = sessions.computeIfAbsent(playerId, id -> new FlowingTrialSession(progress));

            var step = session.step(runtime.motionSample(playerId));

            runtime.emitFlowHint(playerId, step.hint());
            runtime.onFlowingTrialStep(playerId, step);
        }
    }

    /** Visible for testing. */
    FlowingTrialSession sessionForTesting(String playerId) {
        return sessions.get(playerId);
    }
}

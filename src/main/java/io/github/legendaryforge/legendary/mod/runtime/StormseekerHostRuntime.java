package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1Outcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1TickView;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;

/**
 * Host-facing runtime contract used by Stormseeker coordinators.
 *
 * <p>This interface is intentionally engine-agnostic. Hosts provide perception (motion/progress)
 * and receive emissions (hints, tick views, durable outcomes).
 */
public interface StormseekerHostRuntime {

    Iterable<String> playerIds();

    MotionSample motionSample(String playerId);

    StormseekerProgress progress(String playerId);

    void emitFlowHint(String playerId, FlowHintIntent hint);

    /**
     * Optional hook: observe each Flowing Trial step for a player.
     * Default is no-op so host implementations are not forced to handle it.
     */
    default void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {}

    /**
     * Optional host hook: receive the per-player Phase 1 tick read model.
     * Default is no-op so host implementations are not forced to handle it.
     */
    default void emitPhase1TickView(StormseekerPhase1TickView view) {}

    /**
     * Durable Phase 1 outcome notification (emitted exactly once per player).
     * Default is no-op.
     */
    default void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {}

    /**
     * Durable milestone signal emitted at most once per player+milestone edge.
     */
    default void emitPhase1Outcome(StormseekerPhase1Outcome outcome) {
        emitStormseekerMilestone(new StormseekerMilestoneOutcome(outcome.playerId(), outcome.milestone()));
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.runtime;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerFlowingTrialOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerFlowingTrialTickView;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored.AnchoredTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;

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
     * Optional host hook: receive the per-player Flowing Trial tick read model.
     * Default is no-op so host implementations are not forced to handle it.
     */
    default void emitFlowingTrialTickView(StormseekerFlowingTrialTickView view) {}

    /**
     * Durable Flowing Trial outcome notification (emitted exactly once per player).
     * Default is no-op.
     */
    default void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {}

    /**
     * Durable milestone signal emitted at most once per player+milestone edge.
     */
    default void emitFlowingTrialOutcome(StormseekerFlowingTrialOutcome outcome) {
        emitStormseekerMilestone(new StormseekerMilestoneOutcome(outcome.playerId(), outcome.milestone()));
    }

    /** Host presentation hook for the Anchored Trial (Phase 2). */
    default void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {}
}

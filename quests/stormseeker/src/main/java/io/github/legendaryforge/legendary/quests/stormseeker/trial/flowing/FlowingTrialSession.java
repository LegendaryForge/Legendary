package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import java.util.Objects;

/**
 * Phase C facade: binds evaluator mechanics + progress proof granting for a single player.
 *
 * <p>Engine/runtime integration responsibility:
 * <ul>
 *   <li>Store one session per player (or reconstruct deterministically from persisted state).</li>
 *   <li>Translate engine movement into {@link MotionSample} per tick.</li>
 *   <li>Persist {@link StormseekerProgress} and (eventually) durable trial state as needed.</li>
 * </ul>
 */
public final class FlowingTrialSession {

    private final StormseekerProgress progress;
    private final FlowingTrialTuning tuning;
    private final FlowingLodestoneGrantSystem lodestoneGrantSystem;

    private FlowingTrialState state;

    public FlowingTrialSession(StormseekerProgress progress) {
        this(progress, FlowingTrialTuning.defaults(), new FlowingLodestoneGrantSystem(), new FlowingTrialState());
    }

    public FlowingTrialSession(StormseekerProgress progress, FlowingTrialTuning tuning) {
        this(progress, tuning, new FlowingLodestoneGrantSystem(), new FlowingTrialState());
    }

    public FlowingTrialSession(
            StormseekerProgress progress,
            FlowingTrialTuning tuning,
            FlowingLodestoneGrantSystem lodestoneGrantSystem,
            FlowingTrialState initialState) {
        this.progress = Objects.requireNonNull(progress, "progress");
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.lodestoneGrantSystem = Objects.requireNonNull(lodestoneGrantSystem, "lodestoneGrantSystem");
        this.state = Objects.requireNonNull(initialState, "initialState");
    }

    public StormseekerProgress progress() {
        return progress;
    }

    public FlowingTrialState state() {
        return state;
    }

    /**
     * Advances the session by one movement sample tick.
     *
     * @return step result containing hint + completion + whether lodestone was granted this tick.
     */
    public FlowingTrialSessionStep step(MotionSample sample) {
        FlowingTrialStepResult r = FlowingTrialEvaluator.step(state, sample, tuning);
        this.state = r.state();

        boolean lodestoneGranted = false;
        if (r.completedThisTick()) {
            lodestoneGranted = lodestoneGrantSystem.onFlowingTrialCompleted(progress);
        }

        return new FlowingTrialSessionStep(state.status, r.hint(), r.completedThisTick(), lodestoneGranted);
    }
}

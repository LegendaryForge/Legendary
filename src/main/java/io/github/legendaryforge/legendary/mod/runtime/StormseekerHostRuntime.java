package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;

/**
 * Host/runtime adapter for driving Stormseeker gameplay without engine coupling.
 *
 * <p>Implementations are responsible for:
 * <ul>
 *   <li>Providing the current movement sample per player per tick.</li>
 *   <li>Supplying and persisting authoritative {@link StormseekerProgress}.</li>
 *   <li>Translating {@link FlowHintIntent} into platform-specific feedback.</li>
 * </ul>
 */
public interface StormseekerHostRuntime {

    /** Returns the set of players currently participating in the Flowing Trial loop. */
    Iterable<String> playerIds();

    /** Returns the current tick's movement sample for a player. */
    MotionSample motionSample(String playerId);

    /** Returns the authoritative quest progress object for a player. */
    StormseekerProgress progress(String playerId);

    /** Receives hint intent for the current tick (host translates to UI/VFX/etc.). */
    void emitFlowHint(String playerId, FlowHintIntent hint);

    /**
     * Optional host hook: receive the per-player Phase 1 tick read model.
     * Default is no-op so host implementations are not forced to handle it.
     */
    default void emitPhase1TickView(
            io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1TickView view) {}

    /**
     * Receives the step result for the current tick (optional hook for host-side effects).
     *
     * <p>Called after {@link #emitFlowHint(String, FlowHintIntent)}.
     */
    default void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {}
}

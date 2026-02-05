package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialParticipation;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Host driver for Anchored Trial.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Filter the host runtime player set to only those participating.</li>
 *   <li>Delegate to {@link AnchoredTrialHostTick} for per-tick stepping and milestone emission.</li>
 * </ul>
 */
public final class AnchoredTrialHostDriver {

    private final AnchoredTrialHostTick hostTick;

    public AnchoredTrialHostDriver(AnchoredTrialHostTick hostTick) {
        this.hostTick = Objects.requireNonNull(hostTick, "hostTick");
    }

    public void tick(StormseekerHostRuntime runtime, AnchoredTrialParticipation participation) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(participation, "participation");
        hostTick.tick(new ParticipatingOnlyRuntime(runtime, participation));
    }

    /**
     * Explicit leave+cleanup helper (policy seam).
     *
     * <p>Participation membership gates ticking but does not reset session state. If an integration wants
     * leaving participation to also clear Anchored Trial host tick state, call this method.
     */
    public void leaveAndCleanup(
            StormseekerHostRuntime runtime, AnchoredTrialParticipation participation, String playerId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(participation, "participation");
        Objects.requireNonNull(playerId, "playerId");
        participation.leave(playerId);
        hostTick.removePlayer(playerId);
    }

    private static final class ParticipatingOnlyRuntime implements StormseekerHostRuntime {

        private final StormseekerHostRuntime delegate;
        private final AnchoredTrialParticipation participation;

        private ParticipatingOnlyRuntime(StormseekerHostRuntime delegate, AnchoredTrialParticipation participation) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.participation = Objects.requireNonNull(participation, "participation");
        }

        @Override
        public Iterable<String> playerIds() {
            return new ArrayList<>(participation.playerIds());
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            return delegate.progress(playerId);
        }

        @Override
        public MotionSample motionSample(String playerId) {
            return delegate.motionSample(playerId);
        }

        @Override
        public void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {
            delegate.emitStormseekerMilestone(outcome);
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            delegate.emitFlowHint(playerId, hint);
        }

        @Override
        public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {
            delegate.onFlowingTrialStep(playerId, step);
        }

        @Override
        public void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {
            delegate.onAnchoredTrialStep(playerId, step);
        }
    }
}

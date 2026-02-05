package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialParticipation;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Host driver for Anchored Trial.
 *
 * <p>Responsibilities:
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
        public io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress progress(String playerId) {
            return delegate.progress(playerId);
        }

        @Override
        public io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample motionSample(
                String playerId) {
            return delegate.motionSample(playerId);
        }

        @Override
        public void emitStormseekerMilestone(
                io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome outcome) {
            delegate.emitStormseekerMilestone(outcome);
        }

        @Override
        public void emitFlowHint(
                String playerId, io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent hint) {
            delegate.emitFlowHint(playerId, hint);
        }

        @Override
        public void onFlowingTrialStep(
                String playerId,
                io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep step) {
            delegate.onFlowingTrialStep(playerId, step);
        }
    }
}

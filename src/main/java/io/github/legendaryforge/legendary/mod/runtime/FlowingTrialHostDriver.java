package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1TickView;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialParticipation;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.Objects;

/**
 * Phase F: binds participation state to the Phase D/E host tick seam.
 *
 * <p>This coordinator is engine-agnostic. The host is responsible for:
 * <ul>
 *   <li>Updating {@link FlowingTrialParticipation} (enter/leave).</li>
 *   <li>Providing movement + progress via {@link StormseekerHostRuntime}.</li>
 * </ul>
 */
public final class FlowingTrialHostDriver {

    private final FlowingTrialHostTick hostTick;

    public FlowingTrialHostDriver(FlowingTrialHostTick hostTick) {
        this.hostTick = Objects.requireNonNull(hostTick, "hostTick");
    }

    public void tick(StormseekerHostRuntime runtime, FlowingTrialParticipation participation) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(participation, "participation");

        hostTick.retainOnly(participation.playerIdsView());
        hostTick.tick(new ParticipatingOnlyRuntime(runtime, participation));
    }

    private static final class ParticipatingOnlyRuntime implements StormseekerHostRuntime {

        private final StormseekerHostRuntime delegate;
        private final FlowingTrialParticipation participation;

        private ParticipatingOnlyRuntime(StormseekerHostRuntime delegate, FlowingTrialParticipation participation) {
            this.delegate = delegate;
            this.participation = participation;
        }

        @Override
        public Iterable<String> playerIds() {
            return participation.playerIdsView();
        }

        @Override
        public MotionSample motionSample(String playerId) {
            return delegate.motionSample(playerId);
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            return delegate.progress(playerId);
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            delegate.emitFlowHint(playerId, hint);
        }

        @Override
        public void emitPhase1TickView(StormseekerPhase1TickView view) {
            for (String id : participation.playerIdsView()) {
                if (id.equals(view.playerId())) {
                    delegate.emitPhase1TickView(view);
                    return;
                }
            }
        }

        @Override
        public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {
            delegate.onFlowingTrialStep(playerId, step);
        }
    }
}

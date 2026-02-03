package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FlowingTrialHostTickTest {

    @Test
    void tickEmitsHintsAndGrantsSigilAIdempotently() {
        FlowingTrialHostTick hostTick = new FlowingTrialHostTick();
        FakeRuntime rt = new FakeRuntime();

        assertFalse(rt.progress.hasSigilA());

        // Drive enough ticks to complete. We don't care about exact tuning here;
        // we just feed consistent movement until the session reports completion.
        boolean completedObserved = false;
        for (int i = 0; i < 500; i++) {
            hostTick.tick(rt);
            if (rt.completedSteps.size() > 0
                    && rt.completedSteps.get(rt.completedSteps.size() - 1).completedThisTick()) {
                completedObserved = true;
                break;
            }
        }

        assertTrue(rt.hints.size() > 0, "expected at least one hint emission");
        assertTrue(completedObserved, "expected completion to be observed within tick budget");
        assertTrue(rt.progress.hasSigilA(), "sigil A should be granted on completion");

        // Tick some more; sigil A must remain granted and not flip/flop.
        for (int i = 0; i < 50; i++) {
            hostTick.tick(rt);
        }
        assertTrue(rt.progress.hasSigilA());
    }

    private static final class FakeRuntime implements StormseekerHostRuntime {

        private final StormseekerProgress progress = new StormseekerProgress();
        private final List<FlowHintIntent> hints = new ArrayList<>();
        private final List<FlowingTrialSessionStep> completedSteps = new ArrayList<>();

        @Override
        public Iterable<String> playerIds() {
            return List.of("p1");
        }

        @Override
        public MotionSample motionSample(String playerId) {
            // Constant movement in one direction; evaluator normalizes direction anyway.
            return new MotionSample(1.0, 0.0, 0.0, true);
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            return progress;
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            hints.add(hint);
        }

        @Override
        public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {
            if (step.completedThisTick()) {
                completedSteps.add(step);
            }
        }
    }
}

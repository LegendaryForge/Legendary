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

        boolean completedObserved = false;
        for (int i = 0; i < 500; i++) {
            hostTick.tick(rt);
            if (!rt.completedSteps.isEmpty()
                    && rt.completedSteps.get(rt.completedSteps.size() - 1).completedThisTick()) {
                completedObserved = true;
                break;
            }
        }

        assertFalse(rt.hints.isEmpty(), "expected at least one hint emission");
        assertTrue(completedObserved, "expected completion to be observed within tick budget");
        assertTrue(rt.progress.hasSigilA(), "sigil A should be granted on completion");

        for (int i = 0; i < 50; i++) {
            hostTick.tick(rt);
        }
        assertTrue(rt.progress.hasSigilA());
    }

    @Test
    void canRemovePlayerSession() {
        FlowingTrialHostTick hostTick = new FlowingTrialHostTick();
        FakeRuntime rt = new FakeRuntime();

        hostTick.tick(rt);
        assertNotNull(hostTick.sessionForTesting("p1"), "session should exist after tick");

        assertTrue(hostTick.removePlayer("p1"), "expected removal to return true");
        assertNull(hostTick.sessionForTesting("p1"), "session should be removed");

        assertFalse(hostTick.removePlayer("p1"), "second removal should return false");
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

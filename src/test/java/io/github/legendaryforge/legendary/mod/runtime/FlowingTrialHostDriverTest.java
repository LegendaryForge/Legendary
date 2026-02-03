package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialParticipation;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FlowingTrialHostDriverTest {

    @Test
    void leavingParticipationDropsSessionsViaRetainOnly() {
        FlowingTrialHostTick tick = new FlowingTrialHostTick();
        FlowingTrialHostDriver driver = new FlowingTrialHostDriver(tick);

        FlowingTrialParticipation p = new FlowingTrialParticipation();
        FakeRuntime rt = new FakeRuntime();

        p.enter("p1");
        driver.tick(rt, p);
        assertNotNull(tick.sessionForTesting("p1"));

        p.leave("p1");
        driver.tick(rt, p);
        assertNull(tick.sessionForTesting("p1"));
    }

    private static final class FakeRuntime implements StormseekerHostRuntime {

        private final StormseekerProgress progress = new StormseekerProgress();
        private final List<FlowHintIntent> hints = new ArrayList<>();

        @Override
        public Iterable<String> playerIds() {
            // Driver wraps this; not used.
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
        public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {}
    }
}

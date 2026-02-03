package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostDriver;
import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostTick;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StormseekerAttunementServiceTest {

    @Test
    void cannotEnterBeforePhase1() {
        StormseekerAttunementService s = new StormseekerAttunementService();
        StormseekerProgress p = new StormseekerProgress(); // PHASE_0_UNEASE

        assertFalse(s.enterFlowingTrial("p1", p));
        assertFalse(s.isParticipatingForTesting("p1"));
    }

    @Test
    void cannotEnterIfAlreadyHasSigilA() {
        StormseekerAttunementService s = new StormseekerAttunementService();
        StormseekerProgress p = new StormseekerProgress();
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);
        p.grantSigilA();

        assertFalse(s.enterFlowingTrial("p1", p));
        assertFalse(s.isParticipatingForTesting("p1"));
    }

    @Test
    void canEnterInPhase1AndLeave() {
        StormseekerAttunementService s = new StormseekerAttunementService();
        StormseekerProgress p = new StormseekerProgress();
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);

        assertTrue(s.enterFlowingTrial("p1", p));
        assertTrue(s.isParticipatingForTesting("p1"));

        assertTrue(s.leaveFlowingTrial("p1"));
        assertFalse(s.isParticipatingForTesting("p1"));
    }

    @Test
    void tickingRunsDriverForParticipatingPlayers() {
        FlowingTrialHostTick tick = new FlowingTrialHostTick();
        StormseekerAttunementService s = new StormseekerAttunementService(new FlowingTrialHostDriver(tick));

        StormseekerProgress p = new StormseekerProgress();
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);
        assertTrue(s.enterFlowingTrial("p1", p));

        final boolean[] hintEmitted = new boolean[] {false};

        StormseekerHostRuntime rt = new StormseekerHostRuntime() {
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
                return p;
            }

            @Override
            public void emitFlowHint(String playerId, FlowHintIntent hint) {
                hintEmitted[0] = true;
            }

            @Override
            public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {}
        };

        s.tick(rt);
        assertTrue(hintEmitted[0], "expected at least one hint emission for participating player");
    }
}

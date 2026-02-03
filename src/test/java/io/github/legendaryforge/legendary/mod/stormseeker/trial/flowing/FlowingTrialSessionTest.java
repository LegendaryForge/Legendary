package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import org.junit.jupiter.api.Test;

final class FlowingTrialSessionTest {

    @Test
    void completionGrantsSigilAOnce() {
        // Make the test deterministic + fast by using low thresholds.
        FlowingTrialTuning t = new FlowingTrialTuning(
                1.0, // emerge
                2.0, // active
                1, // stable ticks
                0.60, // aligned
                0.20, // misaligned
                5.0, // progress gain
                1.0, // progress decay
                10.0, // completion threshold
                0.25);

        StormseekerProgress p = new StormseekerProgress();
        FlowingTrialSession session = new FlowingTrialSession(p, t);

        boolean granted = false;
        for (int i = 0; i < 200; i++) {
            FlowingTrialSessionStep r = session.step(new MotionSample(1, 0, 0, true));
            granted |= r.sigilGrantedThisTick();
            if (granted) {
                break;
            }
        }

        assertTrue(granted, "should grant sigil A upon completion");
        assertTrue(p.hasSigilA(), "progress should reflect sigil A proof");

        // Keep stepping; sigil should not be granted again.
        boolean grantedAgain = false;
        for (int i = 0; i < 200; i++) {
            FlowingTrialSessionStep r = session.step(new MotionSample(1, 0, 0, true));
            grantedAgain |= r.sigilGrantedThisTick();
        }
        assertFalse(grantedAgain, "sigil grant should be idempotent");
    }

    @Test
    void hintsAreZeroWhenIdle() {
        FlowingTrialTuning t = new FlowingTrialTuning(1.0, 2.0, 1, 0.60, 0.20, 1.0, 0.5, 100.0, 0.25);

        FlowingTrialSession session = new FlowingTrialSession(new StormseekerProgress(), t);

        // Warm up with a few movement samples so we're not INACTIVE forever.
        for (int i = 0; i < 20; i++) {
            session.step(new MotionSample(1, 0, 0, true));
        }

        FlowingTrialSessionStep idle = session.step(new MotionSample(0, 0, 0, false));
        assertEquals(0.0, idle.hint().intensity(), 1e-9);
        assertEquals(0.0, idle.hint().stability(), 1e-9);
        assertEquals(0.0, idle.hint().directionHintStrength(), 1e-9);
    }
}

package io.github.legendaryforge.legendarycontent.dogfood.stormseeker;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSession;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialTuning;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import org.junit.jupiter.api.Test;

final class FlowingTrialDogfoodHarnessTest {

    @Test
    void phaseC_canBeDrivenEndToEnd_andGrantsFlowingSigilOnce() {
        // Fast deterministic tuning for dogfood.
        FlowingTrialTuning t =
                new FlowingTrialTuning(
                        1.0, 2.0, 1,
                        0.60, 0.20,
                        5.0, 1.0,
                        10.0,
                        0.25);

        StormseekerProgress progress = new StormseekerProgress();
        FlowingTrialSession session = new FlowingTrialSession(progress, t);

        // Idle produces no hint.
        FlowingTrialSessionStep idle = session.step(new MotionSample(0, 0, 0, false));
        assertHintZero(idle.hint());

        boolean sawNonZeroHint = false;
        boolean granted = false;

        for (int i = 0; i < 300; i++) {
            FlowingTrialSessionStep r = session.step(new MotionSample(1, 0, 0, true));
            sawNonZeroHint |= r.hint().intensity() > 0.0;
            granted |= r.sigilGrantedThisTick();
            if (granted) {
                break;
            }
        }

        assertTrue(sawNonZeroHint, "moving should produce hint intent");
        assertTrue(granted, "completion should grant Sigil A");
        assertTrue(progress.hasSigilA(), "progress should reflect Sigil A proof");

        // Further ticks must not grant again (idempotent).
        boolean grantedAgain = false;
        for (int i = 0; i < 200; i++) {
            FlowingTrialSessionStep r = session.step(new MotionSample(1, 0, 0, true));
            grantedAgain |= r.sigilGrantedThisTick();
        }
        assertFalse(grantedAgain, "sigil grant must be idempotent");
    }

    @Test
    void missteps_doNotHardFail_andRecoveryStillCompletes() {
        FlowingTrialTuning t =
                new FlowingTrialTuning(
                        1.0, 2.0, 1,
                        0.60, 0.20,
                        5.0, 2.0,  // decay strong enough that missteps matter, but never hard-fail
                        10.0,
                        0.25);

        StormseekerProgress progress = new StormseekerProgress();
        FlowingTrialSession session = new FlowingTrialSession(progress, t);

        boolean granted = false;

        // Build: coherent motion for a while (may or may not complete depending on evaluator shape).
        for (int i = 0; i < 60; i++) {
            FlowingTrialSessionStep r = session.step(new MotionSample(1, 0, 0, true));
            granted |= r.sigilGrantedThisTick();
        }

        // Missteps: reverse direction. Contract: this must not create an additional grant.
        boolean grantedDuringMissteps = false;
        for (int i = 0; i < 40; i++) {
            FlowingTrialSessionStep r = session.step(new MotionSample(-1, 0, 0, true));
            grantedDuringMissteps |= r.sigilGrantedThisTick();
        }
        assertFalse(grantedDuringMissteps, "missteps must not produce a new sigil grant");

        // Recovery: if not yet granted, must still be able to reach completion eventually.
        if (!granted) {
            for (int i = 0; i < 500; i++) {
                FlowingTrialSessionStep r = session.step(new MotionSample(1, 0, 0, true));
                granted |= r.sigilGrantedThisTick();
                if (granted) {
                    break;
                }
            }
            assertTrue(granted, "should be able to recover to completion after missteps");
        }

        assertTrue(progress.hasSigilA(), "Sigil A proof must be present after completion");

        // Idempotent after first completion.
        boolean grantedAgain = false;
        for (int i = 0; i < 150; i++) {
            grantedAgain |= session.step(new MotionSample(1, 0, 0, true)).sigilGrantedThisTick();
        }
        assertFalse(grantedAgain, "sigil grant must remain idempotent");
    }

    private static void assertHintZero(FlowHintIntent hint) {
        assertEquals(0.0, hint.intensity(), 1e-9);
        assertEquals(0.0, hint.stability(), 1e-9);
        assertEquals(0.0, hint.directionHintStrength(), 1e-9);
    }
}

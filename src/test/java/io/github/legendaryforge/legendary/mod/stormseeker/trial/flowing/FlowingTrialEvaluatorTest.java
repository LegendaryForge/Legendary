package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FlowingTrialEvaluatorTest {

    @Test
    void activatesFromSustainedCoherentMovement() {
        FlowingTrialTuning t = FlowingTrialTuning.defaults();
        FlowingTrialState s = new FlowingTrialState();

        boolean becameActive = false;
        for (int i = 0; i < 250; i++) {
            FlowingTrialStepResult r = FlowingTrialEvaluator.step(s, MotionSample.moving(1, 0, 0), t);
            s = r.state();
            if (s.status == FlowingTrialStatus.ACTIVE || s.status == FlowingTrialStatus.COMPLETED) {
                becameActive = true;
                break;
            }
        }

        assertTrue(becameActive, "should become ACTIVE from sustained coherent movement");
    }

    @Test
    void hintsExistOnlyWhileMoving() {
        FlowingTrialTuning t = FlowingTrialTuning.defaults();
        FlowingTrialState s = new FlowingTrialState();

        // Warm up enough to get out of INACTIVE in most tunings.
        for (int i = 0; i < 80; i++) {
            FlowingTrialStepResult r = FlowingTrialEvaluator.step(s, MotionSample.moving(1, 0, 0), t);
            s = r.state();
            if (s.status != FlowingTrialStatus.INACTIVE) {
                break;
            }
        }

        FlowingTrialStepResult moving = FlowingTrialEvaluator.step(s, MotionSample.moving(1, 0, 0), t);
        assertTrue(moving.hint().intensity() > 0.0, "moving should produce hint intent");

        FlowingTrialStepResult idle = FlowingTrialEvaluator.step(moving.state(), MotionSample.idle(), t);
        assertEquals(0.0, idle.hint().intensity(), 1e-9, "idle should produce zero hint intent");
        assertEquals(0.0, idle.hint().stability(), 1e-9, "idle should produce zero hint intent");
        assertEquals(0.0, idle.hint().directionHintStrength(), 1e-9, "idle should produce zero hint intent");
    }

    @Test
    void completesViaConsistencyNotLocation() {
        FlowingTrialTuning t = FlowingTrialTuning.defaults();
        FlowingTrialState s = new FlowingTrialState();

        // Drive the evaluator until completion or timeout.
        boolean completed = false;
        for (int i = 0; i < 2000; i++) {
            FlowingTrialStepResult r = FlowingTrialEvaluator.step(s, MotionSample.moving(1, 0, 0), t);
            s = r.state();
            completed |= r.completedThisTick();
            if (completed || s.status == FlowingTrialStatus.COMPLETED) {
                completed = true;
                break;
            }
        }

        assertTrue(completed, "should complete after sufficient aligned ticks");
        assertEquals(FlowingTrialStatus.COMPLETED, s.status, "status should be COMPLETED");
    }

    @Test
    void doesNotHardFailOnMissteps_progressDecaysButCanRecover() {
        FlowingTrialTuning t = FlowingTrialTuning.defaults();
        FlowingTrialState s = new FlowingTrialState();

        // Build state first.
        for (int i = 0; i < 200; i++) {
            FlowingTrialStepResult r = FlowingTrialEvaluator.step(s, MotionSample.moving(1, 0, 0), t);
            s = r.state();
            if (s.status == FlowingTrialStatus.ACTIVE || s.status == FlowingTrialStatus.COMPLETED) {
                break;
            }
        }
        // Missteps: reverse direction for a bit.
        for (int i = 0; i < 40; i++) {
            FlowingTrialStepResult r = FlowingTrialEvaluator.step(s, MotionSample.moving(-1, 0, 0), t);
            s = r.state();
        }

        // Recover to completion.
        boolean completed = false;
        for (int i = 0; i < 2500; i++) {
            FlowingTrialStepResult r = FlowingTrialEvaluator.step(s, MotionSample.moving(1, 0, 0), t);
            s = r.state();
            completed |= r.completedThisTick();
            if (completed || s.status == FlowingTrialStatus.COMPLETED) {
                completed = true;
                break;
            }
        }

        assertTrue(completed, "should be able to recover and still complete");
        assertEquals(FlowingTrialStatus.COMPLETED, s.status);
    }
}

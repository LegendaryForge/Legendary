package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import org.junit.jupiter.api.Test;

final class AnchoredTrialSessionTest {

    @Test
    void grantsSigilBAfterRequiredStationaryTicks() {
        StormseekerProgress progress = new StormseekerProgress();
        AnchoredTrialSession session = new AnchoredTrialSession(progress);

        MotionSample still = new MotionSample(0.0, 0.0, 0.0, false);

        for (int i = 1; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS; i++) {
            AnchoredTrialSessionStep step = session.step(still);
            assertFalse(step.sigilGrantedThisTick(), "should not grant before threshold");
            assertFalse(progress.hasSigilB());
        }

        AnchoredTrialSessionStep granted = session.step(still);
        assertTrue(granted.sigilGrantedThisTick(), "should grant at threshold");
        assertTrue(progress.hasSigilB());
    }

    @Test
    void movingResetsStationaryStreak() {
        StormseekerProgress progress = new StormseekerProgress();
        AnchoredTrialSession session = new AnchoredTrialSession(progress);

        MotionSample still = new MotionSample(0.0, 0.0, 0.0, false);
        MotionSample moving = new MotionSample(0.0, 0.0, 0.0, true);

        session.step(still);
        session.step(still);

        AnchoredTrialSessionStep reset = session.step(moving);
        assertEquals(0, reset.stationaryStreak());
        assertFalse(reset.sigilGrantedThisTick());
        assertFalse(progress.hasSigilB());
    }

    @Test
    void onceGrantedDoesNotGrantAgain() {
        StormseekerProgress progress = new StormseekerProgress();
        AnchoredTrialSession session = new AnchoredTrialSession(progress);

        MotionSample still = new MotionSample(0.0, 0.0, 0.0, false);

        for (int i = 0; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS; i++) {
            session.step(still);
        }
        assertTrue(progress.hasSigilB());

        AnchoredTrialSessionStep after = session.step(still);
        assertFalse(after.sigilGrantedThisTick(), "idempotent after completion");
    }
}

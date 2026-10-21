package io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;
import org.junit.jupiter.api.Test;

final class AnchoredTrialSessionTest {

    @Test
    void grantsStandingLodestoneAfterRequiredStationaryTicks() {
        StormseekerProgress progress = new StormseekerProgress();
        AnchoredTrialSession session = new AnchoredTrialSession(progress);

        MotionSample still = new MotionSample(0.0, 0.0, 0.0, false);

        for (int i = 1; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS; i++) {
            AnchoredTrialSessionStep step = session.step(still);
            assertFalse(step.lodestoneGrantedThisTick(), "should not grant before threshold");
            assertFalse(progress.hasStandingLodestone());
        }

        AnchoredTrialSessionStep granted = session.step(still);
        assertTrue(granted.lodestoneGrantedThisTick(), "should grant at threshold");
        assertTrue(progress.hasStandingLodestone());
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
        assertFalse(reset.lodestoneGrantedThisTick());
        assertFalse(progress.hasStandingLodestone());
    }

    @Test
    void onceGrantedDoesNotGrantAgain() {
        StormseekerProgress progress = new StormseekerProgress();
        AnchoredTrialSession session = new AnchoredTrialSession(progress);

        MotionSample still = new MotionSample(0.0, 0.0, 0.0, false);

        for (int i = 0; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS; i++) {
            session.step(still);
        }
        assertTrue(progress.hasStandingLodestone());

        AnchoredTrialSessionStep after = session.step(still);
        assertFalse(after.lodestoneGrantedThisTick(), "idempotent after completion");
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerPhase;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import org.junit.jupiter.api.Test;

final class FlowingLodestoneIssuerTest {

    @Test
    void grantsCoursingLodestoneIdempotently() {
        StormseekerProgress p = new StormseekerProgress();

        assertFalse(p.hasCoursingLodestone());
        assertTrue(FlowingLodestoneIssuer.grantIfMissing(p));
        assertTrue(p.hasCoursingLodestone());

        // Second call should not re-grant.
        assertFalse(FlowingLodestoneIssuer.grantIfMissing(p));
        assertTrue(p.hasCoursingLodestone());
    }

    @Test
    void doesNotAdvancePastDualLodestonesWithoutBoth() {
        StormseekerProgress p = new StormseekerProgress();

        // Drive to PHASE_4_THE_TRIALS.
        p.advanceIfEligible(); // UNTOUCHED -> 1
        p.advanceIfEligible(); // 1 -> 2
        p.advanceIfEligible(); // 2 -> 3
        p.advanceIfEligible(); // 3 -> 4
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());

        // Grant only the Coursing Lodestone (Flowing).
        assertTrue(FlowingLodestoneIssuer.grantIfMissing(p));
        assertTrue(p.hasCoursingLodestone());
        assertFalse(p.hasStandingLodestone());

        // Still must remain in Phase 2 until the Standing Lodestone exists.
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());
    }

    @Test
    void advancesWhenBothLodestonesExist() {
        StormseekerProgress p = new StormseekerProgress();

        // Drive to PHASE_4_THE_TRIALS.
        p.advanceIfEligible();
        p.advanceIfEligible();
        p.advanceIfEligible();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());

        // Grant the Coursing Lodestone via issuer.
        assertTrue(FlowingLodestoneIssuer.grantIfMissing(p));

        // Grant the Standing Lodestone directly (Anchored mechanics not implemented here).
        p.grantStandingLodestone();

        // Now advancement should occur.
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_5_THE_FRAME, p.phase());
    }

    @Test
    void systemWrapperDelegatesToIssuer() {
        FlowingLodestoneGrantSystem sys = new FlowingLodestoneGrantSystem();
        StormseekerProgress p = new StormseekerProgress();

        assertTrue(sys.onFlowingTrialCompleted(p));
        assertFalse(sys.onFlowingTrialCompleted(p));
    }
}

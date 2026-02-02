package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormseekerProgressTest {

    @Test
    void advancesLinearlyThroughEarlyPhases() {
        StormseekerProgress p = new StormseekerProgress();
        assertEquals(StormseekerPhase.PHASE_0_UNEASE, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_1_ATTUNEMENT, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_1_5_AFTERSHOCK, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());
    }

    @Test
    void doesNotAdvanceOutOfPhase2WithoutBothSigils() {
        StormseekerProgress p = new StormseekerProgress();

        // Move to Phase 2.
        p.advanceIfEligible();
        p.advanceIfEligible();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());

        // Missing sigils -> no advance.
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());

        p.grantSigilA();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());

        p.grantSigilB();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_3_INCOMPLETE_FORM, p.phase());
    }

    @Test
    void strictAdvanceOnlyAllowsImmediateNextPhase() {
        StormseekerProgress p = new StormseekerProgress();

        assertThrows(
                IllegalArgumentException.class, () -> p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_5_AFTERSHOCK));

        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);
        assertEquals(StormseekerPhase.PHASE_1_ATTUNEMENT, p.phase());
    }
}

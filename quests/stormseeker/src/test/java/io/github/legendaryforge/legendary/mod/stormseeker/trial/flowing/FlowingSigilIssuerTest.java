package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import org.junit.jupiter.api.Test;

final class FlowingSigilIssuerTest {

    @Test
    void grantsSigilAIdempotently() {
        StormseekerProgress p = new StormseekerProgress();

        assertFalse(p.hasSigilA());
        assertTrue(FlowingSigilIssuer.grantIfMissing(p));
        assertTrue(p.hasSigilA());

        // Second call should not re-grant.
        assertFalse(FlowingSigilIssuer.grantIfMissing(p));
        assertTrue(p.hasSigilA());
    }

    @Test
    void doesNotAdvancePastDualSigilsWithoutBoth() {
        StormseekerProgress p = new StormseekerProgress();

        // Drive to PHASE_2_DUAL_SIGILS.
        p.advanceIfEligible(); // 0 -> 1
        p.advanceIfEligible(); // 1 -> 1.5
        p.advanceIfEligible(); // 1.5 -> 2
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());

        // Grant only Sigil A (Flowing).
        assertTrue(FlowingSigilIssuer.grantIfMissing(p));
        assertTrue(p.hasSigilA());
        assertFalse(p.hasSigilB());

        // Still must remain in Phase 2 until Sigil B exists.
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());
    }

    @Test
    void advancesWhenBothSigilsExist() {
        StormseekerProgress p = new StormseekerProgress();

        // Drive to PHASE_2_DUAL_SIGILS.
        p.advanceIfEligible();
        p.advanceIfEligible();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_2_DUAL_SIGILS, p.phase());

        // Grant Sigil A via issuer.
        assertTrue(FlowingSigilIssuer.grantIfMissing(p));

        // Grant Sigil B directly (Anchored mechanics not implemented here).
        p.grantSigilB();

        // Now advancement should occur.
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_3_INCOMPLETE_FORM, p.phase());
    }

    @Test
    void systemWrapperDelegatesToIssuer() {
        FlowingSigilGrantSystem sys = new FlowingSigilGrantSystem();
        StormseekerProgress p = new StormseekerProgress();

        assertTrue(sys.onFlowingTrialCompleted(p));
        assertFalse(sys.onFlowingTrialCompleted(p));
    }
}

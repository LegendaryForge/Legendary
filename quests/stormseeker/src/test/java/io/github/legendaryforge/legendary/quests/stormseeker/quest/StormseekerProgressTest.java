package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormseekerProgressTest {

    @Test
    void aNewPlayerHasNotBegunTheQuestline() {
        assertEquals(StormseekerPhase.UNTOUCHED, new StormseekerProgress().phase());
    }

    @Test
    void advancesLinearlyThroughEarlyPhases() {
        StormseekerProgress p = new StormseekerProgress();
        assertEquals(StormseekerPhase.UNTOUCHED, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_1_THE_MARK, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_2_THE_TREK, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_3_THE_WAKING, p.phase());

        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());
    }

    @Test
    void doesNotAdvanceOutOfTheTrialsWithoutBothSigils() {
        StormseekerProgress p = new StormseekerProgress();

        // Drive to The Trials.
        p.advanceIfEligible();
        p.advanceIfEligible();
        p.advanceIfEligible();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());

        // Missing sigils -> no advance.
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());

        p.grantSigilA();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_4_THE_TRIALS, p.phase());

        p.grantSigilB();
        p.advanceIfEligible();
        assertEquals(StormseekerPhase.PHASE_5_THE_FRAME, p.phase());
    }

    @Test
    void strictAdvanceOnlyAllowsImmediateNextPhase() {
        StormseekerProgress p = new StormseekerProgress();

        assertThrows(IllegalArgumentException.class, () -> p.advanceToNextOrThrow(StormseekerPhase.PHASE_2_THE_TREK));

        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_THE_MARK);
        assertEquals(StormseekerPhase.PHASE_1_THE_MARK, p.phase());
    }

    @Test
    void untouchedAndCompleteBracketTheNumberedPhases() {
        assertFalse(StormseekerPhase.UNTOUCHED.isActive(), "not begun is not an active phase");
        assertFalse(StormseekerPhase.COMPLETE.isActive(), "finished is not an active phase");
        for (StormseekerPhase phase : StormseekerPhase.values()) {
            if (phase != StormseekerPhase.UNTOUCHED && phase != StormseekerPhase.COMPLETE) {
                assertTrue(phase.isActive(), phase + " should be an active phase");
            }
        }
    }

    @Test
    void completeIsTheOnlyFinalPhaseAndIsItsOwnSuccessor() {
        for (StormseekerPhase phase : StormseekerPhase.values()) {
            assertEquals(phase == StormseekerPhase.COMPLETE, phase.isFinal(), phase + ".isFinal()");
        }
        assertEquals(StormseekerPhase.COMPLETE, StormseekerPhase.COMPLETE.next());
    }

    @Test
    void everyPhaseIsReachableFromUntouchedInDeclarationOrder() {
        StormseekerPhase phase = StormseekerPhase.UNTOUCHED;
        for (StormseekerPhase expected : StormseekerPhase.values()) {
            assertEquals(expected, phase, "declaration order must match traversal order");
            phase = phase.next();
        }
        assertEquals(StormseekerPhase.COMPLETE, phase);
    }
}

package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormseekerObjectivesTest {

    @Test
    void flowingTrialObjectiveTracksSigilA() {
        StormseekerProgress p = new StormseekerProgress();
        var obj = StormseekerObjectives.flowingTrial();

        assertFalse(obj.isSatisfied(p));
        p.grantSigilA();
        assertTrue(obj.isSatisfied(p));
    }

    @Test
    void phase1AttunementIncludesFlowingTrialOnly() {
        var list = StormseekerObjectives.phase1Attunement();
        assertEquals(1, list.size());
        assertEquals(StormseekerObjectives.OBJECTIVE_FLOWING_TRIAL, list.get(0).id());
    }
}

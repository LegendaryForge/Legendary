package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormseekerQuestStepMapperTest {

    @Test
    void phasesBeforeThreeDoNotProduceGateSteps() {
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_0_UNEASE)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_1_ATTUNEMENT)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_1_5_AFTERSHOCK)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_2_DUAL_SIGILS)
                .isEmpty());
    }

    @Test
    void phaseThreeAndBeyondProduceCanonicalSteps() {
        assertEquals(
                StormseekerQuestSteps.PHASE_3_INCOMPLETE_FORM,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_3_INCOMPLETE_FORM)
                        .orElseThrow());

        assertEquals(
                StormseekerQuestSteps.PHASE_4_STORMS_ANSWER,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_4_STORMS_ANSWER)
                        .orElseThrow());

        assertEquals(
                StormseekerQuestSteps.PHASE_5_FINAL_TEMPERING,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_5_FINAL_TEMPERING)
                        .orElseThrow());
    }
}

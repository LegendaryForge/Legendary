package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormseekerQuestStepMapperTest {

    @Test
    void phasesBeforeThreeDoNotProduceGateSteps() {
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_0_WATCHING_ELEMENTAL)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_1_STORM_TREK)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_1_5_ATTUNEMENT)
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
                StormseekerQuestSteps.PHASE_5_EPILOGUE,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_5_EPILOGUE)
                        .orElseThrow());
    }
}

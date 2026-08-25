package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class StormseekerQuestStepMapperTest {

    @Test
    void phasesBeforeThreeDoNotProduceGateSteps() {
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_1_THE_MARK)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_2_THE_TREK)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_3_THE_WAKING)
                .isEmpty());
        assertTrue(StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_4_THE_TRIALS)
                .isEmpty());
    }

    @Test
    void phaseThreeAndBeyondProduceCanonicalSteps() {
        assertEquals(
                StormseekerQuestSteps.PHASE_5_THE_FRAME,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_5_THE_FRAME)
                        .orElseThrow());

        assertEquals(
                StormseekerQuestSteps.PHASE_6_THE_FORGING,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.PHASE_6_THE_FORGING)
                        .orElseThrow());

        assertEquals(
                StormseekerQuestSteps.COMPLETE,
                StormseekerQuestStepMapper.stepFor(StormseekerPhase.COMPLETE).orElseThrow());
    }
}

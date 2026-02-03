package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StormseekerPhase1LoopTest {

    @Test
    void tick_enforcesEligibility_thenTicksOnlyEligibleParticipants() {
        StormseekerAttunementService attunement = new StormseekerAttunementService();
        StormseekerPhase1Loop loop = new StormseekerPhase1Loop(attunement);

        StormseekerProgress eligible = new StormseekerProgress();
        eligible.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);

        StormseekerProgress ineligible = new StormseekerProgress();
        ineligible.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);
        ineligible.grantSigilA();

        RecordingHost host = new RecordingHost(eligible, ineligible);

        loop.tick(host);

        // Only p1 should have participated and received hints.
        assertTrue(host.hintsForP1 > 0);
        assertEquals(0, host.hintsForP2);

        // And attunement should not retain p2 as a participant.
        assertFalse(attunement.isParticipatingForTesting("p2"));
    }

    private static final class RecordingHost implements StormseekerHostRuntime {
        private final StormseekerProgress p1;
        private final StormseekerProgress p2;

        int hintsForP1 = 0;
        int hintsForP2 = 0;

        RecordingHost(StormseekerProgress p1, StormseekerProgress p2) {
            this.p1 = p1;
            this.p2 = p2;
        }

        @Override
        public Iterable<String> playerIds() {
            return List.of("p1", "p2");
        }

        @Override
        public MotionSample motionSample(String playerId) {
            return new MotionSample(1, 0, 0, true);
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            return "p1".equals(playerId) ? p1 : p2;
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            if ("p1".equals(playerId)) {
                hintsForP1++;
            } else if ("p2".equals(playerId)) {
                hintsForP2++;
            }
        }
    }
}

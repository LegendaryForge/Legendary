package io.github.legendaryforge.legendarycontent.stormseeker;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import org.junit.jupiter.api.Test;

public class AnchoredTrialHarnessTest {

    @Test
    void engineTickSeam_reachesAnchoredSteps_andEmitsSigilBMilestone() {
        RecordingStormseekerHostRuntime host = new RecordingStormseekerHostRuntime();
        String playerId = "test-player";

        StormseekerProgress progress = host.progress(playerId);

        // Phase 2 anchored requires explicit entry; phase alone is insufficient.
        assertTrue(StormseekerWiring.enterAnchoredTrial(playerId, progress), "Expected enterAnchoredTrial to succeed");

        for (int i = 0; i < 120; i++) {
            StormseekerWiring.tick(host);
        }

        assertFalse(host.anchoredStepsView().isEmpty(), "Expected anchored step callbacks");
        assertTrue(
                host.milestonesView().stream().anyMatch(m -> m.contains("SIGIL_B")),
                "Expected a Sigil B milestone, got: " + host.milestonesView());
    }
}

package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnchoredTrialHostTickRemovePlayerTest {

    @Test
    void removePlayerClearsSessionAndSigilBEdgeTracking() {
        var progress = new StormseekerProgress();
        var host = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new AnchoredTrialHostTick();

        // Grant B and tick -> emits once.
        progress.grantSigilB();
        tick.tick(host);
        assertEquals(1, host.outcomes.size());
        assertEquals(
                StormseekerPhaseMilestone.SIGIL_B_GRANTED, host.outcomes.get(0).milestone());

        // Clear host-side tracking for the player.
        assertTrue(tick.removePlayer("p1"));

        // Ticking again should re-emit because the edge tracking was cleared.
        // (Progress already has Sigil B, so host tick observes the edge as "new" again after removal.)
        tick.tick(host);
        assertEquals(2, host.outcomes.size());
        assertEquals(
                StormseekerPhaseMilestone.SIGIL_B_GRANTED, host.outcomes.get(1).milestone());
    }

    @Test
    void removePlayerReturnsFalseWhenPlayerWasNotTracked() {
        var tick = new AnchoredTrialHostTick();
        assertFalse(tick.removePlayer("p1"));
    }
}

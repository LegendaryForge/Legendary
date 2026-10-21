package io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import org.junit.jupiter.api.Test;

public final class AnchoredLodestoneIssuerTest {

    @Test
    void grantsStandingLodestone_idempotently() {
        var playerId = "p1";
        var progress = new StormseekerProgress();

        var issuer = new AnchoredLodestoneIssuer();

        assertFalse(progress.hasStandingLodestone());
        assertTrue(issuer.tryGrantStandingLodestone(playerId, progress));
        assertTrue(progress.hasStandingLodestone());

        // Second call should not newly grant.
        assertFalse(issuer.tryGrantStandingLodestone(playerId, progress));
        assertTrue(progress.hasStandingLodestone());
    }
}

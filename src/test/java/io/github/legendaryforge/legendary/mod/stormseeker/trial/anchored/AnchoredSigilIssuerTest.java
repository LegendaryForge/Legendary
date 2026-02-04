package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import org.junit.jupiter.api.Test;

public final class AnchoredSigilIssuerTest {

    @Test
    void grantsSigilB_idempotently() {
        var playerId = "p1";
        var progress = new StormseekerProgress();

        var issuer = new AnchoredSigilIssuer();

        assertFalse(progress.hasSigilB());
        assertTrue(issuer.tryGrantSigilB(playerId, progress));
        assertTrue(progress.hasSigilB());

        // Second call should not newly grant.
        assertFalse(issuer.tryGrantSigilB(playerId, progress));
        assertTrue(progress.hasSigilB());
    }
}

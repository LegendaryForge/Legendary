package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.mod.runtime.OutcomeRecordingHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class AnchoredSigilIssuerTest {

    @Test
    void emitsDualSigilsGrantedWhenSigilAAlreadyGrantedOnSigilBGrant() {
        var playerId = "p1";
        var progress = new StormseekerProgress();
        var runtime = new OutcomeRecordingHostRuntime(List.of(playerId), progress);

        // Precondition: Sigil A already granted
        progress.grantSigilA();

        var issuer = new AnchoredSigilIssuer();
        var newlyGranted = issuer.tryGrantSigilB(runtime, playerId, progress);
        assertTrue(newlyGranted);

        assertEquals(2, runtime.outcomes.size());
        assertEquals(
                StormseekerPhaseMilestone.SIGIL_B_GRANTED,
                runtime.outcomes.get(0).milestone());
        assertEquals(
                StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED,
                runtime.outcomes.get(1).milestone());
    }
}

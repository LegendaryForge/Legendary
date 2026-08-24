package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class AnchoredTrialHostTickTest {

    @Test
    void emitsSigilBGrantedExactlyOnceOnSigilBGrant() {
        var progress = new StormseekerProgress();
        var host = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new AnchoredTrialHostTick();

        // No emission before B is granted
        tick.tick(host);
        assertEquals(0, host.outcomes.size());

        // Grant B and tick -> emit once
        progress.grantSigilB();
        tick.tick(host);
        assertEquals(1, host.outcomes.size());
        assertEquals(
                StormseekerPhaseMilestone.SIGIL_B_GRANTED, host.outcomes.get(0).milestone());

        // Additional ticks must not re-emit
        tick.tick(host);
        tick.tick(host);
        assertEquals(1, host.outcomes.size());
    }
}

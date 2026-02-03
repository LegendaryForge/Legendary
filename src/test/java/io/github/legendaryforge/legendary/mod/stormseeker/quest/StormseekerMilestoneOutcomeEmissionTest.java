package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostTick;
import io.github.legendaryforge.legendary.mod.runtime.OutcomeRecordingHostRuntime;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class StormseekerMilestoneOutcomeEmissionTest {

    @Test
    void emitsPhase1OutcomeExactlyOnceOnSigilAGrant() {
        var progress = new StormseekerProgress();
        var host = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new FlowingTrialHostTick();

        // Tick until Sigil A is granted
        for (int i = 0; i < 500 && !progress.hasSigilA(); i++) {
            tick.tick(host);
        }

        assertEquals(
                true, progress.hasSigilA(), "Test precondition: Sigil A should have been granted within tick budget");

        assertEquals(1, host.outcomes.size(), "Phase 1 outcome should emit once on Sigil A grant");

        // Additional ticks must not emit again
        tick.tick(host);
        tick.tick(host);

        assertEquals(1, host.outcomes.size(), "Phase 1 outcome must not re-emit on subsequent ticks");
    }
}

package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class AnchoredTrialHostTickTest {

    @Test
    void emitsSigilBOnce() {
        var progress = new StormseekerProgress();
        var runtime = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new AnchoredTrialHostTick();

        for (int i = 0; i < 500 && !progress.hasSigilB(); i++) {
            tick.tick(runtime);
        }

        assertEquals(
                List.of(StormseekerPhaseMilestone.SIGIL_B_GRANTED),
                runtime.outcomes.stream()
                        .map(StormseekerMilestoneOutcome::milestone)
                        .toList());
    }

    @Test
    void emitsDualSigilsWhenAAlreadyGranted() {
        var progress = new StormseekerProgress();
        progress.grantSigilA();

        var runtime = new OutcomeRecordingHostRuntime(List.of("p1"), progress);
        var tick = new AnchoredTrialHostTick();

        for (int i = 0; i < 500 && !progress.hasSigilB(); i++) {
            tick.tick(runtime);
        }

        assertEquals(
                List.of(StormseekerPhaseMilestone.SIGIL_B_GRANTED, StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED),
                runtime.outcomes.stream()
                        .map(StormseekerMilestoneOutcome::milestone)
                        .toList());
    }
}

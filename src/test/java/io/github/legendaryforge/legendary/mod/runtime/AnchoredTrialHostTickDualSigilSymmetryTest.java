package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class AnchoredTrialHostTickDualSigilSymmetryTest {

    @Test
    void emitsDualSigilsGrantedWhenSigilAIsGrantedAfterSigilB() {
        var progress = new StormseekerProgress();
        var host = new RecordingHostRuntime("p1", progress);
        var tick = new AnchoredTrialHostTick();

        // First, grant B and tick: should emit SIGIL_B_GRANTED only.
        progress.grantSigilB();
        tick.tick(host);

        assertEquals(1, host.outcomes.size());
        assertEquals(
                StormseekerPhaseMilestone.SIGIL_B_GRANTED, host.outcomes.get(0).milestone());

        // Now grant A after B and tick: should emit DUAL_SIGILS_GRANTED exactly once.
        progress.grantSigilA();
        tick.tick(host);

        assertTrue(
                host.outcomes.stream().anyMatch(o -> o.milestone() == StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED),
                "expected DUAL_SIGILS_GRANTED after A is granted post-B");

        int dualCount = (int) host.outcomes.stream()
                .filter(o -> o.milestone() == StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED)
                .count();
        assertEquals(1, dualCount, "dual milestone must be durable (emit once)");

        // Additional ticks must not re-emit dual.
        tick.tick(host);
        dualCount = (int) host.outcomes.stream()
                .filter(o -> o.milestone() == StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED)
                .count();
        assertEquals(1, dualCount, "dual milestone must not re-emit");
    }

    private static final class RecordingHostRuntime implements StormseekerHostRuntime {
        private final String playerId;
        private final StormseekerProgress progress;
        private final List<StormseekerMilestoneOutcome> outcomes = new ArrayList<>();

        private RecordingHostRuntime(String playerId, StormseekerProgress progress) {
            this.playerId = playerId;
            this.progress = progress;
        }

        @Override
        public Iterable<String> playerIds() {
            return List.of(playerId);
        }

        @Override
        public MotionSample motionSample(String playerId) {
            return new MotionSample(0.0, 0.0, 0.0, false);
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            if (!this.playerId.equals(playerId)) {
                throw new IllegalArgumentException("Unknown playerId: " + playerId);
            }
            return progress;
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {}

        @Override
        public void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {
            outcomes.add(outcome);
        }
    }
}

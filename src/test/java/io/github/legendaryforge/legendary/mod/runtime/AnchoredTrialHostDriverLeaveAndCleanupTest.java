package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialParticipation;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnchoredTrialHostDriverLeaveAndCleanupTest {

    @Test
    void leaveAndCleanupStopsTickingAndResetsHostTickSessionState() {
        var progressByPlayer = new HashMap<String, StormseekerProgress>();
        progressByPlayer.put("p1", new StormseekerProgress());

        var host = new RecordingHostRuntime(List.of("p1"), progressByPlayer);

        var participation = new AnchoredTrialParticipation();
        participation.enter("p1");

        var tick = new AnchoredTrialHostTick();
        var driver = new AnchoredTrialHostDriver(tick);

        // Two ticks while participating -> streak increments.
        driver.tick(host, participation);
        driver.tick(host, participation);

        assertEquals(2, host.steps.size());
        assertEquals(1, host.steps.get(0).step().stationaryStreak());
        assertEquals(2, host.steps.get(1).step().stationaryStreak());

        // Explicit leave + cleanup
        driver.leaveAndCleanup(host, participation, "p1");

        // No callbacks after leaving.
        driver.tick(host, participation);
        assertEquals(2, host.steps.size());

        // Re-enter and tick: session should be reset (new session starts streak from 1).
        participation.enter("p1");
        driver.tick(host, participation);

        assertEquals(3, host.steps.size());
        assertEquals(1, host.steps.get(2).step().stationaryStreak(), "expected reset after leaveAndCleanup");
    }

    private static final class RecordingHostRuntime implements StormseekerHostRuntime {
        private final List<String> allPlayerIds;
        private final Map<String, StormseekerProgress> progressByPlayer;

        final List<StepCall> steps = new ArrayList<>();

        private RecordingHostRuntime(List<String> allPlayerIds, Map<String, StormseekerProgress> progressByPlayer) {
            this.allPlayerIds = allPlayerIds;
            this.progressByPlayer = progressByPlayer;
        }

        @Override
        public Iterable<String> playerIds() {
            return allPlayerIds;
        }

        @Override
        public MotionSample motionSample(String playerId) {
            return new MotionSample(0.0, 0.0, 0.0, false);
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            StormseekerProgress p = progressByPlayer.get(playerId);
            if (p == null) {
                throw new IllegalArgumentException("Unknown playerId: " + playerId);
            }
            return p;
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            // not used by anchored host tick; required by interface
        }

        @Override
        public void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {
            steps.add(new StepCall(playerId, step));
        }
    }

    private record StepCall(String playerId, AnchoredTrialSessionStep step) {}
}

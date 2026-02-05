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

final class AnchoredTrialParticipationLifecycleInvariantTest {

    @Test
    void participationMembershipGatesTickingButDoesNotResetSessionState() {
        var progressByPlayer = new HashMap<String, StormseekerProgress>();
        progressByPlayer.put("p1", new StormseekerProgress());
        progressByPlayer.put("p2", new StormseekerProgress());

        var host = new RecordingHostRuntime(List.of("p1", "p2"), progressByPlayer);

        var participation = new AnchoredTrialParticipation();
        var tick = new AnchoredTrialHostTick();
        var driver = new AnchoredTrialHostDriver(tick);

        // Enter: p1 participates and receives steps.
        participation.enter("p1");
        driver.tick(host, participation);
        driver.tick(host, participation);

        assertEquals(2, host.steps.size(), "expected two step callbacks for p1");
        assertEquals("p1", host.steps.get(0).playerId());
        assertEquals(1, host.steps.get(0).step().stationaryStreak());
        assertEquals("p1", host.steps.get(1).playerId());
        assertEquals(2, host.steps.get(1).step().stationaryStreak());

        // Leave: p1 should no longer be ticked at all.
        participation.leave("p1");
        driver.tick(host, participation);
        assertEquals(2, host.steps.size(), "expected no additional callbacks after leaving participation");

        // Re-enter: p1 is ticked again, and session state should NOT have been implicitly reset.
        participation.enter("p1");
        driver.tick(host, participation);

        assertEquals(3, host.steps.size(), "expected a callback after re-entering participation");
        assertEquals("p1", host.steps.get(2).playerId());
        assertEquals(
                3,
                host.steps.get(2).step().stationaryStreak(),
                "expected stationary streak to continue across leave/re-enter without removePlayer");
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
            // Stationary each tick so streak deterministically increments.
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

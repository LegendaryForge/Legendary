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

final class AnchoredTrialHostDriverEmitsOneStepPerTickPerParticipantTest {

    @Test
    void emitsExactlyOneStepPerTickPerParticipatingPlayer() {
        var progressByPlayer = new HashMap<String, StormseekerProgress>();
        progressByPlayer.put("p1", new StormseekerProgress());
        progressByPlayer.put("p2", new StormseekerProgress());
        progressByPlayer.put("p3", new StormseekerProgress());

        var host = new RecordingHostRuntime(List.of("p1", "p2", "p3"), progressByPlayer);

        var participation = new AnchoredTrialParticipation();
        var tick = new AnchoredTrialHostTick();
        var driver = new AnchoredTrialHostDriver(tick);

        // Only p1 and p2 participate.
        participation.enter("p1");
        participation.enter("p2");

        // One driver tick => exactly one callback per participating player.
        driver.tick(host, participation);
        assertEquals(2, host.steps.size(), "expected exactly one callback per participating player");
        assertEquals("p1", host.steps.get(0).playerId());
        assertEquals(1, host.steps.get(0).step().stationaryStreak());
        assertEquals("p2", host.steps.get(1).playerId());
        assertEquals(1, host.steps.get(1).step().stationaryStreak());

        // Second driver tick => exactly one additional callback per participating player (no duplication within a
        // tick).
        driver.tick(host, participation);
        assertEquals(
                4,
                host.steps.size(),
                "expected exactly one additional callback per participating player on the next tick");
        assertEquals("p1", host.steps.get(2).playerId());
        assertEquals(2, host.steps.get(2).step().stationaryStreak());
        assertEquals("p2", host.steps.get(3).playerId());
        assertEquals(2, host.steps.get(3).step().stationaryStreak());

        // Non-participant (p3) should never receive callbacks even if present in playerIds().
        assertFalse(
                host.steps.stream().anyMatch(s -> s.playerId().equals("p3")),
                "expected no callbacks for non-participant");
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

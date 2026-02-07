package io.github.legendaryforge.legendary.mod.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialParticipation;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class FlowingTrialHostDriverEmitsOneHintPerTickPerParticipantTest {

    @Test
    void emitsExactlyOneFlowHintPerTickPerParticipatingPlayer() {
        var progressByPlayer = new HashMap<String, StormseekerProgress>();
        progressByPlayer.put("p1", new StormseekerProgress());
        progressByPlayer.put("p2", new StormseekerProgress());
        progressByPlayer.put("p3", new StormseekerProgress());

        var host = new RecordingHostRuntime(List.of("p1", "p2", "p3"), progressByPlayer);

        var participation = new FlowingTrialParticipation();
        var tick = new FlowingTrialHostTick();
        var driver = new FlowingTrialHostDriver(tick);

        // Only p1 and p2 participate.
        participation.enter("p1");
        participation.enter("p2");

        // One driver tick => exactly one hint per participating player.
        driver.tick(host, participation);
        assertEquals(2, host.hints.size(), "expected exactly one hint per participating player");
        assertEquals("p1", host.hints.get(0).playerId());
        assertEquals("p2", host.hints.get(1).playerId());

        // Second driver tick => exactly one additional hint per participating player (no duplication within a tick).
        driver.tick(host, participation);
        assertEquals(
                4, host.hints.size(), "expected exactly one additional hint per participating player on the next tick");
        assertEquals("p1", host.hints.get(2).playerId());
        assertEquals("p2", host.hints.get(3).playerId());

        // Non-participant (p3) should never receive hints even if present in playerIds().
        assertFalse(
                host.hints.stream().anyMatch(h -> h.playerId().equals("p3")), "expected no hints for non-participant");
    }

    private static final class RecordingHostRuntime implements StormseekerHostRuntime {
        private final List<String> allPlayerIds;
        private final Map<String, StormseekerProgress> progressByPlayer;

        final List<HintCall> hints = new ArrayList<>();

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
            // Stable sample; exact values are not important for this guardrail.
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
            hints.add(new HintCall(playerId, hint));
        }
    }

    private record HintCall(String playerId, FlowHintIntent hint) {}
}

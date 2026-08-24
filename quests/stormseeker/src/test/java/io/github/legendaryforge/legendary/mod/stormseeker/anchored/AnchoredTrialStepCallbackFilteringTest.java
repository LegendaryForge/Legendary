package io.github.legendaryforge.legendary.mod.stormseeker.anchored;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostDriver;
import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostTick;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
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

final class AnchoredTrialStepCallbackFilteringTest {

    @Test
    void stepCallbacksFireOnlyForParticipatingPlayers() {
        var progressByPlayer = new HashMap<String, StormseekerProgress>();
        progressByPlayer.put("p1", new StormseekerProgress());
        progressByPlayer.put("p2", new StormseekerProgress());

        var host = new RecordingHostRuntime(List.of("p1", "p2"), progressByPlayer);

        var participation = new AnchoredTrialParticipation();
        participation.enter("p1");

        var driver = new AnchoredTrialHostDriver(new AnchoredTrialHostTick());
        driver.tick(host, participation);

        assertTrue(host.calls.stream().anyMatch(s -> s.startsWith("p1:")), "expected step callback for p1");
        assertTrue(
                host.calls.stream().noneMatch(s -> s.startsWith("p2:")),
                "expected no step callback for non-participant p2");
    }

    static final class RecordingHostRuntime implements StormseekerHostRuntime {
        final List<String> calls = new ArrayList<>();
        final List<String> hints = new ArrayList<>();

        private final List<String> playerIds;
        private final Map<String, StormseekerProgress> progressByPlayer;

        RecordingHostRuntime(List<String> playerIds, Map<String, StormseekerProgress> progressByPlayer) {
            this.playerIds = playerIds;
            this.progressByPlayer = progressByPlayer;
        }

        @Override
        public Iterable<String> playerIds() {
            return playerIds;
        }

        @Override
        public MotionSample motionSample(String playerId) {
            // Still motion so the session can step deterministically without relying on movement.
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
            hints.add(playerId + ":" + hint);
        }

        @Override
        public void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {
            calls.add(playerId + ":" + step);
        }
    }
}

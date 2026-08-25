package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StormseekerFlowingTrialLoopTest {

    @Test
    void tickReturnsViewsWithObjectiveSnapshotsForAllPlayers() {
        StormseekerFlowingTrialLoop loop = new StormseekerFlowingTrialLoop();

        // p0: an early-phase player (The Mark). UNTOUCHED would correctly yield no objectives.
        StormseekerProgress p0 = new StormseekerProgress();
        p0.advanceToNextOrThrow(StormseekerPhase.PHASE_1_THE_MARK);

        // p1: The Trials (Flowing Trial)
        StormseekerProgress p1 = new StormseekerProgress();
        p1.advanceToNextOrThrow(StormseekerPhase.PHASE_1_THE_MARK);
        p1.advanceToNextOrThrow(StormseekerPhase.PHASE_2_THE_TREK);
        p1.advanceToNextOrThrow(StormseekerPhase.PHASE_3_THE_WAKING);
        p1.advanceToNextOrThrow(StormseekerPhase.PHASE_4_THE_TRIALS);

        Map<String, StormseekerProgress> progress = new HashMap<>();
        progress.put("p0", p0);
        progress.put("p1", p1);

        List<StormseekerFlowingTrialTickView> emitted = new ArrayList<>();

        var host = new io.github.legendaryforge.legendary.quests.stormseeker.runtime.StormseekerHostRuntime() {
            @Override
            public Iterable<String> playerIds() {
                return List.of("p0", "p1");
            }

            @Override
            public MotionSample motionSample(String playerId) {
                return new MotionSample(1, 0, 0, true);
            }

            @Override
            public StormseekerProgress progress(String playerId) {
                return progress.get(playerId);
            }

            @Override
            public void emitFlowHint(String playerId, FlowHintIntent hint) {}

            @Override
            public void emitFlowingTrialTickView(StormseekerFlowingTrialTickView view) {
                emitted.add(view);
            }
        };

        List<StormseekerFlowingTrialTickView> views = loop.tick(host);
        assertEquals(2, views.size());

        StormseekerFlowingTrialTickView v0 = views.stream()
                .filter(v -> v.playerId().equals("p0"))
                .findFirst()
                .orElseThrow();
        StormseekerFlowingTrialTickView v1 = views.stream()
                .filter(v -> v.playerId().equals("p1"))
                .findFirst()
                .orElseThrow();

        // Both players should have objective snapshots.
        assertFalse(v0.objectives().isEmpty());
        assertFalse(v1.objectives().isEmpty());

        // Views should also be emitted to the host.
        assertEquals(2, emitted.size());
    }
}

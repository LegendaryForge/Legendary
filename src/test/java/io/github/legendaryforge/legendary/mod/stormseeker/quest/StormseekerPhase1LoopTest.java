package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StormseekerPhase1LoopTest {

    @Test
    void tickReturnsViewsWithObjectiveSnapshotsForAllPlayers() {
        StormseekerPhase1Loop loop = new StormseekerPhase1Loop();

        // p0: Phase 0
        StormseekerProgress p0 = new StormseekerProgress();

        // p1: Phase 1 (Storm Trek)
        StormseekerProgress p1 = new StormseekerProgress();
        p1.advanceToNextOrThrow(StormseekerPhase.PHASE_1_STORM_TREK);

        Map<String, StormseekerProgress> progress = new HashMap<>();
        progress.put("p0", p0);
        progress.put("p1", p1);

        List<StormseekerPhase1TickView> emitted = new ArrayList<>();

        var host = new io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime() {
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
            public void emitPhase1TickView(StormseekerPhase1TickView view) {
                emitted.add(view);
            }
        };

        List<StormseekerPhase1TickView> views = loop.tick(host);
        assertEquals(2, views.size());

        StormseekerPhase1TickView v0 = views.stream()
                .filter(v -> v.playerId().equals("p0"))
                .findFirst()
                .orElseThrow();
        StormseekerPhase1TickView v1 = views.stream()
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

package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1TickView;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only StormseekerHostRuntime that records observable outputs.
 *
 * <p>This runtime is intentionally minimal and will fail fast if
 * motion or progress are unexpectedly queried.
 */
public final class RecordingStormseekerHostRuntime implements StormseekerHostRuntime {

    private final Iterable<String> playerIds;

    public final Map<String, StormseekerPhase1TickView> lastTickViewByPlayer = new HashMap<>();
    public final Map<String, List<FlowHintIntent>> flowHintsByPlayer = new HashMap<>();

    public RecordingStormseekerHostRuntime(Iterable<String> playerIds) {
        this.playerIds = playerIds;
    }

    @Override
    public Iterable<String> playerIds() {
        return playerIds;
    }

    @Override
    public MotionSample motionSample(String playerId) {
        throw new UnsupportedOperationException("RecordingStormseekerHostRuntime does not provide MotionSample");
    }

    @Override
    public StormseekerProgress progress(String playerId) {
        throw new UnsupportedOperationException("RecordingStormseekerHostRuntime does not provide StormseekerProgress");
    }

    @Override
    public void emitFlowHint(String playerId, FlowHintIntent hint) {
        flowHintsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(hint);
    }

    @Override
    public void emitPhase1TickView(StormseekerPhase1TickView view) {
        lastTickViewByPlayer.put(view.playerId(), view);
    }

    @Override
    public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {
        // intentionally ignored
    }
}

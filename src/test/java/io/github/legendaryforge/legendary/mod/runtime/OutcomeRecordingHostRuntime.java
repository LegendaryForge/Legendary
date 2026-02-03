package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1Outcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.List;

public final class OutcomeRecordingHostRuntime implements StormseekerHostRuntime {

    private final Iterable<String> playerIds;
    private final StormseekerProgress progress;

    public final List<StormseekerPhase1Outcome> outcomes = new ArrayList<>();

    public OutcomeRecordingHostRuntime(Iterable<String> playerIds, StormseekerProgress progress) {
        this.playerIds = playerIds;
        this.progress = progress;
    }

    @Override
    public Iterable<String> playerIds() {
        return playerIds;
    }

    @Override
    public MotionSample motionSample(String playerId) {
        return MotionSample.moving(1, 0, 0);
    }

    @Override
    public StormseekerProgress progress(String playerId) {
        return progress;
    }

    @Override
    public void emitFlowHint(String playerId, FlowHintIntent hint) {}

    @Override
    public void emitPhase1Outcome(StormseekerPhase1Outcome outcome) {
        outcomes.add(outcome);
    }

    @Override
    public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {}
}

package io.github.legendaryforge.legendary.harness.stormseeker;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored.AnchoredTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RecordingStormseekerHostRuntime implements StormseekerHostRuntime {

    private final Map<String, StormseekerProgress> progressByPlayer = new ConcurrentHashMap<>();
    private final List<AnchoredTrialSessionStep> anchoredSteps = new ArrayList<>();
    private final List<String> milestones = new ArrayList<>();

    @Override
    public Iterable<String> playerIds() {
        return Collections.singleton("test-player");
    }

    @Override
    public MotionSample motionSample(String playerId) {
        // IMPORTANT: MotionSample boolean is `moving`. Stationary == moving=false.
        return new MotionSample(0.0, 0.0, 0.0, false);
    }

    @Override
    public StormseekerProgress progress(String playerId) {
        return progressByPlayer.computeIfAbsent(playerId, id -> {
            StormseekerProgress p = new StormseekerProgress();
            // Force into Phase 2 anchored trial scaffold.
            p.advanceIfEligible();
            p.advanceIfEligible();
            p.advanceIfEligible();
            return p;
        });
    }

    @Override
    public void emitFlowHint(String playerId, FlowHintIntent hint) {
        // no-op
    }

    @Override
    public void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {
        milestones.add(outcome.playerId() + ":" + outcome.milestone());
    }

    @Override
    public void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {
        anchoredSteps.add(step);
    }

    public List<AnchoredTrialSessionStep> anchoredStepsView() {
        return List.copyOf(anchoredSteps);
    }

    public List<String> milestonesView() {
        return List.copyOf(milestones);
    }
}

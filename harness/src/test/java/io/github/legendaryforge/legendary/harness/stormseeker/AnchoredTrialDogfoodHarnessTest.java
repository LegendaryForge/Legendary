package io.github.legendaryforge.legendary.harness.stormseeker;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.AnchoredTrialHostTick;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored.AnchoredTrialSession;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnchoredTrialDogfoodHarnessTest {

    @Test
    void anchoredTrialSessionGrantsSigilB_andHostTickEmitsMilestones() {
        String player = "p1";

        FakeHostRuntime runtime = new FakeHostRuntime();
        runtime.ensurePlayer(player);

        // Phase 2E gameplay loop is AnchoredTrialSession (stationary streak grants Sigil B).
        AnchoredTrialSession session = new AnchoredTrialSession(runtime.progress(player));

        for (int i = 0; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS + 5; i++) {
            session.step(runtime.motionSample(player));
            if (runtime.progress(player).hasSigilB()) {
                break;
            }
        }

        assertTrue(runtime.progress(player).hasSigilB(), "expected Sigil B to be granted");

        // Host observer emits durable milestones on the progress edge.
        AnchoredTrialHostTick hostTick = new AnchoredTrialHostTick();
        hostTick.tick(runtime);

        assertEquals(1, runtime.countMilestones(player, StormseekerPhaseMilestone.SIGIL_B_GRANTED));
        assertEquals(0, runtime.countMilestones(player, StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED));
    }

    @Test
    void emitsDualSigilsGrantedWhenSigilAAlreadyPresent() {
        String player = "p1";

        FakeHostRuntime runtime = new FakeHostRuntime();
        runtime.ensurePlayer(player);
        runtime.progress(player).grantSigilA();

        AnchoredTrialSession session = new AnchoredTrialSession(runtime.progress(player));
        for (int i = 0; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS + 5; i++) {
            session.step(runtime.motionSample(player));
            if (runtime.progress(player).hasSigilB()) {
                break;
            }
        }

        assertTrue(runtime.progress(player).hasSigilB(), "expected Sigil B to be granted");

        AnchoredTrialHostTick hostTick = new AnchoredTrialHostTick();
        hostTick.tick(runtime);

        assertEquals(1, runtime.countMilestones(player, StormseekerPhaseMilestone.SIGIL_B_GRANTED));
        assertEquals(1, runtime.countMilestones(player, StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED));
    }

    private static final class FakeHostRuntime implements StormseekerHostRuntime {

        private final Map<String, StormseekerProgress> progress = new HashMap<>();
        private final List<StormseekerMilestoneOutcome> milestones = new ArrayList<>();

        void ensurePlayer(String playerId) {
            progress.computeIfAbsent(playerId, id -> new StormseekerProgress());
        }

        int countMilestones(String playerId, StormseekerPhaseMilestone milestone) {
            int count = 0;
            for (var o : milestones) {
                if (o.playerId().equals(playerId) && o.milestone() == milestone) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public Iterable<String> playerIds() {
            return progress.keySet();
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            return progress.computeIfAbsent(playerId, id -> new StormseekerProgress());
        }

        @Override
        public MotionSample motionSample(String playerId) {
            // Stationary sample: moving=false.
            return new MotionSample(0.0, 0.0, 0.0, false);
        }

        @Override
        public void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {
            milestones.add(outcome);
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            // No-op for anchored dogfood.
        }

        @Override
        public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {
            // No-op for anchored dogfood.
        }
    }
}

package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.runtime.MotionSampleSource;
import io.github.legendaryforge.legendary.mod.runtime.PlayerRef;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerProgressStore;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class AnchoredTrialRuntimeOrchestratorTest {

    @Test
    void tick_loadsOrCreatesProgress_andSamplesMotion() {
        var player = new PlayerRef("p1");

        var store = new FakeStore();
        var motion = new FakeMotion();

        var orch = new AnchoredTrialRuntimeOrchestrator(store, motion);
        orch.tick(player);

        assertEquals("p1", store.lastLoadPlayer.id());
        assertNotNull(store.savedProgress);
        assertEquals("p1", store.savedPlayer.id());
        assertEquals("p1", motion.lastPlayer.id());
    }

    private static final class FakeStore implements StormseekerProgressStore {
        PlayerRef lastLoadPlayer;
        PlayerRef savedPlayer;
        StormseekerProgress savedProgress;

        @Override
        public Optional<StormseekerProgress> load(PlayerRef player) {
            this.lastLoadPlayer = player;
            return Optional.empty();
        }

        @Override
        public void save(PlayerRef player, StormseekerProgress progress) {
            this.savedPlayer = player;
            this.savedProgress = progress;
        }
    }

    private static final class FakeMotion implements MotionSampleSource {
        PlayerRef lastPlayer;

        @Override
        public MotionSample sampleFor(PlayerRef player) {
            this.lastPlayer = player;
            return new MotionSample(1, 0, 0, true);
        }
    }
}

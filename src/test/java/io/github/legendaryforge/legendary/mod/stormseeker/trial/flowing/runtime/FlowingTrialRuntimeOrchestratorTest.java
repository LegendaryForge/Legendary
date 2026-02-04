package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.runtime.ActivePlayerProvider;
import io.github.legendaryforge.legendary.mod.runtime.FlowHintSink;
import io.github.legendaryforge.legendary.mod.runtime.LegendaryTickContext;
import io.github.legendaryforge.legendary.mod.runtime.MotionSampleSource;
import io.github.legendaryforge.legendary.mod.runtime.PlayerRef;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerProgressStore;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialStatus;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class FlowingTrialRuntimeOrchestratorTest {

    @Test
    void coherentMovementEventuallyGrantsSigilA_andEmitsHints_andIsIdempotent() {
        PlayerRef p = new PlayerRef("p1");

        InMemoryProgressStore store = new InMemoryProgressStore();
        store.put(p, new StormseekerProgress());

        CapturingHintSink hintSink = new CapturingHintSink();

        ActivePlayerProvider players = () -> List.of(p);

        // Always "moving" in a consistent direction.
        MotionSampleSource motion = player -> new MotionSample(1, 0, 0, true);

        FlowingTrialRuntimeOrchestrator orch = new FlowingTrialRuntimeOrchestrator(players, motion, store, hintSink);

        // Run ticks until completion/grant should happen.
        boolean granted = false;
        for (int i = 0; i < 2000; i++) {
            orch.tick(new LegendaryTickContext(i));
            granted = store.load(p).orElseThrow().hasSigilA();
            if (granted) {
                break;
            }
        }

        assertTrue(granted, "should grant Sigil A after sustained coherence");
        assertFalse(hintSink.events.isEmpty(), "should emit hints during evaluation");

        int savesAfterGrant = store.saveCount;

        // Keep ticking: should not re-grant / re-save repeatedly.
        for (int i = 2000; i < 2100; i++) {
            orch.tick(new LegendaryTickContext(i));
        }

        assertTrue(store.load(p).orElseThrow().hasSigilA());
        assertEquals(savesAfterGrant, store.saveCount, "grant must be idempotent and not spam saves");

        // Sanity: at least one emitted hint should have non-zero intensity at some point.
        assertTrue(
                hintSink.events.stream().anyMatch(e -> e.hint().intensity() != 0.0),
                "should produce a non-zero hint intensity at some point");
    }

    private static final class InMemoryProgressStore implements StormseekerProgressStore {
        private StormseekerProgress progress;
        private PlayerRef owner;
        private int saveCount;

        void put(PlayerRef player, StormseekerProgress progress) {
            this.owner = player;
            this.progress = progress;
        }

        @Override
        public Optional<StormseekerProgress> load(PlayerRef player) {
            if (owner == null || !owner.equals(player)) {
                return Optional.empty();
            }
            return Optional.of(progress);
        }

        @Override
        public void save(PlayerRef player, StormseekerProgress progress) {
            assertEquals(owner, player);
            this.progress = progress;
            saveCount++;
        }
    }

    private static final class CapturingHintSink implements FlowHintSink {

        private final List<Event> events = new ArrayList<>();

        @Override
        public void emit(PlayerRef player, FlowHintIntent hint, FlowingTrialStatus status) {
            events.add(new Event(player, hint, status));
        }

        private record Event(PlayerRef player, FlowHintIntent hint, FlowingTrialStatus status) {}
    }
}

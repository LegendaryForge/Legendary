package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialSession;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StormseekerAnchoredTrialServiceLeaveDoesNotCleanupTest {

    @Test
    void leaveAnchoredTrialDoesNotResetAnchoredSessionState() {
        var progress = new StormseekerProgress();
        var host = new SinglePlayerHostRuntime("p1", progress);
        var service = new StormseekerAnchoredTrialService();

        assertTrue(service.enterAnchoredTrial("p1", progress));

        // Advance close to the grant threshold (session + stationary streak should exist in host tick state).
        for (int i = 1; i < AnchoredTrialSession.REQUIRED_STATIONARY_TICKS; i++) {
            service.tick(host);
            assertFalse(progress.hasSigilB(), "should not grant before threshold");
        }

        // Default leave is membership-only: it must NOT cleanup/reset host tick session state.
        assertTrue(service.leaveAnchoredTrial("p1"));
        assertFalse(service.isParticipatingForTesting("p1"), "expected participation cleared");

        // Re-enter and tick once: if leave did NOT cleanup, we should grant immediately.
        assertTrue(service.enterAnchoredTrial("p1", progress));
        service.tick(host);
        assertTrue(progress.hasSigilB(), "leave should NOT reset stationary streak (expect immediate grant)");
    }

    private static final class SinglePlayerHostRuntime implements StormseekerHostRuntime {
        private final String playerId;
        private final StormseekerProgress progress;

        SinglePlayerHostRuntime(String playerId, StormseekerProgress progress) {
            this.playerId = playerId;
            this.progress = progress;
        }

        @Override
        public Iterable<String> playerIds() {
            return List.of(playerId);
        }

        @Override
        public MotionSample motionSample(String playerId) {
            // Always still.
            return new MotionSample(0.0, 0.0, 0.0, false);
        }

        @Override
        public StormseekerProgress progress(String playerId) {
            if (!this.playerId.equals(playerId)) {
                throw new IllegalArgumentException("Unknown playerId: " + playerId);
            }
            return progress;
        }

        @Override
        public void emitFlowHint(String playerId, FlowHintIntent hint) {
            // Not used for anchored trial.
        }
    }
}

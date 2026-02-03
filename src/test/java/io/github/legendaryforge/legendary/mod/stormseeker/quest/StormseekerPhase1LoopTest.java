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
    void tickReturnsHostFacingViewsAndOnlyParticipatingPlayersReceiveHints() {
        StormseekerPhase1Loop loop = new StormseekerPhase1Loop();

        // p0: Phase 0 (denied)
        StormseekerProgress p0 = new StormseekerProgress();

        // p1: Phase 1 (eligible, no Sigil A yet)
        StormseekerProgress p1 = new StormseekerProgress();
        p1.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);

        Map<String, StormseekerProgress> progress = new HashMap<>();
        progress.put("p0", p0);
        progress.put("p1", p1);

        List<String> hintRecipients = new ArrayList<>();

        var host = new io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime() {
            @Override
            public Iterable<String> playerIds() {
                return List.of("p0", "p1");
            }

            @Override
            public MotionSample motionSample(String playerId) {
                // Provide a moving sample; eligible participants should cause hint emission.
                return new MotionSample(1.0, 0.0, 0.0, true);
            }

            @Override
            public StormseekerProgress progress(String playerId) {
                return progress.get(playerId);
            }

            @Override
            public void emitFlowHint(String playerId, FlowHintIntent hint) {
                hintRecipients.add(playerId);
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

        assertEquals(StormseekerAttunementService.DENY_ENTER_NOT_IN_PHASE_1, v0.denyEnterReason());
        assertFalse(v0.participating());
        assertFalse(v0.objectives().isEmpty());

        assertNull(v1.denyEnterReason());
        assertTrue(v1.participating());
        assertFalse(v1.objectives().isEmpty());

        // Only participating player should receive hint emissions.
        assertTrue(hintRecipients.contains("p1"));
        assertFalse(hintRecipients.contains("p0"));
    }
}

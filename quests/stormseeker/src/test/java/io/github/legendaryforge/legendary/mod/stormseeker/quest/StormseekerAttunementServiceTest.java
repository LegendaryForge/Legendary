package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class StormseekerAttunementServiceTest {

    /** Minimal no-op EventBus for decoupled testing. */
    private static final EventBus NOOP_EVENT_BUS = new EventBus() {
        @Override
        public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
            return () -> {};
        }

        @Override
        public void post(Event event) {}
    };

    /** Minimal no-op World for decoupled testing. */
    private static final StormseekerAttunementService.World NOOP_WORLD = new StormseekerAttunementService.World() {
        @Override
        public boolean isPlayerOnPlate(String playerId, UUID plateId) {
            return true;
        }

        @Override
        public void applyRootEffect(String playerId) {}

        @Override
        public void removeRootEffect(String playerId) {}

        @Override
        public void updatePlateVisuals(UUID plateId, float intensity, StormseekerAttunementService.RitualState state) {}

        @Override
        public void updatePlateAudio(UUID plateId, float pitch, float volume) {}

        @Override
        public void setPlateToIdle(UUID plateId) {}
    };

    @Test
    void startRitualAndTickDoNotThrow() {
        StormseekerAttunementService s = new StormseekerAttunementService(NOOP_EVENT_BUS, NOOP_WORLD);
        UUID plateId = UUID.randomUUID();

        s.startRitual(plateId, "p1");
        s.tick();
    }

    @Test
    void constructorRejectsNullArgs() {
        assertThrows(NullPointerException.class, () -> new StormseekerAttunementService(null, NOOP_WORLD));
        // world is nullable (no-ops until host provides a real World implementation)
        assertDoesNotThrow(() -> new StormseekerAttunementService(NOOP_EVENT_BUS, null));
    }
}

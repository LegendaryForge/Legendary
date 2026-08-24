package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ArenaRevocationControllerTest {

    @Test
    void revokeMarksTrackerAndEmitsEventForLegendaryInstances() {
        EventBus bus = new SimpleEventBus();
        ArenaRevocationTracker tracker = new ArenaRevocationTracker();

        AtomicBoolean seen = new AtomicBoolean(false);
        Subscription sub = bus.subscribe(ParticipationRevokedEvent.class, e -> seen.set(true));

        ArenaRevocationController controller = new ArenaRevocationController(tracker, bus, id -> true);

        UUID instanceId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        controller.revoke(instanceId, playerId, new ResourceId("legendary", "rule_violation"));

        assertTrue(tracker.isRevoked(instanceId, playerId), "tracker should mark revoked");
        assertTrue(seen.get(), "should emit ParticipationRevokedEvent");

        sub.unsubscribe();
    }

    @Test
    void revokeNoopsForNonLegendaryInstances() {
        EventBus bus = new SimpleEventBus();
        ArenaRevocationTracker tracker = new ArenaRevocationTracker();

        AtomicBoolean seen = new AtomicBoolean(false);
        Subscription sub = bus.subscribe(ParticipationRevokedEvent.class, e -> seen.set(true));

        ArenaRevocationController controller = new ArenaRevocationController(tracker, bus, id -> false);

        UUID instanceId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        controller.revoke(instanceId, playerId, new ResourceId("legendary", "rule_violation"));

        assertFalse(tracker.isRevoked(instanceId, playerId), "tracker should not mark revoked");
        assertFalse(seen.get(), "should not emit ParticipationRevokedEvent");

        sub.unsubscribe();
    }
}

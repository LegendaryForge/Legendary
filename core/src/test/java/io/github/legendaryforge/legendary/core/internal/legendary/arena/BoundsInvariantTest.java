package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ArenaBoundsViolatedEvent;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class BoundsInvariantTest {

    @Test
    void emitsRevokeOnlyWhenActiveAndOnlyOnce() {
        EventBus bus = new SimpleEventBus();
        BoundsInvariant inv = new BoundsInvariant(bus);

        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000011");

        List<ParticipationRevokedEvent> revoked = new ArrayList<>();
        bus.subscribe(ParticipationRevokedEvent.class, revoked::add);

        // Not active yet -> no revoke
        bus.post(ArenaBoundsViolatedEvent.withoutPosition(instanceId, playerId));
        assertEquals(0, revoked.size());

        inv.onStart(instanceId);

        // First violation while active -> revoke once
        bus.post(ArenaBoundsViolatedEvent.withoutPosition(instanceId, playerId));
        assertEquals(1, revoked.size());

        // Duplicate violations -> no additional revoke
        bus.post(ArenaBoundsViolatedEvent.withoutPosition(instanceId, playerId));
        assertEquals(1, revoked.size());

        inv.onEnd(instanceId);

        // Ended -> no revoke
        bus.post(ArenaBoundsViolatedEvent.withoutPosition(instanceId, playerId));
        assertEquals(1, revoked.size());

        inv.onCleanup(instanceId);
        inv.onStart(instanceId);

        // After cleanup, dedup resets
        bus.post(ArenaBoundsViolatedEvent.withoutPosition(instanceId, playerId));
        assertEquals(2, revoked.size());
    }
}

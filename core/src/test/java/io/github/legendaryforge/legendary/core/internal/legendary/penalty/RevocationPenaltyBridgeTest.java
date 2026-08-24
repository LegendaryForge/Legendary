package io.github.legendaryforge.legendary.core.internal.legendary.penalty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RevocationPenaltyBridgeTest {

    @Test
    void suggestsPenaltyForOutOfBoundsRevocation() {
        EventBus bus = new SimpleEventBus();
        new RevocationPenaltyBridge(bus);

        List<LegendaryPenaltySuggestedEvent> suggested = new ArrayList<>();
        bus.subscribe(LegendaryPenaltySuggestedEvent.class, suggested::add);

        UUID instanceId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ResourceId reasonId = new ResourceId("legendary", "out_of_bounds");

        bus.post(new ParticipationRevokedEvent(instanceId, playerId, reasonId));

        assertEquals(1, suggested.size(), "should suggest exactly one penalty");
        LegendaryPenaltySuggestedEvent event = suggested.get(0);
        assertEquals(instanceId, event.instanceId());
        assertEquals(playerId, event.playerId());
        assertEquals(reasonId, event.reasonId());
        assertEquals("out_of_bounds", event.suggestion().key());
        assertEquals(Duration.ofMinutes(5), event.suggestion().duration());
    }

    @Test
    void ignoresOtherRevocationReasons() {
        EventBus bus = new SimpleEventBus();
        new RevocationPenaltyBridge(bus);

        List<LegendaryPenaltySuggestedEvent> suggested = new ArrayList<>();
        bus.subscribe(LegendaryPenaltySuggestedEvent.class, suggested::add);

        bus.post(new ParticipationRevokedEvent(
                UUID.randomUUID(), UUID.randomUUID(), new ResourceId("legendary", "other")));

        assertTrue(suggested.isEmpty(), "should not suggest penalty for unrelated reasons");
    }
}

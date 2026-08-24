package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PhaseGateInvariantTest {

    @Test
    void tracksPhaseAndClearsOnCleanup() {
        SimpleEventBus bus = new SimpleEventBus();

        ResourceId defId = ResourceId.parse("test:encounter_def");
        UUID instanceId = UUID.randomUUID();

        PhaseGateInvariant phaseGate = new PhaseGateInvariant();
        ArenaInvariantRegistry registry = id -> List.of(phaseGate);
        ArenaInvariantBridge.bind(bus, registry);

        EncounterAnchor anchor =
                new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.empty());
        EncounterKey key = new EncounterKey(ResourceId.parse("test:encounter"), anchor);

        bus.post(new EncounterStartedEvent(key, instanceId, defId, anchor, UUID.randomUUID()));
        assertEquals(Optional.of(ArenaPhase.ACTIVE), phaseGate.phaseOf(instanceId));

        bus.post(new EncounterEndedEvent(key, instanceId, defId, anchor, EndReason.COMPLETED));
        assertEquals(Optional.of(ArenaPhase.ENDED), phaseGate.phaseOf(instanceId));

        bus.post(new EncounterCleanupEvent(instanceId));
        assertTrue(phaseGate.phaseOf(instanceId).isEmpty());
    }
}

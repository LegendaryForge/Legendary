package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

final class ArenaInvariantLegendaryOnlyGatingTest {

    @Test
    void phaseGateRunsOnlyForLegendaryDefinitionIds() {
        EventBus bus = new SimpleEventBus();

        Set<ResourceId> legendary = ConcurrentHashMap.newKeySet();
        PhaseGateInvariant phase = new PhaseGateInvariant();

        ArenaInvariantRegistry reg =
                defId -> legendary.contains(defId) ? java.util.List.of(phase) : java.util.List.of();

        ArenaInvariantBridge.bind(bus, reg);

        UUID normalInstance = UUID.randomUUID();
        UUID legendaryInstance = UUID.randomUUID();

        ResourceId normalDef = ResourceId.parse("test:normal_def");
        ResourceId legendaryDef = ResourceId.parse("test:legendary_def");
        legendary.add(legendaryDef);

        EncounterAnchor anchor =
                new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.empty());

        // Normal: should not track
        bus.post(new EncounterStartedEvent(
                new EncounterKey(normalDef, anchor), normalInstance, normalDef, anchor, UUID.randomUUID()));
        assertTrue(phase.phaseOf(normalInstance).isEmpty());

        // Legendary: should track start/end/cleanup
        bus.post(new EncounterStartedEvent(
                new EncounterKey(legendaryDef, anchor), legendaryInstance, legendaryDef, anchor, UUID.randomUUID()));
        assertEquals(ArenaPhase.ACTIVE, phase.phaseOf(legendaryInstance).orElseThrow());

        bus.post(new EncounterEndedEvent(
                new EncounterKey(legendaryDef, anchor), legendaryInstance, legendaryDef, anchor, EndReason.COMPLETED));
        assertEquals(ArenaPhase.ENDED, phase.phaseOf(legendaryInstance).orElseThrow());

        bus.post(new EncounterCleanupEvent(legendaryInstance));
        assertTrue(phase.phaseOf(legendaryInstance).isEmpty());
    }
}

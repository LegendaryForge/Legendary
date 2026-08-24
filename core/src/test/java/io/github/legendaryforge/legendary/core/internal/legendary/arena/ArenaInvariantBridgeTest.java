package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ArenaInvariantBridgeTest {

    @Test
    void forwardsStartEndCleanupOnceInOrder() {
        SimpleEventBus bus = new SimpleEventBus();

        ResourceId defId = ResourceId.parse("test:encounter_def");
        UUID instanceId = UUID.randomUUID();

        List<String> calls = new ArrayList<>();
        ArenaInvariant inv = new ArenaInvariant() {
            @Override
            public void onStart(UUID id) {
                calls.add("start:" + id);
            }

            @Override
            public void onEnd(UUID id) {
                calls.add("end:" + id);
            }

            @Override
            public void onCleanup(UUID id) {
                calls.add("cleanup:" + id);
            }
        };

        ArenaInvariantRegistry registry = id -> id.equals(defId) ? List.of(inv) : List.of();
        ArenaInvariantBridge.bind(bus, registry);

        EncounterAnchor anchor =
                new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.empty());
        EncounterKey key = new EncounterKey(ResourceId.parse("test:encounter"), anchor);

        bus.post(new EncounterStartedEvent(key, instanceId, defId, anchor, UUID.randomUUID()));
        bus.post(new EncounterEndedEvent(key, instanceId, defId, anchor, EndReason.COMPLETED));
        bus.post(new EncounterCleanupEvent(instanceId));

        assertEquals(List.of("start:" + instanceId, "end:" + instanceId, "cleanup:" + instanceId), calls);
    }
}

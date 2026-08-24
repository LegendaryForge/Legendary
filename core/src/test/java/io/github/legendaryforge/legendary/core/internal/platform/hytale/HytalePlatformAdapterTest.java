package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;
import io.github.legendaryforge.legendary.core.api.lifecycle.Lifecycle;
import io.github.legendaryforge.legendary.core.api.lifecycle.ServiceRegistry;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.api.registry.RegistryAccess;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class HytalePlatformAdapterTest {

    @Test
    void bindsSignalSourceToRuntime() {
        CoreRuntime runtime = new CoreRuntime() {
            @Override
            public RegistryAccess registries() {
                return null;
            }

            @Override
            public Lifecycle lifecycle() {
                return null;
            }

            @Override
            public ServiceRegistry services() {
                return null;
            }

            @Override
            public EventBus events() {
                return null;
            }

            @Override
            public EncounterManager encounters() {
                return null;
            }
        };

        AtomicReference<CoreRuntime> seen = new AtomicReference<>();
        HytaleSignalSource source = seen::set;

        new HytalePlatformAdapter(runtime, source);
        assertSame(runtime, seen.get());
    }

    @Test
    void forwardsPenaltySuggestionsToApplier() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();

        AtomicReference<LegendaryPenaltySuggestedEvent> seen = new AtomicReference<>();
        LegendaryPenaltyApplier applier = seen::set;

        new HytalePlatformAdapter(runtime, HytaleSignalSource.penalties(applier));

        UUID instanceId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ResourceId reasonId = new ResourceId("legendary", "out_of_bounds");

        runtime.events().post(new ParticipationRevokedEvent(instanceId, playerId, reasonId));

        LegendaryPenaltySuggestedEvent event = seen.get();
        assertNotNull(event, "expected adapter to observe a penalty suggestion");
        assertEquals(instanceId, event.instanceId());
        assertEquals(playerId, event.playerId());
        assertEquals(reasonId, event.reasonId());
        assertEquals("out_of_bounds", event.suggestion().key());
        assertEquals(Duration.ofMinutes(5), event.suggestion().duration());
    }
}

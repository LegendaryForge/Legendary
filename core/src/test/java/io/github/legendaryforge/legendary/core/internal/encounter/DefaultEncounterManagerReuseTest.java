package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.encounter.policy.EncounterJoinPolicy;
import io.github.legendaryforge.legendary.core.internal.encounter.policy.EncounterReusePolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class DefaultEncounterManagerReuseTest {

    private static final class RecordingEventBus implements EventBus {
        private final List<Event> events = new CopyOnWriteArrayList<>();

        @Override
        public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
            throw new UnsupportedOperationException("subscribe not needed for this test");
        }

        @Override
        public void post(Event event) {
            events.add(event);
        }

        List<Event> events() {
            return events;
        }
    }

    private static final EncounterJoinPolicy ALWAYS_ALLOW_JOIN = (playerId, def, ctx, role, players, parties) ->
            io.github.legendaryforge.legendary.core.api.encounter.JoinResult.SUCCESS;

    private static final EncounterReusePolicy ALWAYS_REUSE = (key, existing) -> true;

    @Test
    void create_reusesExistingInstance_whenPolicyAllows_andInstanceNotEnded() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(
                ALWAYS_ALLOW_JOIN, ALWAYS_REUSE, Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance a = mgr.create(def(), ctx());
        assertEquals(1, count(bus.events(), EncounterCreatedEvent.class));

        EncounterInstance b = mgr.create(def(), ctx());

        assertSame(a, b);
        assertEquals(1, count(bus.events(), EncounterCreatedEvent.class));
        assertEquals(1, count(bus.events(), EncounterReusedEvent.class));
    }

    @Test
    void create_doesNotReuse_whenExistingEnded_evenIfPolicyWouldReuse() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(
                ALWAYS_ALLOW_JOIN, ALWAYS_REUSE, Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance a = mgr.create(def(), ctx());
        mgr.end(a, EndReason.COMPLETED);

        EncounterInstance b = mgr.create(def(), ctx());

        assertNotEquals(a.instanceId(), b.instanceId());
        assertTrue(count(bus.events(), EncounterCreatedEvent.class) >= 2);
        // Reuse should not occur across ENDED boundary.
        assertEquals(0, count(bus.events(), EncounterReusedEvent.class));
    }

    private static int count(List<Event> events, Class<?> type) {
        int n = 0;
        for (Event e : events) {
            if (type.isInstance(e)) {
                n++;
            }
        }
        return n;
    }

    private static EncounterDefinition def() {
        return new EncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:encounter");
            }

            @Override
            public String displayName() {
                return "test";
            }

            @Override
            public EncounterAccessPolicy accessPolicy() {
                return EncounterAccessPolicy.PUBLIC;
            }

            @Override
            public SpectatorPolicy spectatorPolicy() {
                return SpectatorPolicy.ALLOW_VIEW_ONLY;
            }

            @Override
            public int maxParticipants() {
                return 2;
            }

            @Override
            public int maxSpectators() {
                return 2;
            }
        };
    }

    private static EncounterContext ctx() {
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new EncounterContext() {
            @Override
            public EncounterAnchor anchor() {
                return new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.of(party));
            }

            @Override
            public Map<String, Object> metadata() {
                return Map.of();
            }
        };
    }
}

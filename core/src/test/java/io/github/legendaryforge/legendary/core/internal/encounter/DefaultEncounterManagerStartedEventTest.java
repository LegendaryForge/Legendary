package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DefaultEncounterManagerStartedEventTest {

    private static final class RecordingEventBus implements EventBus {
        private final List<Event> events = new java.util.concurrent.CopyOnWriteArrayList<>();

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

    private static final EncounterDefinition DEF = def();
    private static final EncounterContext CTX = context();

    @Test
    void startedEmittedOnceOnFirstParticipantJoin_only() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance instance = mgr.create(DEF, CTX);

        assertEquals(1, bus.events().size());
        assertTrue(bus.events().get(0) instanceof EncounterCreatedEvent);

        UUID s1 = UUID.randomUUID();
        mgr.join(s1, instance, ParticipationRole.SPECTATOR);

        // Spectator-only join should not start.
        assertEquals(1, bus.events().size());

        UUID p1 = UUID.randomUUID();
        mgr.join(p1, instance, ParticipationRole.PARTICIPANT);

        assertEquals(2, bus.events().size());
        assertTrue(bus.events().get(1) instanceof EncounterStartedEvent);

        UUID p2 = UUID.randomUUID();
        mgr.join(p2, instance, ParticipationRole.PARTICIPANT);

        // Second participant join must not emit Started again.
        assertEquals(2, bus.events().size());
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

    private static EncounterContext context() {
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

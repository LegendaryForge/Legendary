package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterState;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
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
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class DefaultEncounterManagerLeaveSemanticsTest {

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

    @Test
    void leave_removesPlayerFromParticipantsAndSpectators_withoutChangingStateOrEmittingEvents() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance inst = mgr.create(def(), ctx());

        UUID p1 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        mgr.join(p1, inst, ParticipationRole.PARTICIPANT);
        mgr.join(s1, inst, ParticipationRole.SPECTATOR);

        assertEquals(EncounterState.RUNNING, inst.state());
        assertTrue(inst.participants().contains(p1));
        assertTrue(inst.spectators().contains(s1));

        int startedBeforeLeave = count(bus.events(), EncounterStartedEvent.class);
        int endedBeforeLeave = count(bus.events(), EncounterEndedEvent.class);

        mgr.leave(p1, inst);
        mgr.leave(s1, inst);

        assertFalse(inst.participants().contains(p1));
        assertFalse(inst.spectators().contains(s1));
        assertEquals(EncounterState.RUNNING, inst.state());

        assertEquals(startedBeforeLeave, count(bus.events(), EncounterStartedEvent.class));
        assertEquals(endedBeforeLeave, count(bus.events(), EncounterEndedEvent.class));
    }

    @Test
    void leave_isIdempotent() {
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.empty());

        EncounterInstance inst = mgr.create(def(), ctx());
        UUID p1 = UUID.randomUUID();

        mgr.join(p1, inst, ParticipationRole.PARTICIPANT);
        assertTrue(inst.participants().contains(p1));

        mgr.leave(p1, inst);
        mgr.leave(p1, inst);

        assertFalse(inst.participants().contains(p1));
        assertEquals(EncounterState.RUNNING, inst.state());
    }

    @Test
    void leave_afterEnd_doesNotReopenOrEmitAdditionalEndedEvents() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance inst = mgr.create(def(), ctx());
        UUID p1 = UUID.randomUUID();
        mgr.join(p1, inst, ParticipationRole.PARTICIPANT);

        mgr.end(inst, EndReason.COMPLETED);

        int endedBeforeLeave = count(bus.events(), EncounterEndedEvent.class);
        assertEquals(EncounterState.ENDED, inst.state());

        mgr.leave(p1, inst);

        assertEquals(EncounterState.ENDED, inst.state());
        assertEquals(endedBeforeLeave, count(bus.events(), EncounterEndedEvent.class));
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

package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterDurationMeasuredEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.encounter.DefaultEncounterManager;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class EncounterDurationTelemetryTest {

    private static final class RecordingEventBus implements EventBus {

        private static final class ListenerEntry<E extends Event> {
            private final Class<E> type;
            private final EventListener<E> listener;

            private ListenerEntry(Class<E> type, EventListener<E> listener) {
                this.type = type;
                this.listener = listener;
            }
        }

        private final List<Event> posted = new CopyOnWriteArrayList<>();
        private final List<ListenerEntry<?>> listeners = new CopyOnWriteArrayList<>();

        @Override
        public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
            ListenerEntry<E> entry = new ListenerEntry<>(type, listener);
            listeners.add(entry);
            return () -> listeners.remove(entry);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void post(Event event) {
            posted.add(event);
            for (ListenerEntry<?> e : listeners) {
                if (e.type.isInstance(event)) {
                    ((EventListener) e.listener).onEvent(event);
                }
            }
        }

        List<Event> posted() {
            return posted;
        }
    }

    @Test
    void durationMeasuredEmittedAfterStartedThenEnded() {
        RecordingEventBus bus = new RecordingEventBus();

        MutableClock clock = new MutableClock(1_000L, ZoneId.of("UTC"));

        EncounterDurationTelemetry telemetry = new EncounterDurationTelemetry(bus, clock);
        bus.subscribe(EncounterStartedEvent.class, telemetry::onStarted);
        bus.subscribe(EncounterEndedEvent.class, telemetry::onEnded);

        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));
        EncounterInstance inst = mgr.create(def(), ctx());

        UUID p = UUID.fromString("00000000-0000-0000-0000-0000000000AA");
        mgr.join(p, inst, ParticipationRole.PARTICIPANT);
        clock.advanceMillis(1_500L);
        mgr.end(inst, EndReason.COMPLETED);

        boolean found = false;
        for (Event e : bus.posted()) {
            if (e instanceof EncounterDurationMeasuredEvent dm) {
                found = true;
                assertEquals(EndReason.COMPLETED, dm.reason());
                assertEquals(1_500L, dm.durationMillis());
            }
        }
        assertTrue(found);
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

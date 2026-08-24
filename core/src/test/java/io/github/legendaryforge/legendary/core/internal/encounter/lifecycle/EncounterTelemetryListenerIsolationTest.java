package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class EncounterTelemetryListenerIsolationTest {

    private static final class RecordingEventBus implements EventBus {

        private static final class ListenerEntry<E extends Event> {
            private final Class<E> type;
            private final EventListener<E> listener;

            private ListenerEntry(Class<E> type, EventListener<E> listener) {
                this.type = type;
                this.listener = listener;
            }
        }

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
            for (ListenerEntry<?> e : listeners) {
                if (e.type.isInstance(event)) {
                    ((EventListener) e.listener).onEvent(event);
                }
            }
        }
    }

    @Test
    void oneListenerThrowingDoesNotPreventOthersFromRunning() {
        RecordingEventBus bus = new RecordingEventBus();

        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-0000000000AA");
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-0000000000BB");
        EncounterStartedEvent started = new EncounterStartedEvent(
                new EncounterKey(
                        ResourceId.parse("test:encounter"),
                        new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.empty())),
                instanceId,
                ResourceId.parse("test:encounter"),
                new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.empty()),
                playerId);

        class Throwing implements EncounterTelemetryListener {
            @Override
            public void onCreated(
                    io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent event) {}

            @Override
            public void onReused(
                    io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent event) {}

            @Override
            public void onStarted(EncounterStartedEvent event) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onEnded(
                    io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent event) {}
        }

        final int[] ran = {0};
        class Counter implements EncounterTelemetryListener {
            @Override
            public void onCreated(
                    io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent event) {}

            @Override
            public void onReused(
                    io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent event) {}

            @Override
            public void onStarted(EncounterStartedEvent event) {
                ran[0]++;
            }

            @Override
            public void onEnded(
                    io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent event) {}
        }

        EncounterTelemetryListener.bind(Optional.of(bus), new Throwing(), new Counter());

        bus.post(started);

        assertEquals(1, ran[0]);
    }
}

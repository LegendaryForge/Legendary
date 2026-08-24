package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class DefaultEncounterManagerEndedEventTest {

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
    void endedEmittedOnce_whenEndCalledTwice() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance inst = mgr.create(def(), ctx());
        mgr.join(UUID.randomUUID(), inst, ParticipationRole.PARTICIPANT);

        mgr.end(inst, EndReason.COMPLETED);
        mgr.end(inst, EndReason.COMPLETED);

        assertEquals(1, countEnded(bus.events()));
    }

    @Test
    void endedEmittedOnce_underConcurrentEndCalls() throws Exception {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));

        EncounterInstance inst = mgr.create(def(), ctx());
        mgr.join(UUID.randomUUID(), inst, ParticipationRole.PARTICIPANT);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        var f1 = pool.submit(() -> {
            ready.countDown();
            start.await();
            mgr.end(inst, EndReason.COMPLETED);
            return null;
        });
        var f2 = pool.submit(() -> {
            ready.countDown();
            start.await();
            mgr.end(inst, EndReason.COMPLETED);
            return null;
        });

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(1, countEnded(bus.events()));
    }

    private static int countEnded(List<Event> events) {
        int ended = 0;
        for (Event e : events) {
            if (e instanceof EncounterEndedEvent) {
                ended++;
            }
        }
        return ended;
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

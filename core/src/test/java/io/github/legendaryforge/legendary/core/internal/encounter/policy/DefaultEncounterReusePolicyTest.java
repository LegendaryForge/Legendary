package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.encounter.DefaultEncounterManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class DefaultEncounterReusePolicyTest {

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
    void defaultPolicy_doesNotReuse() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.of(bus));
        EncounterInstance a = mgr.create(DEF, CTX);
        EncounterInstance b = mgr.create(DEF, CTX);
        assertNotEquals(a.instanceId(), b.instanceId());
        List<Event> events = bus.events();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof EncounterCreatedEvent);
        assertTrue(events.get(1) instanceof EncounterCreatedEvent);
    }

    @Test
    void reuseActivePolicy_reusesWhileActive_andCreatesNewAfterEnd() {
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(
                new DefaultEncounterJoinPolicy(),
                new ReuseActiveEncounterPolicy(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(bus));

        EncounterInstance a = mgr.create(DEF, CTX);
        EncounterInstance b = mgr.create(DEF, CTX);
        assertSame(a, b);
        List<Event> events = bus.events();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof EncounterCreatedEvent);
        assertTrue(events.get(1) instanceof EncounterReusedEvent);

        mgr.end(a, io.github.legendaryforge.legendary.core.api.encounter.EndReason.COMPLETED);
        EncounterInstance c = mgr.create(DEF, CTX);
        assertNotEquals(a.instanceId(), c.instanceId());
        assertEquals(5, events.size());
        assertTrue(events.get(2) instanceof EncounterEndedEvent);
        assertTrue(events.get(3) instanceof EncounterCleanupEvent);
        assertTrue(events.get(4) instanceof EncounterCreatedEvent);
        EncounterEndedEvent ended = (EncounterEndedEvent) events.get(2);
        assertEquals(EndReason.COMPLETED, ended.reason());
    }

    @Test
    void reuseActivePolicy_isAtomic_underConcurrency() throws Exception {
        int n = 20;
        RecordingEventBus bus = new RecordingEventBus();
        DefaultEncounterManager mgr = new DefaultEncounterManager(
                new DefaultEncounterJoinPolicy(),
                new ReuseActiveEncounterPolicy(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(bus));

        ExecutorService exec = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EncounterInstance>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            futures.add(exec.submit(() -> {
                ready.countDown();
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return mgr.create(DEF, CTX);
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        EncounterInstance first = futures.get(0).get(5, TimeUnit.SECONDS);
        for (int i = 1; i < n; i++) {
            EncounterInstance next = futures.get(i).get(5, TimeUnit.SECONDS);
            assertSame(first, next);
        }

        exec.shutdown();
        assertTrue(exec.awaitTermination(5, TimeUnit.SECONDS));

        int created = 0;
        int reused = 0;
        for (Event e : bus.events()) {
            if (e instanceof EncounterCreatedEvent) {
                created++;
            } else if (e instanceof EncounterReusedEvent) {
                reused++;
            }
        }
        assertEquals(1, created);
        assertEquals(n - 1, reused);
        assertEquals(n, bus.events().size());
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
                return 0;
            }

            @Override
            public int maxSpectators() {
                return 0;
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
                return Map.of("role", ParticipationRole.PARTICIPANT.name(), "state", EncounterState.CREATED.name());
            }
        };
    }

    @Test
    void reuseActivePolicy_isAtomic_underConcurrentCreate() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        DefaultEncounterManager mgr = new DefaultEncounterManager(
                new DefaultEncounterJoinPolicy(),
                new ReuseActiveEncounterPolicy(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        List<Future<EncounterInstance>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return mgr.create(DEF, CTX);
            }));
        }

        // Ensure all threads are ready, then release simultaneously
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        EncounterInstance first = futures.get(0).get(5, TimeUnit.SECONDS);
        for (Future<EncounterInstance> f : futures) {
            assertSame(first, f.get(5, TimeUnit.SECONDS));
        }

        pool.shutdownNow();
    }
}

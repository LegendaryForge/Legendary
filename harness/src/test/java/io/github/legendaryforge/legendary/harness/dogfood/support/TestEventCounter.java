package io.github.legendaryforge.legendary.harness.dogfood.support;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestEventCounter {

    private final AtomicInteger startedTotal = new AtomicInteger();
    private final AtomicInteger endedTotal = new AtomicInteger();

    private final Map<UUID, AtomicInteger> startedByInstance = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> endedByInstance = new ConcurrentHashMap<>();

    public TestEventCounter(EventBus bus) {
        bus.subscribe(EncounterStartedEvent.class, this::onStarted);
        bus.subscribe(EncounterEndedEvent.class, this::onEnded);
    }

    private void onStarted(EncounterStartedEvent e) {
        startedTotal.incrementAndGet();
        startedByInstance
                .computeIfAbsent(e.instanceId(), __ -> new AtomicInteger())
                .incrementAndGet();
    }

    private void onEnded(EncounterEndedEvent e) {
        endedTotal.incrementAndGet();
        endedByInstance
                .computeIfAbsent(e.instanceId(), __ -> new AtomicInteger())
                .incrementAndGet();
    }

    public int startedTotal() {
        return startedTotal.get();
    }

    public int endedTotal() {
        return endedTotal.get();
    }

    public int startedFor(UUID instanceId) {
        AtomicInteger v = startedByInstance.get(instanceId);
        return v == null ? 0 : v.get();
    }

    public int endedFor(UUID instanceId) {
        AtomicInteger v = endedByInstance.get(instanceId);
        return v == null ? 0 : v.get();
    }
}

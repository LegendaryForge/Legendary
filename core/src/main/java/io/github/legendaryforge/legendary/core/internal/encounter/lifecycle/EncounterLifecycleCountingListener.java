package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import java.util.concurrent.atomic.AtomicInteger;

public final class EncounterLifecycleCountingListener implements EncounterLifecycleListener {

    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger reused = new AtomicInteger();
    private final AtomicInteger ended = new AtomicInteger();

    @Override
    public void onCreated(EncounterCreatedEvent event) {
        created.incrementAndGet();
    }

    @Override
    public void onReused(EncounterReusedEvent event) {
        reused.incrementAndGet();
    }

    @Override
    public void onEnded(EncounterEndedEvent event) {
        ended.incrementAndGet();
    }

    public int createdCount() {
        return created.get();
    }

    public int reusedCount() {
        return reused.get();
    }

    public int endedCount() {
        return ended.get();
    }
}

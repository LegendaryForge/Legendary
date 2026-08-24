package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import java.util.Objects;
import java.util.function.Consumer;

public final class EncounterLifecycleLoggingListener implements EncounterLifecycleListener {

    private final Consumer<String> sink;

    public EncounterLifecycleLoggingListener(Consumer<String> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void onCreated(EncounterCreatedEvent event) {
        sink.accept("EncounterCreatedEvent: " + event);
    }

    @Override
    public void onReused(EncounterReusedEvent event) {
        sink.accept("EncounterReusedEvent: " + event);
    }

    @Override
    public void onEnded(EncounterEndedEvent event) {
        sink.accept("EncounterEndedEvent: " + event);
    }
}

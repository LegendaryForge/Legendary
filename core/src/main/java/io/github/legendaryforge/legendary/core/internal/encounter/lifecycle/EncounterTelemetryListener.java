package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public interface EncounterTelemetryListener {

    Logger LOG = Logger.getLogger(EncounterTelemetryListener.class.getName());

    void onCreated(EncounterCreatedEvent event);

    void onReused(EncounterReusedEvent event);

    void onStarted(EncounterStartedEvent event);

    void onEnded(EncounterEndedEvent event);

    static List<Subscription> bind(Optional<EventBus> events, EncounterTelemetryListener... listeners) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(listeners, "listeners");
        List<Subscription> subs = new ArrayList<>();
        events.ifPresent(bus -> {
            for (EncounterTelemetryListener l : listeners) {
                subs.add(bus.subscribe(EncounterCreatedEvent.class, e -> safeInvoke("onCreated", l, e, l::onCreated)));
                subs.add(bus.subscribe(EncounterReusedEvent.class, e -> safeInvoke("onReused", l, e, l::onReused)));
                subs.add(bus.subscribe(EncounterStartedEvent.class, e -> safeInvoke("onStarted", l, e, l::onStarted)));
                subs.add(bus.subscribe(EncounterEndedEvent.class, e -> safeInvoke("onEnded", l, e, l::onEnded)));
            }
        });
        return subs;
    }

    private static <E> void safeInvoke(String hook, EncounterTelemetryListener listener, E event, Consumer<E> invoke) {
        try {
            invoke.accept(event);
        } catch (Throwable t) {
            LOG.log(
                    Level.WARNING,
                    "EncounterTelemetryListener " + listener.getClass().getName() + " threw in " + hook,
                    t);
        }
    }
}

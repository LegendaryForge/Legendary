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

public final class EncounterLifecycleEventBridge {

    private EncounterLifecycleEventBridge() {}

    public static List<Subscription> bind(java.util.Optional<EventBus> events, EncounterLifecycleListener listener) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(listener, "listener");
        return events.map(b -> bind(b, listener)).orElseGet(java.util.List::of);
    }

    public static List<Subscription> bind(EventBus bus, EncounterLifecycleListener listener) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(listener, "listener");

        List<Subscription> subs = new ArrayList<>(4);

        subs.add(bus.subscribe(EncounterCreatedEvent.class, listener::onCreated));
        subs.add(bus.subscribe(EncounterReusedEvent.class, listener::onReused));
        subs.add(bus.subscribe(EncounterStartedEvent.class, listener::onStarted));
        subs.add(bus.subscribe(EncounterEndedEvent.class, listener::onEnded));

        return subs;
    }
}

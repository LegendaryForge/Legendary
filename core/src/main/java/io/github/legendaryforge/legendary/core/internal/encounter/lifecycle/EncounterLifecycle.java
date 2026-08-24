package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EncounterLifecycle {

    private EncounterLifecycle() {}

    public static List<Subscription> bind(Optional<EventBus> events, EncounterLifecycleListener... listeners) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(listeners, "listeners");
        EncounterLifecycleListener composite = EncounterLifecycleListeners.composite(listeners);
        return EncounterLifecycleEventBridge.bind(events, composite);
    }
}

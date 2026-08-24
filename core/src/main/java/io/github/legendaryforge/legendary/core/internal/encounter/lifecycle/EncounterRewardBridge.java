package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EncounterRewardBridge {

    private EncounterRewardBridge() {}

    /**
     * Bind one or more reward listeners to an optional EventBus.
     * Returns the list of subscriptions.
     */
    public static List<Subscription> bind(Optional<EventBus> events, EncounterRewardListener... listeners) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(listeners, "listeners");

        List<Subscription> subs = new ArrayList<>();
        for (EncounterRewardListener l : listeners) {
            if (events.isPresent()) {
                subs.add(events.get().subscribe(EncounterEndedEvent.class, l::onReward));
            }
        }
        return subs;
    }
}

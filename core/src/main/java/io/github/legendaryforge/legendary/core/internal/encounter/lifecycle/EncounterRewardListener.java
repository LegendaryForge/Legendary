package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import java.util.*;

@FunctionalInterface
public interface EncounterRewardListener {
    /** Called when an encounter ends. Implementations can award rewards to participants. */
    void onReward(EncounterEndedEvent event);

    static List<Subscription> bind(Optional<EventBus> events, EncounterRewardListener... listeners) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(listeners, "listeners");
        List<Subscription> subs = new ArrayList<>();
        events.ifPresent(bus -> {
            for (EncounterRewardListener l : listeners) {
                subs.add(bus.subscribe(EncounterEndedEvent.class, l::onReward));
            }
        });
        return subs;
    }
}

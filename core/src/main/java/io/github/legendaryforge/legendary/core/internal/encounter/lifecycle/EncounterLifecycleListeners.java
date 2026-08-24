package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCreatedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterReusedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import java.util.Arrays;
import java.util.Objects;

public final class EncounterLifecycleListeners {

    private EncounterLifecycleListeners() {}

    public static EncounterLifecycleListener composite(EncounterLifecycleListener... listeners) {
        Objects.requireNonNull(listeners, "listeners");

        EncounterLifecycleListener[] copy =
                Arrays.stream(listeners).filter(Objects::nonNull).toArray(EncounterLifecycleListener[]::new);

        if (copy.length == 0) {
            return new EncounterLifecycleListener() {
                @Override
                public void onCreated(EncounterCreatedEvent event) {}

                @Override
                public void onReused(EncounterReusedEvent event) {}

                @Override
                public void onStarted(EncounterStartedEvent event) {}

                @Override
                public void onEnded(EncounterEndedEvent event) {}
            };
        }
        if (copy.length == 1) {
            return copy[0];
        }

        return new EncounterLifecycleListener() {
            @Override
            public void onCreated(EncounterCreatedEvent event) {
                for (EncounterLifecycleListener l : copy) {
                    l.onCreated(event);
                }
            }

            @Override
            public void onReused(EncounterReusedEvent event) {
                for (EncounterLifecycleListener l : copy) {
                    l.onReused(event);
                }
            }

            @Override
            public void onStarted(EncounterStartedEvent event) {
                for (EncounterLifecycleListener l : copy) {
                    l.onStarted(event);
                }
            }

            @Override
            public void onEnded(EncounterEndedEvent event) {
                for (EncounterLifecycleListener l : copy) {
                    l.onEnded(event);
                }
            }
        };
    }
}

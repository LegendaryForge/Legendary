package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import java.util.Objects;

/**
 * Hytale platform signal binding seam.
 *
 * <p>Deliberately contains no Hytale imports. This is the integration point where a platform adapter
 * may subscribe to core events and forward them to platform-specific systems.
 */
@FunctionalInterface
public interface HytaleSignalSource {

    void bind(CoreRuntime runtime);

    static HytaleSignalSource noop() {
        return runtime -> {};
    }

    /**
     * Binds a listener for advisory Legendary penalty suggestions.
     */
    static HytaleSignalSource penalties(LegendaryPenaltyApplier applier) {
        Objects.requireNonNull(applier, "applier");
        return runtime -> {
            Objects.requireNonNull(runtime, "runtime");
            EventBus bus = Objects.requireNonNull(runtime.events(), "events");
            bus.subscribe(LegendaryPenaltySuggestedEvent.class, applier::apply);
        };
    }
}

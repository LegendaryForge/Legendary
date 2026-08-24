package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterCleanupEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import java.util.Objects;

/**
 * Content-side bridge wiring core cleanup eligibility into content scripts.
 *
 * <p>Core emits {@link EncounterCleanupEvent} exactly once per instance. This bridge forwards
 * that signal into scripts that opt into {@link EncounterCleanupHandler}.
 */
public final class ScriptCleanupBridge {

    private final EncounterCleanupHandler cleanup;

    public ScriptCleanupBridge(EventBus events, EncounterCleanupHandler cleanup) {
        Objects.requireNonNull(events, "events");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        events.subscribe(EncounterCleanupEvent.class, this::onCleanup);
    }

    private void onCleanup(EncounterCleanupEvent event) {
        cleanup.onCleanup(event.instanceId());
    }
}

package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import java.util.Objects;

/**
 * Content-side bridge wiring core encounter lifecycle events into an {@link EncounterScript}.
 */
public final class ScriptEventBridge {

    private final EncounterManager encounters;
    private final EncounterScript script;

    public ScriptEventBridge(EventBus events, EncounterManager encounters, EncounterScript script) {
        Objects.requireNonNull(events, "events");
        this.encounters = Objects.requireNonNull(encounters, "encounters");
        this.script = Objects.requireNonNull(script, "script");

        events.subscribe(EncounterStartedEvent.class, this::onStarted);
        events.subscribe(EncounterEndedEvent.class, this::onEnded);
    }

    private void onStarted(EncounterStartedEvent event) {
        encounters
                .byInstanceId(event.instanceId())
                .ifPresent(instance -> script.onStart(instance, event.triggeringPlayerId()));
    }

    private void onEnded(EncounterEndedEvent event) {
        encounters.byInstanceId(event.instanceId()).ifPresent(script::onEnd);
    }
}

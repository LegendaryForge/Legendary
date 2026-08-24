package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterDurationMeasuredEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Measures encounter duration between EncounterStartedEvent and EncounterEndedEvent and emits
 * EncounterDurationMeasuredEvent.
 *
 * <p>Wall-clock based and best-effort: if an encounter ends without a corresponding start,
 * no duration event is emitted.</p>
 */
public final class EncounterDurationTelemetry {

    private final EventBus bus;
    private final Clock clock;
    private final Map<UUID, Long> startedAtMillis = new ConcurrentHashMap<>();

    public EncounterDurationTelemetry(EventBus bus, Clock clock) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void onStarted(EncounterStartedEvent event) {
        Objects.requireNonNull(event, "event");
        startedAtMillis.putIfAbsent(event.instanceId(), clock.millis());
    }

    public void onEnded(EncounterEndedEvent event) {
        Objects.requireNonNull(event, "event");
        Long started = startedAtMillis.remove(event.instanceId());
        if (started == null) {
            return;
        }
        long duration = Math.max(0L, clock.millis() - started);
        bus.post(new EncounterDurationMeasuredEvent(
                event.key(), event.instanceId(), event.definitionId(), event.anchor(), event.reason(), duration));
    }
}

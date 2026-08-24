package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory penalty applier for adapter tests and reference integrations.
 *
 * <p>This does not enforce anything; it only records advisory suggestions.
 */
public final class RecordingLegendaryPenaltyApplier implements LegendaryPenaltyApplier {

    private final Map<UUID, LegendaryPenaltySuggestedEvent> lastByPlayer = new ConcurrentHashMap<>();
    private final List<LegendaryPenaltySuggestedEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void apply(LegendaryPenaltySuggestedEvent event) {
        Objects.requireNonNull(event, "event");
        lastByPlayer.put(event.playerId(), event);
        events.add(event);
    }

    /** Returns the last suggested penalty for a player, if any. */
    public Optional<LegendaryPenaltySuggestedEvent> lastFor(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(lastByPlayer.get(playerId));
    }

    /** Returns a snapshot of all received events. */
    public List<LegendaryPenaltySuggestedEvent> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}

package io.github.legendaryforge.legendary.core.internal.legendary.penalty;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestion;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.event.ParticipationRevokedEvent;
import java.time.Duration;
import java.util.Objects;

/**
 * Internal bridge from participation revocation signals to advisory Legendary penalty suggestions.
 *
 * <p>Core stays advisory: platforms may clamp, ignore, or implement penalties however they choose.
 */
public final class RevocationPenaltyBridge {

    private static final ResourceId OUT_OF_BOUNDS_REASON = new ResourceId("legendary", "out_of_bounds");
    private static final LegendaryPenaltySuggestion OUT_OF_BOUNDS_PENALTY =
            LegendaryPenaltySuggestion.of("out_of_bounds", Duration.ofMinutes(5));

    private final EventBus bus;

    public RevocationPenaltyBridge(EventBus bus) {
        this.bus = Objects.requireNonNull(bus, "bus");
        bus.subscribe(ParticipationRevokedEvent.class, this::onRevoked);
    }

    private void onRevoked(ParticipationRevokedEvent event) {
        Objects.requireNonNull(event, "event");

        if (OUT_OF_BOUNDS_REASON.equals(event.reasonId())) {
            bus.post(new LegendaryPenaltySuggestedEvent(
                    event.instanceId(), event.playerId(), event.reasonId(), OUT_OF_BOUNDS_PENALTY));
        }
    }
}

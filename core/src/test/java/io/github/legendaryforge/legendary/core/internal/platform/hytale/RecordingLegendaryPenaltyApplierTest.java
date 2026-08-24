package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;
import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestion;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class RecordingLegendaryPenaltyApplierTest {

    @Test
    void recordsLastEventPerPlayerAndAllEvents() {
        RecordingLegendaryPenaltyApplier applier = new RecordingLegendaryPenaltyApplier();

        UUID instanceA = UUID.randomUUID();
        UUID instanceB = UUID.randomUUID();
        UUID player = UUID.randomUUID();

        ResourceId reasonA = new ResourceId("legendary", "out_of_bounds");
        ResourceId reasonB = new ResourceId("legendary", "other");

        LegendaryPenaltySuggestedEvent first = new LegendaryPenaltySuggestedEvent(
                instanceA, player, reasonA, LegendaryPenaltySuggestion.of("out_of_bounds", Duration.ofMinutes(5)));

        LegendaryPenaltySuggestedEvent second = new LegendaryPenaltySuggestedEvent(
                instanceB, player, reasonB, LegendaryPenaltySuggestion.of("other", Duration.ofMinutes(1)));

        applier.apply(first);
        applier.apply(second);

        assertEquals(2, applier.events().size(), "should keep all events");
        assertTrue(applier.lastFor(player).isPresent(), "should have last event for player");
        assertEquals(second, applier.lastFor(player).get(), "last event should win");
    }
}

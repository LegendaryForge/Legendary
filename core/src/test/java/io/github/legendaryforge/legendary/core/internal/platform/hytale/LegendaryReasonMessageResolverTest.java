package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import org.junit.jupiter.api.Test;

final class LegendaryReasonMessageResolverTest {

    @Test
    void knownReasonResolvesToSpecificMessage() {
        String msg = LegendaryReasonMessageResolver.messageFor(new ResourceId("legendary", "out_of_bounds"));
        assertEquals("Participation revoked: left arena bounds.", msg);
    }

    @Test
    void unknownReasonFallsBackToGenericMessage() {
        String msg = LegendaryReasonMessageResolver.messageFor(new ResourceId("legendary", "unknown"));
        assertEquals("Participation revoked.", msg);
    }
}

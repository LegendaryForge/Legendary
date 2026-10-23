package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Earth and Wind are both {@code Geometric} and score by one predicate, so the thing that must
 * differ between them is what counts as present: rock for Earth, the exposed surface for Wind.
 * Encoding that as {@code Occupancy} keeps it one rule rather than an {@code elementId} branch.
 */
class ElementGroundingOccupancyTest {

    @Test
    void earthCountsSolidAndWindCountsTheExposedSurface() {
        ElementGrounding.Geometric earth =
                new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, ElementGrounding.Occupancy.SOLID);
        ElementGrounding.Geometric wind = new ElementGrounding.Geometric(
                "wind", Set.of("Mountains"), 12, 15, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID);
        assertEquals(ElementGrounding.Occupancy.SOLID, earth.occupancy());
        assertEquals(ElementGrounding.Occupancy.OPEN_ABOVE_SOLID, wind.occupancy());
    }

    @Test
    void occupancyIsRequired() {
        assertThrows(
                NullPointerException.class,
                () -> new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, null));
    }
}

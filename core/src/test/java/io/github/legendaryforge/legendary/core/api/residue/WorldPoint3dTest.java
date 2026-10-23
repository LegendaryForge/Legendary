package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldPoint3dTest {

    @Test
    void distanceIsEuclideanInThreeAxes() {
        WorldPoint3d a = new WorldPoint3d(0, 0, 0);
        WorldPoint3d b = new WorldPoint3d(3, 4, 12);
        assertEquals(13.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void horizontalDistanceIgnoresY() {
        WorldPoint3d low = new WorldPoint3d(0, 10, 0);
        WorldPoint3d high = new WorldPoint3d(3, 300, 4);
        assertEquals(5.0, low.horizontalDistanceTo(high), 1e-9);
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint3d(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint3d(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint3d(0, 0, Double.NEGATIVE_INFINITY));
    }
}

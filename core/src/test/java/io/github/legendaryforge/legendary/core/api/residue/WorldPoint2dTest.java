package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldPoint2dTest {

    @Test
    void distanceTo_isEuclidean() {
        WorldPoint2d a = new WorldPoint2d(0.0, 0.0);
        WorldPoint2d b = new WorldPoint2d(3.0, 4.0);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void distanceTo_isSymmetric() {
        WorldPoint2d a = new WorldPoint2d(-12.5, 7.25);
        WorldPoint2d b = new WorldPoint2d(4.0, -3.0);
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-9);
    }

    @Test
    void distanceTo_self_isZero() {
        WorldPoint2d a = new WorldPoint2d(101.0, -55.0);
        assertEquals(0.0, a.distanceTo(a), 1e-9);
    }

    @Test
    void rejects_nonFinite() {
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint2d(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint2d(0.0, Double.POSITIVE_INFINITY));
    }
}

package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import org.junit.jupiter.api.Test;

class SegmentMathTest {

    private static final WorldPoint2d ORIGIN = new WorldPoint2d(0.0, 0.0);
    private static final WorldPoint2d TEN_EAST = new WorldPoint2d(10.0, 0.0);

    @Test
    void projection_midpoint() {
        assertEquals(0.5, SegmentMath.projectionParameter(ORIGIN, TEN_EAST, 5.0, 3.0), 1e-9);
    }

    @Test
    void projection_clampsBeforeStart() {
        assertEquals(0.0, SegmentMath.projectionParameter(ORIGIN, TEN_EAST, -50.0, 0.0), 1e-9);
    }

    @Test
    void projection_clampsAfterEnd() {
        assertEquals(1.0, SegmentMath.projectionParameter(ORIGIN, TEN_EAST, 50.0, 0.0), 1e-9);
    }

    @Test
    void projection_degenerateSegment_returnsZero() {
        assertEquals(0.0, SegmentMath.projectionParameter(ORIGIN, ORIGIN, 5.0, 5.0), 1e-9);
    }

    @Test
    void pointAt_interpolates() {
        WorldPoint2d p = SegmentMath.pointAt(ORIGIN, TEN_EAST, 0.25);
        assertEquals(2.5, p.x(), 1e-9);
        assertEquals(0.0, p.z(), 1e-9);
    }

    @Test
    void intersection_crossingSegments() {
        WorldPoint2d hit = SegmentMath.intersection(
                new WorldPoint2d(-5.0, 0.0),
                new WorldPoint2d(5.0, 0.0),
                new WorldPoint2d(0.0, -5.0),
                new WorldPoint2d(0.0, 5.0));
        assertNotNull(hit);
        assertEquals(0.0, hit.x(), 1e-9);
        assertEquals(0.0, hit.z(), 1e-9);
    }

    @Test
    void intersection_nonCrossing_returnsNull() {
        assertNull(SegmentMath.intersection(
                new WorldPoint2d(0.0, 0.0),
                new WorldPoint2d(1.0, 0.0),
                new WorldPoint2d(0.0, 5.0),
                new WorldPoint2d(1.0, 5.0)));
    }

    @Test
    void intersection_parallel_returnsNull() {
        assertNull(SegmentMath.intersection(
                new WorldPoint2d(0.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(0.0, 1.0),
                new WorldPoint2d(10.0, 1.0)));
    }

    @Test
    void intersection_collinearTouching_returnsNull() {
        // Collinear segments; denominator is zero, so the parallel/degenerate guard catches this.
        assertNull(SegmentMath.intersection(
                new WorldPoint2d(0.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(20.0, 0.0)));
    }

    @Test
    void intersection_bendAtSharedEndpoint_returnsNull() {
        // Non-parallel, so the denominator is non-zero and the parallel guard does NOT fire.
        // The shared endpoint gives t == 1.0 exactly, which the epsilon branch must reject.
        // This is the case consecutive arm segments actually produce.
        assertNull(SegmentMath.intersection(
                new WorldPoint2d(0.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(10.0, 10.0)));
    }

    @Test
    void intersection_asymmetricCrossing_offOrigin() {
        // A: (0,0)->(10,0), B: (3,-2)->(7,6). Hand-computed: t = 0.4, u = 0.25, point = (4,0).
        // Asymmetric in both parameters, so a t/u swap or a sign error changes the result.
        WorldPoint2d hit = SegmentMath.intersection(
                new WorldPoint2d(0.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(3.0, -2.0),
                new WorldPoint2d(7.0, 6.0));
        assertNotNull(hit);
        assertEquals(4.0, hit.x(), 1e-9);
        assertEquals(0.0, hit.z(), 1e-9);
    }

    @Test
    void intersection_nonParallelNearMiss_returnsNull() {
        // Denominator is non-zero; the segments simply do not reach each other within [0,1].
        assertNull(SegmentMath.intersection(
                new WorldPoint2d(0.0, 0.0),
                new WorldPoint2d(10.0, 0.0),
                new WorldPoint2d(50.0, -5.0),
                new WorldPoint2d(50.0, 5.0)));
    }
}

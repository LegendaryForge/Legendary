package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.residue.DefaultResidueNetwork;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResidueNetworkTest {

    private static final CurrentParameters PARAMS = new CurrentParameters(4, 80, 16.0, 0.35, 24.0, 0.3);
    private static final long SEED = 20260827L;
    private static final ResourceId ELEMENT = ResourceId.of("test", "element");

    @Test
    void densityAt_convergence_isOrdinaryCurrent() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        // N6 reserved 1.0 for a nexus. Every arm starts at the convergence, and shared endpoints
        // are deliberately not counted as crossings, so the convergence is ordinary current.
        assertEquals(1.0 - PARAMS.nexusWeight(), n.densityAt(c.x(), c.z()), 1e-9);
    }

    @Test
    void densityAt_farAway_isZero() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        assertEquals(0.0, n.densityAt(c.x() + 100_000.0, c.z() + 100_000.0), 1e-9);
    }

    @Test
    void densityAt_isBounded() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        for (int i = -50; i <= 50; i++) {
            double d = n.densityAt(c.x() + i * 13.0, c.z() + i * 7.0);
            assertTrue(d >= 0.0 && d <= 1.0, "density out of range: " + d);
        }
    }

    @Test
    void densityAt_fallsOffWithDistance() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        double near = n.densityAt(c.x(), c.z());
        double mid = n.densityAt(c.x(), c.z() + PARAMS.influenceRadius() * 0.5);
        assertTrue(near > mid, "expected density to fall off: " + near + " vs " + mid);
    }

    @Test
    void flowAt_farAway_isEmpty() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        assertEquals(Optional.empty(), n.flowAt(c.x() + 100_000.0, c.z() + 100_000.0));
    }

    @Test
    void flowAt_onCurrent_isPresentAndUnit() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        Optional<FlowVector> flow = n.flowAt(c.x(), c.z());
        assertTrue(flow.isPresent());
        assertEquals(1.0, Math.hypot(flow.get().dx(), flow.get().dz()), 1e-9);
    }

    @Test
    void flowAt_pointsTowardConvergence() {
        // The whole navigation design rests on this: walking along the flow must reduce the
        // distance to the Grand Convergence. Reversed flow would silently invert the questline.
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        int checked = 0;
        // Sample a ring around the convergence; points that land within influence of an arm are
        // the ones that can be checked.
        for (double angle = 0.0; angle < 2 * Math.PI; angle += Math.PI / 8) {
            double px = c.x() + Math.cos(angle) * 40.0;
            double pz = c.z() + Math.sin(angle) * 40.0;
            Optional<FlowVector> flow = n.flowAt(px, pz);
            if (flow.isEmpty()) {
                continue;
            }
            double before = Math.hypot(px - c.x(), pz - c.z());
            double after = Math.hypot(
                    px + flow.get().dx() * 4.0 - c.x(), pz + flow.get().dz() * 4.0 - c.z());
            assertTrue(after < before, "flow moved away from convergence at angle " + angle);
            checked++;
        }
        assertTrue(checked > 0, "no on-current sample points found; widen the probe ring");
    }

    @Test
    void nexusesWithin_areInsideTheBounds() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        List<WorldPoint2d> circles = n.nexusesWithin(-5000.0, -5000.0, 5000.0, 5000.0);
        for (WorldPoint2d p : circles) {
            assertTrue(p.x() >= -5000.0 && p.x() <= 5000.0, "x out of bounds: " + p);
            assertTrue(p.z() >= -5000.0 && p.z() <= 5000.0, "z out of bounds: " + p);
        }
    }

    @Test
    void nexusesWithin_emptyRegion_isEmpty() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        assertTrue(n.nexusesWithin(500_000.0, 500_000.0, 510_000.0, 510_000.0).isEmpty());
    }

    @Test
    void nexusesWithin_rejectsInvertedBounds() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        assertThrows(IllegalArgumentException.class, () -> n.nexusesWithin(10.0, 0.0, 0.0, 10.0));
    }

    @Test
    void nexusesWithin_rejectsInvertedBoundsOnZ() {
        // The existing inverted-bounds test only inverts X, leaving the Z half of the guard unexercised.
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        assertThrows(IllegalArgumentException.class, () -> n.nexusesWithin(0.0, 10.0, 10.0, 0.0));
    }

    @Test
    void nexusesWithin_rejectsNonFiniteBounds() {
        // NaN slipped through the inverted-bounds guard and produced an empty list -- a silent
        // wrong answer, where every other type in this package rejects non-finite input.
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        assertThrows(IllegalArgumentException.class, () -> n.nexusesWithin(Double.NaN, -100.0, 100.0, 100.0));
        assertThrows(
                IllegalArgumentException.class, () -> n.nexusesWithin(-100.0, -100.0, Double.POSITIVE_INFINITY, 100.0));
    }

    @Test
    void nexusesWithin_filtersOnZIndependentlyOfX() {
        // Every other box in this suite is X/Z-symmetric, so swapping maxZ for maxX in the filter
        // changed no result. This box is deliberately asymmetric.
        for (long seed = 0; seed < 20; seed++) {
            ResidueNetwork n = new DefaultResidueNetwork(seed, ELEMENT, PARAMS);
            List<WorldPoint2d> all = n.nexusesWithin(-100_000.0, -100_000.0, 100_000.0, 100_000.0);
            if (all.isEmpty()) {
                continue;
            }
            WorldPoint2d c = all.get(0);
            // A band that contains c in X but excludes it in Z.
            List<WorldPoint2d> excludedByZ = n.nexusesWithin(c.x() - 1.0, c.z() + 10.0, c.x() + 1.0, c.z() + 20.0);
            assertTrue(excludedByZ.isEmpty(), "a crossing outside the Z band must be filtered out");
            // The same X band, with a Z band that does contain it.
            List<WorldPoint2d> includedByZ = n.nexusesWithin(c.x() - 1.0, c.z() - 1.0, c.x() + 1.0, c.z() + 1.0);
            assertTrue(includedByZ.contains(c), "a crossing inside both bands must be returned");
            return;
        }
        fail("no seed in 0..19 produced a crossing to filter");
    }

    @Test
    void nexusesWithin_findsCrossings_forSomeSeed() {
        // Guards against nexusesWithin returning empty unconditionally. Crossing counts are
        // seed-dependent and low at these parameters -- some seeds legitimately yield zero -- so
        // this asserts existence across a scan rather than a count at one seed, which would be
        // brittle against any geometry change.
        int seedsWithCrossings = 0;
        for (long seed = 0; seed < 20; seed++) {
            ResidueNetwork n = new DefaultResidueNetwork(seed, ELEMENT, PARAMS);
            if (!n.nexusesWithin(-100_000.0, -100_000.0, 100_000.0, 100_000.0).isEmpty()) {
                seedsWithCrossings++;
            }
        }
        assertTrue(seedsWithCrossings > 0, "no seed in 0..19 produced a crossing; crossing detection is dead");
    }

    @Test
    void nexusesWithin_crossingLiesOnTheNetwork() {
        // A crossing is by construction a point on two segments, so density there must be maximal.
        // This catches a nexusesWithin that returns points unrelated to the geometry.
        for (long seed = 0; seed < 20; seed++) {
            ResidueNetwork n = new DefaultResidueNetwork(seed, ELEMENT, PARAMS);
            List<WorldPoint2d> circles = n.nexusesWithin(-100_000.0, -100_000.0, 100_000.0, 100_000.0);
            if (!circles.isEmpty()) {
                WorldPoint2d c = circles.get(0);
                assertEquals(1.0, n.densityAt(c.x(), c.z()), 1e-6, "a crossing must lie on a current");
                return;
            }
        }
        fail("no seed in 0..19 produced a crossing to check");
    }

    @Test
    void isDeterministic() {
        ResidueNetwork a = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        ResidueNetwork b = new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
        assertEquals(a.grandConvergence(), b.grandConvergence());
        assertEquals(a.densityAt(100.0, 200.0), b.densityAt(100.0, 200.0), 1e-12);
        assertEquals(
                a.nexusesWithin(-5000.0, -5000.0, 5000.0, 5000.0), b.nexusesWithin(-5000.0, -5000.0, 5000.0, 5000.0));
    }
}

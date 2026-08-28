package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.internal.residue.DefaultResidueNetwork;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResidueNetworkTest {

    private static final CurrentParameters PARAMS = new CurrentParameters(4, 80, 16.0, 0.35, 24.0);
    private static final long SEED = 20260827L;

    @Test
    void densityAt_convergence_isMaximum() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        assertEquals(1.0, n.densityAt(c.x(), c.z()), 1e-9);
    }

    @Test
    void densityAt_farAway_isZero() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        assertEquals(0.0, n.densityAt(c.x() + 100_000.0, c.z() + 100_000.0), 1e-9);
    }

    @Test
    void densityAt_isBounded() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        for (int i = -50; i <= 50; i++) {
            double d = n.densityAt(c.x() + i * 13.0, c.z() + i * 7.0);
            assertTrue(d >= 0.0 && d <= 1.0, "density out of range: " + d);
        }
    }

    @Test
    void densityAt_fallsOffWithDistance() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        double near = n.densityAt(c.x(), c.z());
        double mid = n.densityAt(c.x(), c.z() + PARAMS.influenceRadius() * 0.5);
        assertTrue(near > mid, "expected density to fall off: " + near + " vs " + mid);
    }

    @Test
    void flowAt_farAway_isEmpty() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        assertEquals(Optional.empty(), n.flowAt(c.x() + 100_000.0, c.z() + 100_000.0));
    }

    @Test
    void flowAt_onCurrent_isPresentAndUnit() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        WorldPoint2d c = n.grandConvergence();
        Optional<FlowVector> flow = n.flowAt(c.x(), c.z());
        assertTrue(flow.isPresent());
        assertEquals(1.0, Math.hypot(flow.get().dx(), flow.get().dz()), 1e-9);
    }

    @Test
    void flowAt_pointsTowardConvergence() {
        // The whole navigation design rests on this: walking along the flow must reduce the
        // distance to the Grand Convergence. Reversed flow would silently invert the questline.
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
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
    void circlesWithin_areInsideTheBounds() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        List<WorldPoint2d> circles = n.circlesWithin(-5000.0, -5000.0, 5000.0, 5000.0);
        for (WorldPoint2d p : circles) {
            assertTrue(p.x() >= -5000.0 && p.x() <= 5000.0, "x out of bounds: " + p);
            assertTrue(p.z() >= -5000.0 && p.z() <= 5000.0, "z out of bounds: " + p);
        }
    }

    @Test
    void circlesWithin_emptyRegion_isEmpty() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        assertTrue(n.circlesWithin(500_000.0, 500_000.0, 510_000.0, 510_000.0).isEmpty());
    }

    @Test
    void circlesWithin_rejectsInvertedBounds() {
        ResidueNetwork n = new DefaultResidueNetwork(SEED, PARAMS);
        assertThrows(IllegalArgumentException.class, () -> n.circlesWithin(10.0, 0.0, 0.0, 10.0));
    }

    @Test
    void circlesWithin_findsCrossings_forSomeSeed() {
        // Guards against circlesWithin returning empty unconditionally. Crossing counts are
        // seed-dependent and low at these parameters -- some seeds legitimately yield zero -- so
        // this asserts existence across a scan rather than a count at one seed, which would be
        // brittle against any geometry change.
        int seedsWithCrossings = 0;
        for (long seed = 0; seed < 20; seed++) {
            ResidueNetwork n = new DefaultResidueNetwork(seed, PARAMS);
            if (!n.circlesWithin(-100_000.0, -100_000.0, 100_000.0, 100_000.0).isEmpty()) {
                seedsWithCrossings++;
            }
        }
        assertTrue(seedsWithCrossings > 0, "no seed in 0..19 produced a crossing; crossing detection is dead");
    }

    @Test
    void circlesWithin_crossingLiesOnTheNetwork() {
        // A crossing is by construction a point on two segments, so density there must be maximal.
        // This catches a circlesWithin that returns points unrelated to the geometry.
        for (long seed = 0; seed < 20; seed++) {
            ResidueNetwork n = new DefaultResidueNetwork(seed, PARAMS);
            List<WorldPoint2d> circles = n.circlesWithin(-100_000.0, -100_000.0, 100_000.0, 100_000.0);
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
        ResidueNetwork a = new DefaultResidueNetwork(SEED, PARAMS);
        ResidueNetwork b = new DefaultResidueNetwork(SEED, PARAMS);
        assertEquals(a.grandConvergence(), b.grandConvergence());
        assertEquals(a.densityAt(100.0, 200.0), b.densityAt(100.0, 200.0), 1e-12);
        assertEquals(
                a.circlesWithin(-5000.0, -5000.0, 5000.0, 5000.0), b.circlesWithin(-5000.0, -5000.0, 5000.0, 5000.0));
    }
}

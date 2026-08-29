package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.residue.DefaultResidueNetwork;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Owns the claim that a nexus is a <em>peak</em>: density is 1.0 only there, and strictly greater
 * than anywhere else on the same current.
 *
 * <p>The design record for N6 is explicit that this property survived eight reviews unimplemented
 * because no task owned its test. These tests are the deliverable, not a side effect — so each one
 * asserts its precondition (that crossings exist at all) before comparing anything. A prior review
 * found a crossing test that would have passed against an unconditionally empty list.
 *
 * <p>Crossing <em>counts</em> are deliberately not hard-coded. The design's measured figures
 * (three at {@code defaults()} seed 1, two at {@code stepsPerArm} 80) were taken before element
 * identity shifted every arm's initial heading, so they no longer hold.
 */
class NexusPeakTest {

    private static final double WEIGHT = 0.3;
    private static final CurrentParameters PARAMS = new CurrentParameters(4, 80, 16.0, 0.35, 24.0, WEIGHT);
    private static final ResourceId ELEMENT = ResourceId.of("test", "element");
    private static final long SEED = 1L;
    private static final double BOUND = 100_000.0;

    private static ResidueNetwork network() {
        return new DefaultResidueNetwork(SEED, ELEMENT, PARAMS);
    }

    private static List<WorldPoint2d> nexuses(ResidueNetwork n) {
        List<WorldPoint2d> found = n.nexusesWithin(-BOUND, -BOUND, BOUND, BOUND);
        assertFalse(found.isEmpty(), "precondition: these parameters must produce at least one nexus");
        return found;
    }

    private static double distanceToNearestNexus(List<WorldPoint2d> nexuses, double x, double z) {
        double best = Double.MAX_VALUE;
        for (WorldPoint2d p : nexuses) {
            best = Math.min(best, Math.hypot(p.x() - x, p.z() - z));
        }
        return best;
    }

    @Test
    void densityAtNexus_strictlyExceedsAnotherPointOnTheSameCurrent() {
        ResidueNetwork n = network();
        List<WorldPoint2d> found = nexuses(n);
        WorldPoint2d nexus = found.get(0);
        WorldPoint2d convergence = n.grandConvergence();

        // The convergence is on the current by construction: every arm starts there.
        assertTrue(
                distanceToNearestNexus(found, convergence.x(), convergence.z()) > PARAMS.nexusRadius(),
                "precondition: the convergence must lie outside every nexus peak for this comparison");

        double atNexus = n.densityAt(nexus.x(), nexus.z());
        double onCurrent = n.densityAt(convergence.x(), convergence.z());

        assertTrue(
                atNexus > onCurrent,
                "a nexus must be a strict maximum, not a shoulder: " + atNexus + " vs " + onCurrent);
    }

    @Test
    void densityOfOne_occursOnlyAtANexus() {
        ResidueNetwork n = network();
        List<WorldPoint2d> found = nexuses(n);

        assertEquals(1.0, n.densityAt(found.get(0).x(), found.get(0).z()), 1e-9);

        WorldPoint2d c = n.grandConvergence();
        for (int i = -30; i <= 30; i++) {
            for (int j = -30; j <= 30; j++) {
                double x = c.x() + i * 37.0;
                double z = c.z() + j * 37.0;
                if (n.densityAt(x, z) > 1.0 - 1e-9) {
                    assertTrue(
                            distanceToNearestNexus(found, x, z) < 1e-6,
                            "density reached 1.0 away from every nexus, at " + x + "," + z);
                }
            }
        }
    }

    @Test
    void onOrdinaryCurrent_densityIsOneMinusNexusWeight() {
        ResidueNetwork n = network();
        List<WorldPoint2d> found = nexuses(n);
        WorldPoint2d c = n.grandConvergence();

        assertTrue(
                distanceToNearestNexus(found, c.x(), c.z()) > PARAMS.nexusRadius(),
                "precondition: the convergence must lie outside every nexus peak");

        assertEquals(1.0 - WEIGHT, n.densityAt(c.x(), c.z()), 1e-9);
    }

    @Test
    void nexusWeightZero_reproducesTheUnpeakedField() {
        CurrentParameters flat = new CurrentParameters(4, 80, 16.0, 0.35, 24.0, 0.0);
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, flat);
        List<WorldPoint2d> found = n.nexusesWithin(-BOUND, -BOUND, BOUND, BOUND);
        assertFalse(found.isEmpty(), "precondition: crossings still exist when the weight is zero");

        // With no weight on the nexus term, a nexus is no longer a peak: both sit on the current.
        assertEquals(1.0, n.densityAt(found.get(0).x(), found.get(0).z()), 1e-9);
        WorldPoint2d c = n.grandConvergence();
        assertEquals(1.0, n.densityAt(c.x(), c.z()), 1e-9);
    }

    @Test
    void networkWithNoCrossings_capsAtOneMinusNexusWeight() {
        // Zero jitter yields straight arms radiating from one point, which never cross.
        CurrentParameters straight = new CurrentParameters(4, 80, 16.0, 0.0, 24.0, WEIGHT);
        ResidueNetwork n = new DefaultResidueNetwork(SEED, ELEMENT, straight);

        assertTrue(
                n.nexusesWithin(-BOUND, -BOUND, BOUND, BOUND).isEmpty(), "precondition: straight arms must not cross");

        WorldPoint2d c = n.grandConvergence();
        assertEquals(
                1.0 - WEIGHT,
                n.densityAt(c.x(), c.z()),
                1e-9,
                "a crossing-free world caps below 1.0 everywhere; this pins the behaviour, it does not bless it");
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.residue.CurrentParameters;
import io.github.legendaryforge.legendary.core.api.residue.ResidueNetwork;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.DefaultResidueNetwork;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Owns the N6 interaction that the six-element design says nobody else owns.
 *
 * <p>N6 established that density is {@code 1.0} only at a nexus. Suppressing density inside the
 * null radius means nexuses there can no longer reach {@code 1.0} — so "walk uphill" must still
 * work even though "density 1.0 identifies a nexus" stops being globally true. That is a property
 * spanning two specs, and these tests are its only reviewer.
 */
class NullCoreResidueNetworkTest {

    private static final CurrentParameters PARAMS = CurrentParameters.defaults();
    private static final ResourceId LIGHTNING = ResourceId.of("stormseeker", "lightning");
    private static final double R = NullCoreResidueNetwork.LIGHTNING_NULL_RADIUS;
    private static final double BOUND = 100_000.0;

    private record Inside(long seed, ResidueNetwork net, WorldPoint2d nexus, double distance) {}

    /** Every nexus that falls inside the null radius, across a seed sweep. */
    private static List<Inside> nexusesInsideNullRadius(int seeds) {
        List<Inside> found = new ArrayList<>();
        for (long seed = 1; seed <= seeds; seed++) {
            ResidueNetwork base = new DefaultResidueNetwork(seed, LIGHTNING, PARAMS);
            ResidueNetwork net = NullCoreResidueNetwork.lightning(base);
            WorldPoint2d c = net.grandConvergence();
            for (WorldPoint2d n : net.nexusesWithin(-BOUND, -BOUND, BOUND, BOUND)) {
                double d = Math.hypot(n.x() - c.x(), n.z() - c.z());
                if (d < R) {
                    found.add(new Inside(seed, net, n, d));
                }
            }
        }
        return found;
    }

    /** True when no point on a small ring around the nexus reads denser than the nexus itself. */
    private static boolean isLocalMaximum(ResidueNetwork net, WorldPoint2d n, double probeRadius) {
        double here = net.densityAt(n.x(), n.z());
        for (int i = 0; i < 32; i++) {
            double a = 2.0 * Math.PI * i / 32.0;
            double d = net.densityAt(n.x() + Math.cos(a) * probeRadius, n.z() + Math.sin(a) * probeRadius);
            if (d >= here) {
                return false;
            }
        }
        return true;
    }

    @Test
    void atConvergence_densityIsZero() {
        ResidueNetwork net = NullCoreResidueNetwork.lightning(new DefaultResidueNetwork(1L, LIGHTNING, PARAMS));
        WorldPoint2d c = net.grandConvergence();
        assertEquals(0.0, net.densityAt(c.x(), c.z()), 1e-12);
    }

    @Test
    void beyondNullRadius_densityIsUnchanged() {
        ResidueNetwork base = new DefaultResidueNetwork(1L, LIGHTNING, PARAMS);
        ResidueNetwork net = NullCoreResidueNetwork.lightning(base);
        WorldPoint2d c = net.grandConvergence();

        for (int i = 0; i < 40; i++) {
            double a = 2.0 * Math.PI * i / 40.0;
            double x = c.x() + Math.cos(a) * (R + 50.0);
            double z = c.z() + Math.sin(a) * (R + 50.0);
            assertEquals(base.densityAt(x, z), net.densityAt(x, z), 1e-12);
        }
    }

    @Test
    void insideNullRadius_densityIsSuppressedTowardTheCentre() {
        ResidueNetwork base = new DefaultResidueNetwork(1L, LIGHTNING, PARAMS);
        ResidueNetwork net = NullCoreResidueNetwork.lightning(base);
        WorldPoint2d c = net.grandConvergence();

        double half = net.densityAt(c.x() + R / 2.0, c.z());
        double baseHalf = base.densityAt(c.x() + R / 2.0, c.z());
        assertEquals(baseHalf * 0.5, half, 1e-9, "survival is linear in distance from the convergence");
    }

    @Test
    void flowIsUnsuppressed_theNeedleKeepsWorking() {
        ResidueNetwork base = new DefaultResidueNetwork(1L, LIGHTNING, PARAMS);
        ResidueNetwork net = NullCoreResidueNetwork.lightning(base);
        WorldPoint2d c = net.grandConvergence();

        // The needle's best moment is exactly here: it points upstream while the crystals die out.
        assertEquals(base.flowAt(c.x() + 30.0, c.z()), net.flowAt(c.x() + 30.0, c.z()));
    }

    @Test
    void nexusesAreSuppressedNotDeleted() {
        List<Inside> inside = nexusesInsideNullRadius(40);
        assertFalse(inside.isEmpty(), "precondition: the sweep must find a nexus inside the null radius");

        Inside first = inside.get(0);
        assertTrue(
                first.net().densityAt(first.nexus().x(), first.nexus().z()) < 1.0,
                "a nexus inside the null radius cannot reach 1.0 — the global N6 reading stops holding");
    }

    /**
     * The distance inside which suppression overwhelms the nexus peak.
     *
     * <p>Derived, not fitted. Density is {@code (d/R) * base}. Stepping outward from a nexus,
     * survival grows at {@code 1/R} while base falls at {@code nexusWeight / nexusRadius}, so
     * density still <em>increases</em> while {@code base > d * nexusWeight / nexusRadius}. At a
     * nexus {@code base == 1}, giving {@code d < nexusRadius / nexusWeight} — and note this
     * threshold does not involve {@code nullRadius} at all.
     */
    private static final double PEAK_SURVIVES_BEYOND = PARAMS.nexusRadius() / PARAMS.nexusWeight();

    @Test
    void nexusesBeyondTheInnerBand_remainLocalMaxima() {
        List<Inside> inside = nexusesInsideNullRadius(200);
        List<Inside> outer = inside.stream()
                .filter(i -> i.distance() >= PEAK_SURVIVES_BEYOND)
                .toList();
        assertFalse(outer.isEmpty(), "precondition: the sweep must find nexuses in the outer band");

        List<String> failures = new ArrayList<>();
        for (Inside i : outer) {
            if (!isLocalMaximum(i.net(), i.nexus(), 1.0)) {
                failures.add(String.format("seed %d, %.1f m", i.seed(), i.distance()));
            }
        }
        assertTrue(failures.isEmpty(), "walk-uphill must still find these nexuses; failed for: " + failures);
    }

    /**
     * Pins the behaviour; does <strong>not</strong> bless it.
     *
     * <p>The six-element design asserts that a nexus inside the null radius "remains a local
     * maximum". Measured over 200 seeds, that is false for the innermost ones: 5 of 56 nexuses
     * inside the null radius are not local maxima, all within 29.1 m of the convergence. Such a
     * nexus cannot be found by walking uphill — the gradient leads away from it.
     *
     * <p>Left as measured rather than fixed, because every available fix is a design decision:
     * suppress only the current term, drop these nexuses from {@code nexusesWithin} (which is what
     * the design's own "10.5% of nexuses lost" accounting already implies), or accept the loss. At
     * the shipped tuning this is roughly 1% of all nexuses.
     */
    @Test
    void nexusesVeryNearTheConvergence_areNotLocalMaxima() {
        List<Inside> inside = nexusesInsideNullRadius(200);
        List<Inside> inner =
                inside.stream().filter(i -> i.distance() < PEAK_SURVIVES_BEYOND).toList();
        assertFalse(inner.isEmpty(), "precondition: the sweep must find nexuses in the inner band");

        long broken = inner.stream()
                .filter(i -> !isLocalMaximum(i.net(), i.nexus(), 1.0))
                .count();
        assertTrue(
                broken > 0,
                "expected the innermost nexuses to lose their peak; if this passes, the suppression "
                        + "curve changed and the design's local-maximum claim may now hold outright");
    }
}

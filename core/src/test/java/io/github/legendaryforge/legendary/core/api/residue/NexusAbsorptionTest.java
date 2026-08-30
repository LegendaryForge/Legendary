package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.residue.DefaultResidueNetwork;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Owns absorption: crossings within {@link CurrentParameters#nexusAbsorptionRadius()} of the Grand
 * Convergence merge into it and are not nexuses — for any purpose.
 *
 * <p>The motivation is geometric and element-neutral. Arms leave the convergence only
 * {@code 360/armCount} degrees apart and {@code headingJitter} bends them together before they
 * separate, so crossings pile up at the centre: the six-element scan measured p10 at 194 m with a
 * median <em>nearest</em> crossing of 195 m. Those are the same meeting, resolved as several
 * distinct nexuses only because the polyline happens to cross itself more than once on its way out.
 *
 * <p>The property that makes this worth a parameter rather than a filter at the call site is that
 * <strong>one crossing set feeds both</strong> {@code densityAt} and {@code nexusesWithin}. An
 * absorbed crossing therefore cannot leave a density peak standing at a position the same network
 * declines to call a nexus — which is the inconsistency a listing-only filter would create.
 */
class NexusAbsorptionTest {

    private static final double WEIGHT = 0.3;
    private static final CurrentParameters UNABSORBED = new CurrentParameters(4, 160, 16.0, 0.35, 24.0, WEIGHT, 0.0);
    private static final ResourceId ELEMENT = ResourceId.of("test", "element");
    private static final double BOUND = 100_000.0;

    /**
     * The first seed whose network has crossings at two different distances from the convergence,
     * so that some absorption radius provably splits them.
     *
     * <p>Searched rather than hard-coded. Crossing counts are a property of the seed, and the
     * six-element work has already been bitten once by a sweep that happened to contain none of the
     * case under test — a fixed seed here would make every assertion below vacuous the next time
     * element identity or the arm walk shifts.
     */
    private static final long SEED = seedWithSplittableCrossings();

    private static long seedWithSplittableCrossings() {
        for (long seed = 1; seed <= 200; seed++) {
            ResidueNetwork n = new DefaultResidueNetwork(seed, ELEMENT, UNABSORBED);
            List<Double> d = distances(n);
            if (d.size() >= 2 && d.get(0) < d.get(d.size() - 1)) {
                return seed;
            }
        }
        throw new AssertionError("no seed in 1..200 produces crossings at two different distances");
    }

    private static List<Double> distances(ResidueNetwork n) {
        List<Double> distances = new ArrayList<>();
        for (WorldPoint2d p : nexuses(n)) {
            distances.add(distanceFromConvergence(n, p));
        }
        distances.sort(Double::compare);
        return distances;
    }

    private static List<WorldPoint2d> nexuses(ResidueNetwork n) {
        return n.nexusesWithin(-BOUND, -BOUND, BOUND, BOUND);
    }

    private static double distanceFromConvergence(ResidueNetwork n, WorldPoint2d p) {
        WorldPoint2d c = n.grandConvergence();
        return Math.hypot(p.x() - c.x(), p.z() - c.z());
    }

    /**
     * An absorption radius that provably splits this seed's crossings — at least one falls inside
     * and at least one outside. Derived rather than hard-coded, because a constant that happened to
     * drop everything (or nothing) would let every assertion below pass vacuously.
     */
    private static double splittingRadius(ResidueNetwork unabsorbed) {
        List<Double> distances = distances(unabsorbed);
        assertTrue(distances.size() >= 2, "precondition: need at least two crossings to split");
        double r = (distances.get(0) + distances.get(distances.size() - 1)) / 2.0;
        assertTrue(distances.get(0) < r, "precondition: at least one crossing inside the radius");
        assertTrue(distances.get(distances.size() - 1) > r, "precondition: at least one crossing outside");
        return r;
    }

    @Test
    void zeroAbsorption_keepsEveryCrossing() {
        ResidueNetwork off = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED);
        ResidueNetwork explicitZero =
                new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED.withNexusAbsorptionRadius(0.0));

        assertFalse(nexuses(off).isEmpty(), "precondition: these parameters must produce nexuses");
        assertEquals(nexuses(off), nexuses(explicitZero), "zero absorption is the identity");
    }

    @Test
    void crossingsInsideTheRadius_areNotListed() {
        ResidueNetwork unabsorbed = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED);
        double r = splittingRadius(unabsorbed);
        ResidueNetwork absorbed = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED.withNexusAbsorptionRadius(r));

        List<WorldPoint2d> kept = nexuses(absorbed);
        assertFalse(kept.isEmpty(), "absorption must not empty the network at a splitting radius");
        assertTrue(kept.size() < nexuses(unabsorbed).size(), "absorption must actually drop something");
        for (WorldPoint2d p : kept) {
            assertTrue(
                    distanceFromConvergence(absorbed, p) >= r,
                    "a surviving nexus must lie outside the absorption radius");
        }
    }

    @Test
    void anAbsorbedCrossing_leavesNoDensityPeakBehind() {
        ResidueNetwork unabsorbed = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED);
        double r = splittingRadius(unabsorbed);
        ResidueNetwork absorbed = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED.withNexusAbsorptionRadius(r));

        WorldPoint2d dropped = null;
        for (WorldPoint2d p : nexuses(unabsorbed)) {
            if (distanceFromConvergence(unabsorbed, p) < r) {
                dropped = p;
                break;
            }
        }
        assertNotNull(dropped, "precondition: the splitting radius must drop at least one crossing");

        assertEquals(
                1.0,
                unabsorbed.densityAt(dropped.x(), dropped.z()),
                1e-9,
                "precondition: unabsorbed, this position is a nexus and reads exactly 1.0");
        assertTrue(
                absorbed.densityAt(dropped.x(), dropped.z()) < 1.0,
                "absorbed, the same position must no longer read as a nexus");
    }

    /**
     * The prize. N6 says density {@code 1.0} identifies a nexus; the six-element design conceded
     * that a suppressed core breaks the <em>global</em> reading of that. Absorbing rather than
     * suppressing the crossings restores it: every nexus the network reports is a full peak.
     */
    @Test
    void everySurvivingNexus_stillReadsExactlyOne() {
        ResidueNetwork unabsorbed = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED);
        double r = splittingRadius(unabsorbed);
        ResidueNetwork absorbed = new DefaultResidueNetwork(SEED, ELEMENT, UNABSORBED.withNexusAbsorptionRadius(r));

        List<WorldPoint2d> kept = nexuses(absorbed);
        assertFalse(kept.isEmpty(), "precondition: absorption must leave at least one nexus");
        for (WorldPoint2d p : kept) {
            assertEquals(1.0, absorbed.densityAt(p.x(), p.z()), 1e-9, "every reported nexus is a full peak");
        }
    }

    @Test
    void negativeAbsorptionRadius_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> UNABSORBED.withNexusAbsorptionRadius(-1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CurrentParameters(4, 80, 16.0, 0.35, 24.0, WEIGHT, Double.NaN));
    }
}

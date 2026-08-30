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
 * <p>N6 established that density is {@code 1.0} only at a nexus. The design expected that
 * suppressing density inside the null radius would cost the <em>global</em> reading of that — a
 * nexus there could no longer reach 1.0 — while "walk uphill" survived because such a nexus
 * "remains a local maximum". <strong>The second half was measured and found false</strong> for the
 * innermost nexuses, so absorption replaced suppression for them: a crossing inside the null radius
 * merges into the Grand Convergence instead of standing as its own nexus.
 *
 * <p>The result is stronger than the design asked for. Every nexus the network reports is a full
 * peak, so the global reading of N6 is restored rather than conceded, and there is no band in which
 * walking uphill fails.
 */
class NullCoreResidueNetworkTest {

    /** Lightning's own parameters: the null core absorbs, so it must be built absorbing. */
    private static final CurrentParameters PARAMS =
            NullCoreResidueNetwork.lightningParameters(CurrentParameters.defaults());

    /** The same current with an untouched core, for before/after comparison only. */
    private static final CurrentParameters UNABSORBED = CurrentParameters.defaults();

    private static final ResourceId LIGHTNING = ResourceId.of("stormseeker", "lightning");
    private static final double R = NullCoreResidueNetwork.LIGHTNING_NULL_RADIUS;
    private static final double BOUND = 100_000.0;

    private record Near(long seed, ResidueNetwork net, WorldPoint2d nexus, double distance) {}

    /** Every nexus within {@code maxDistance} of the convergence, across a seed sweep. */
    private static List<Near> nexusesNearConvergence(CurrentParameters params, int seeds, double maxDistance) {
        List<Near> found = new ArrayList<>();
        for (long seed = 1; seed <= seeds; seed++) {
            ResidueNetwork base = new DefaultResidueNetwork(seed, LIGHTNING, params);
            ResidueNetwork net = params.nexusAbsorptionRadius() > 0.0 ? NullCoreResidueNetwork.lightning(base) : base;
            WorldPoint2d c = net.grandConvergence();
            for (WorldPoint2d n : net.nexusesWithin(-BOUND, -BOUND, BOUND, BOUND)) {
                double d = Math.hypot(n.x() - c.x(), n.z() - c.z());
                if (d < maxDistance) {
                    found.add(new Near(seed, net, n, d));
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
    void nexusesInsideNullRadius_areAbsorbedIntoTheConvergence() {
        // Non-vacuity first: without absorption this sweep finds nexuses in the null radius. An
        // earlier version of this file's inner-band test passed against a sweep that happened to
        // contain none, which is the failure this precondition exists to make impossible.
        assertFalse(
                nexusesNearConvergence(UNABSORBED, 40, R).isEmpty(),
                "precondition: the unabsorbed current must put nexuses inside the null radius");

        assertTrue(
                nexusesNearConvergence(PARAMS, 40, R).isEmpty(),
                "every crossing inside the null radius merges into the Grand Convergence");
    }

    @Test
    void absorptionDoesNotEmptyTheNetwork() {
        List<Near> kept = nexusesNearConvergence(PARAMS, 40, BOUND);
        assertFalse(kept.isEmpty(), "absorbing the core must leave the rest of the network intact");
    }

    /**
     * The distance inside which suppression would overwhelm the nexus peak — the reason absorption
     * is necessary at all, and the reason it must reach at least this far.
     *
     * <p>Derived, not fitted. Density under suppression is {@code (d/R) * base}. Stepping outward
     * from a nexus, survival grows at {@code 1/R} while base falls at
     * {@code nexusWeight / nexusRadius}, so density still <em>increases</em> while
     * {@code base > d * nexusWeight / nexusRadius}. At a nexus {@code base == 1}, giving
     * {@code d < nexusRadius / nexusWeight} — and note this threshold does not involve
     * {@code nullRadius} at all, which is why no retuning of the null radius could have fixed it.
     */
    private static final double PEAK_SURVIVES_BEYOND = PARAMS.nexusRadius() / PARAMS.nexusWeight();

    /**
     * The guard that keeps the fix correct under a retune. Absorption removes the broken band only
     * while it reaches past the threshold above; raise {@code nexusWeight} or {@code influenceRadius}
     * far enough and nexuses that are not local maxima reappear outside the absorbed core.
     */
    @Test
    void absorptionReachesPastTheThresholdWhereSuppressionBreaksThePeak() {
        assertTrue(
                R >= PEAK_SURVIVES_BEYOND,
                "null radius " + R + " must cover the " + PEAK_SURVIVES_BEYOND
                        + " m band in which a suppressed nexus is not a local maximum");
    }

    /**
     * The claim the six-element design assigned to this class, now true without a carve-out.
     *
     * <p>Scoped to the band where survival varies, because beyond the null radius the property is
     * plain N6 and {@code NexusPeakTest} in {@code :core} already owns it.
     */
    @Test
    void everyNexus_isALocalMaximum() {
        List<Near> near = nexusesNearConvergence(PARAMS, 200, 2.0 * R);
        assertFalse(near.isEmpty(), "precondition: the sweep must find nexuses near the convergence");

        List<String> failures = new ArrayList<>();
        for (Near n : near) {
            if (!isLocalMaximum(n.net(), n.nexus(), 1.0)) {
                failures.add(String.format("seed %d, %.1f m", n.seed(), n.distance()));
            }
        }
        assertTrue(failures.isEmpty(), "walk-uphill must find every nexus; failed for: " + failures);
    }

    /** The global reading of N6, restored rather than conceded. */
    @Test
    void everyNexusReadsExactlyOne() {
        List<Near> near = nexusesNearConvergence(PARAMS, 40, 2.0 * R);
        assertFalse(near.isEmpty(), "precondition: the sweep must find nexuses near the convergence");
        for (Near n : near) {
            assertEquals(1.0, n.net().densityAt(n.nexus().x(), n.nexus().z()), 1e-9, "seed " + n.seed());
        }
    }

    /**
     * The two halves of the wound are configured in different places, so the class refuses to be
     * built half-configured rather than silently reintroducing the band it was written to remove.
     */
    @Test
    void wrappingAnUnabsorbedDelegate_isRejected() {
        long seed = firstSeedWithANexusInsideTheNullRadius();
        ResidueNetwork unabsorbed = new DefaultResidueNetwork(seed, LIGHTNING, UNABSORBED);
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> NullCoreResidueNetwork.lightning(unabsorbed));
        assertTrue(e.getMessage().contains("withNexusAbsorptionRadius"), "the message must name the fix");
    }

    private static long firstSeedWithANexusInsideTheNullRadius() {
        List<Near> found = nexusesNearConvergence(UNABSORBED, 40, R);
        assertFalse(found.isEmpty(), "precondition: some seed must put a nexus inside the null radius");
        return found.get(0).seed();
    }
}

package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.residue.DefaultResidueNetwork;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two elements sharing a world seed and tuning must not produce the same network.
 *
 * <p>Before element identity existed they did — verifiably, at every probe point — because the
 * constructor took only {@code (worldSeed, parameters)} and {@code buildArm} set the initial
 * heading to {@code 2π · armIndex / armCount}. Any two elements with the same {@code armCount}
 * therefore started their arms in identical directions from the identical point.
 *
 * <p>The inverse invariant is tested here too: elements must <em>agree</em> on the Grand
 * Convergence. {@code GrandConvergence} carries an explicit prohibition against per-element
 * variation, and this is the test that keeps the new element term from leaking into it.
 */
class ElementIdentityTest {

    private static final CurrentParameters PARAMS = new CurrentParameters(4, 80, 16.0, 0.35, 24.0, 0.3);
    private static final long SEED = 20260827L;

    private static final ResourceId LIGHTNING = ResourceId.of("test", "lightning");
    private static final ResourceId FIRE = ResourceId.of("test", "fire");

    /** Probe points spread well beyond one arm, so a difference anywhere in the star is caught. */
    private static int differingProbePoints(ResidueNetwork a, ResidueNetwork b) {
        WorldPoint2d c = a.grandConvergence();
        int differing = 0;
        for (int i = 0; i < 100; i++) {
            double x = c.x() + ((i % 10) - 5) * 137.0;
            double z = c.z() + ((i / 10) - 5) * 137.0;
            if (Math.abs(a.densityAt(x, z) - b.densityAt(x, z)) > 1e-12) {
                differing++;
            }
        }
        return differing;
    }

    @Test
    void differentElements_produceDifferentDensityFields() {
        ResidueNetwork lightning = new DefaultResidueNetwork(SEED, LIGHTNING, PARAMS);
        ResidueNetwork fire = new DefaultResidueNetwork(SEED, FIRE, PARAMS);

        assertTrue(
                differingProbePoints(lightning, fire) > 0,
                "two elements sharing a seed and tuning produced an identical density field");
    }

    @Test
    void differentElements_produceDifferentNexuses() {
        ResidueNetwork lightning = new DefaultResidueNetwork(SEED, LIGHTNING, PARAMS);
        ResidueNetwork fire = new DefaultResidueNetwork(SEED, FIRE, PARAMS);

        double r = 100_000.0;
        List<WorldPoint2d> a = lightning.nexusesWithin(-r, -r, r, r);
        List<WorldPoint2d> b = fire.nexusesWithin(-r, -r, r, r);

        assertNotEquals(a, b, "two elements sharing a seed and tuning produced identical nexuses");
    }

    @Test
    void sameElement_isDeterministic() {
        ResidueNetwork first = new DefaultResidueNetwork(SEED, LIGHTNING, PARAMS);
        ResidueNetwork second = new DefaultResidueNetwork(SEED, LIGHTNING, PARAMS);

        assertEquals(
                0,
                differingProbePoints(first, second),
                "the same element must be reproducible: identity may not introduce run-to-run variance");
    }

    @Test
    void allElements_shareOneGrandConvergence() {
        WorldPoint2d lightning = new DefaultResidueNetwork(SEED, LIGHTNING, PARAMS).grandConvergence();
        WorldPoint2d fire = new DefaultResidueNetwork(SEED, FIRE, PARAMS).grandConvergence();
        WorldPoint2d water = new DefaultResidueNetwork(SEED, ResourceId.of("test", "water"), PARAMS).grandConvergence();

        assertEquals(lightning, fire, "elements must agree on the shared anchor");
        assertEquals(lightning, water, "elements must agree on the shared anchor");
    }

    @Test
    void elementId_isRequired() {
        assertThrows(NullPointerException.class, () -> new DefaultResidueNetwork(SEED, null, PARAMS));
    }
}

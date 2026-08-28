package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import org.junit.jupiter.api.Test;

class GrandConvergenceTest {

    @Test
    void locate_isDeterministic() {
        assertEquals(GrandConvergence.locate(1234L), GrandConvergence.locate(1234L));
    }

    @Test
    void locate_differsBySeed() {
        assertNotEquals(GrandConvergence.locate(1234L), GrandConvergence.locate(1235L));
    }

    @Test
    void locate_staysWithinDeclaredRadiusBand() {
        WorldPoint2d origin = new WorldPoint2d(0.0, 0.0);
        for (long seed = 0; seed < 500; seed++) {
            double r = GrandConvergence.locate(seed).distanceTo(origin);
            assertTrue(
                    r >= GrandConvergence.MIN_RADIUS && r <= GrandConvergence.MAX_RADIUS,
                    "seed " + seed + " gave radius " + r);
        }
    }

    @Test
    void locate_spreadsAroundOrigin() {
        // All four quadrants should be reachable; a bug fixing the angle would collapse this.
        boolean[] quadrant = new boolean[4];
        for (long seed = 0; seed < 500; seed++) {
            WorldPoint2d p = GrandConvergence.locate(seed);
            int q = (p.x() >= 0 ? 0 : 1) + (p.z() >= 0 ? 0 : 2);
            quadrant[q] = true;
        }
        for (int q = 0; q < 4; q++) {
            assertTrue(quadrant[q], "no convergence landed in quadrant " + q);
        }
    }
}

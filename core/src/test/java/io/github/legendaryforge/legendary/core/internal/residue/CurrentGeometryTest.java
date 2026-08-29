package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.residue.CurrentParameters;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentGeometryTest {

    private static final CurrentParameters PARAMS = new CurrentParameters(4, 50, 16.0, 0.35, 24.0);
    private static final ResourceId ELEMENT = ResourceId.of("test", "element");

    @Test
    void arms_haveExpectedCountAndLength() {
        CurrentGeometry g = new CurrentGeometry(1L, ELEMENT, PARAMS);
        assertEquals(4, g.arms().size());
        for (List<WorldPoint2d> arm : g.arms()) {
            assertEquals(51, arm.size(), "stepsPerArm segments means stepsPerArm+1 points");
        }
    }

    @Test
    void everyArm_startsAtConvergence() {
        CurrentGeometry g = new CurrentGeometry(1L, ELEMENT, PARAMS);
        for (List<WorldPoint2d> arm : g.arms()) {
            assertEquals(g.convergence(), arm.get(0));
        }
    }

    @Test
    void isDeterministic() {
        assertEquals(new CurrentGeometry(7L, ELEMENT, PARAMS).arms(), new CurrentGeometry(7L, ELEMENT, PARAMS).arms());
    }

    @Test
    void differsBySeed() {
        assertNotEquals(
                new CurrentGeometry(7L, ELEMENT, PARAMS).arms(), new CurrentGeometry(8L, ELEMENT, PARAMS).arms());
    }

    @Test
    void consecutivePoints_areOneStepApart() {
        CurrentGeometry g = new CurrentGeometry(3L, ELEMENT, PARAMS);
        for (List<WorldPoint2d> arm : g.arms()) {
            for (int i = 1; i < arm.size(); i++) {
                assertEquals(16.0, arm.get(i - 1).distanceTo(arm.get(i)), 1e-9);
            }
        }
    }

    @Test
    void zeroJitter_producesStraightArms() {
        CurrentParameters straight = new CurrentParameters(4, 20, 10.0, 0.0, 24.0);
        CurrentGeometry g = new CurrentGeometry(5L, ELEMENT, straight);
        for (List<WorldPoint2d> arm : g.arms()) {
            WorldPoint2d start = arm.get(0);
            WorldPoint2d end = arm.get(arm.size() - 1);
            // A straight arm's endpoint distance equals total path length.
            assertEquals(20 * 10.0, start.distanceTo(end), 1e-6);
        }
    }

    @Test
    void armsFanOut_notAllInOneDirection() {
        CurrentGeometry g = new CurrentGeometry(11L, ELEMENT, PARAMS);
        WorldPoint2d c = g.convergence();
        boolean sawPositiveX = false;
        boolean sawNegativeX = false;
        for (List<WorldPoint2d> arm : g.arms()) {
            double dx = arm.get(1).x() - c.x();
            sawPositiveX |= dx > 0;
            sawNegativeX |= dx < 0;
        }
        assertTrue(sawPositiveX && sawNegativeX, "arms should radiate, not bunch");
    }

    @Test
    void unmodifiable_armsCannotBeMutated() {
        CurrentGeometry g = new CurrentGeometry(1L, ELEMENT, PARAMS);
        assertThrows(UnsupportedOperationException.class, () -> g.arms().clear());
    }
}

package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CurrentParametersTest {

    @Test
    void defaults_areValid() {
        CurrentParameters p = CurrentParameters.defaults();
        assertTrue(p.armCount() > 0);
        assertTrue(p.stepsPerArm() > 0);
        assertTrue(p.stepLength() > 0.0);
        assertTrue(p.influenceRadius() > 0.0);
    }

    @Test
    void rejects_nonPositiveArmCount() {
        assertThrows(IllegalArgumentException.class, () -> new CurrentParameters(0, 100, 16.0, 0.35, 24.0, 0.3, 0.0));
    }

    @Test
    void rejects_nonPositiveSteps() {
        assertThrows(IllegalArgumentException.class, () -> new CurrentParameters(4, 0, 16.0, 0.35, 24.0, 0.3, 0.0));
    }

    @Test
    void rejects_nonPositiveStepLength() {
        assertThrows(IllegalArgumentException.class, () -> new CurrentParameters(4, 100, 0.0, 0.35, 24.0, 0.3, 0.0));
    }

    @Test
    void rejects_negativeJitter() {
        assertThrows(IllegalArgumentException.class, () -> new CurrentParameters(4, 100, 16.0, -0.1, 24.0, 0.3, 0.0));
    }

    @Test
    void rejects_nonPositiveInfluenceRadius() {
        assertThrows(IllegalArgumentException.class, () -> new CurrentParameters(4, 100, 16.0, 0.35, 0.0, 0.3, 0.0));
    }

    @Test
    void accepts_zeroJitter_forStraightArms() {
        CurrentParameters p = new CurrentParameters(4, 100, 16.0, 0.0, 24.0, 0.3, 0.0);
        assertEquals(0.0, p.headingJitter(), 1e-9);
    }
}

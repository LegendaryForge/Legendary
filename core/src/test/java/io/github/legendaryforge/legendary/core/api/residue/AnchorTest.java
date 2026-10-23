package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnchorTest {

    private static final WorldPoint3d POS = new WorldPoint3d(10, 64, 20);

    @Test
    void strengthFractionIsStrengthOverSamples() {
        Anchor anchor = new Anchor("water", POS, 250, 1000);
        assertEquals(0.25, anchor.strengthFraction(), 1e-9);
    }

    @Test
    void strengthFractionIsZeroWhenNoSamplesTaken() {
        Anchor anchor = new Anchor("water", POS, 0, 0);
        assertEquals(0.0, anchor.strengthFraction(), 1e-9);
    }

    @Test
    void rejectsBlankElementId() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor("  ", POS, 1, 10));
    }

    @Test
    void rejectsStrengthExceedingSamples() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor("water", POS, 11, 10));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor("water", POS, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Anchor("water", POS, 0, -1));
    }
}

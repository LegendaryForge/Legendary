package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResidueRandomTest {

    @Test
    void mix_isDeterministic() {
        assertEquals(ResidueRandom.mix(42L, 1L, 2L), ResidueRandom.mix(42L, 1L, 2L));
    }

    @Test
    void mix_differsBySeed() {
        assertNotEquals(ResidueRandom.mix(42L, 1L, 2L), ResidueRandom.mix(43L, 1L, 2L));
    }

    @Test
    void mix_differsByCoordinate() {
        assertNotEquals(ResidueRandom.mix(42L, 1L, 2L), ResidueRandom.mix(42L, 2L, 1L));
    }

    @Test
    void unit_staysInRange() {
        for (int i = 0; i < 10_000; i++) {
            double v = ResidueRandom.unit(7L, i, i * 31L);
            assertTrue(v >= 0.0 && v < 1.0, "out of range at " + i + ": " + v);
        }
    }

    @Test
    void unit_isRoughlyUniform() {
        int[] buckets = new int[10];
        for (int i = 0; i < 100_000; i++) {
            buckets[(int) (ResidueRandom.unit(9L, i, 0L) * 10)]++;
        }
        for (int b = 0; b < 10; b++) {
            assertTrue(buckets[b] > 8_000 && buckets[b] < 12_000, "bucket " + b + " = " + buckets[b]);
        }
    }

    @Test
    void signed_staysInRange() {
        for (int i = 0; i < 10_000; i++) {
            double v = ResidueRandom.signed(11L, i, 0L);
            assertTrue(v >= -1.0 && v < 1.0, "out of range at " + i + ": " + v);
        }
    }
}

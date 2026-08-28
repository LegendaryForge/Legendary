package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FlowVectorTest {

    @Test
    void normalises_onConstruction() {
        FlowVector v = new FlowVector(0.0, 5.0);
        assertEquals(0.0, v.dx(), 1e-9);
        assertEquals(1.0, v.dz(), 1e-9);
    }

    @Test
    void normalises_diagonal() {
        FlowVector v = new FlowVector(3.0, 4.0);
        assertEquals(0.6, v.dx(), 1e-9);
        assertEquals(0.8, v.dz(), 1e-9);
        assertEquals(1.0, Math.hypot(v.dx(), v.dz()), 1e-9);
    }

    @Test
    void opposite_reversesBothComponents() {
        FlowVector v = new FlowVector(3.0, 4.0).opposite();
        assertEquals(-0.6, v.dx(), 1e-9);
        assertEquals(-0.8, v.dz(), 1e-9);
    }

    @Test
    void rejects_zeroLength() {
        assertThrows(IllegalArgumentException.class, () -> new FlowVector(0.0, 0.0));
    }

    @Test
    void rejects_nonFinite() {
        assertThrows(IllegalArgumentException.class, () -> new FlowVector(Double.NaN, 1.0));
    }
}

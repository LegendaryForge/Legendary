package io.github.legendaryforge.legendary.core.api.residue;

/** A unit-length horizontal direction. Normalised on construction, so every instance is a unit vector. */
public record FlowVector(double dx, double dz) {

    private static final double MIN_LENGTH = 1e-9;

    public FlowVector {
        if (!Double.isFinite(dx) || !Double.isFinite(dz)) {
            throw new IllegalArgumentException("FlowVector requires finite components, got " + dx + "," + dz);
        }
        double length = Math.hypot(dx, dz);
        if (length < MIN_LENGTH) {
            throw new IllegalArgumentException("FlowVector must not be zero-length");
        }
        dx = dx / length;
        dz = dz / length;
    }

    /** The same axis, reversed — downstream given upstream. */
    public FlowVector opposite() {
        return new FlowVector(-dx, -dz);
    }
}

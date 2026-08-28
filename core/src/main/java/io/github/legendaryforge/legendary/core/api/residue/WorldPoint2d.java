package io.github.legendaryforge.legendary.core.api.residue;

/** An immutable horizontal world position. */
public record WorldPoint2d(double x, double z) {

    public WorldPoint2d {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("WorldPoint2d requires finite coordinates, got " + x + "," + z);
        }
    }

    /** Euclidean distance to {@code other}. */
    public double distanceTo(WorldPoint2d other) {
        return Math.hypot(x - other.x, z - other.z);
    }
}

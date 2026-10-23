package io.github.legendaryforge.legendary.core.api.residue;

/**
 * An immutable world position with elevation.
 *
 * <p>Anchors carry their own y (anchor-density design §4), which retires the shared-reference-plane
 * defect: every candidate in a run used to be scored against one {@code referencePlaneY}, so the
 * same candidate scored differently depending on where the player stood. Measured twice on
 * 2026-10-21 — {@code -583,89} scored {@code water 3.0} at {@code y=117} and nothing at
 * {@code y=165}.
 */
public record WorldPoint3d(double x, double y, double z) {

    public WorldPoint3d {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "WorldPoint3d requires finite coordinates, got " + x + "," + y + "," + z);
        }
    }

    /** Euclidean distance in all three axes. */
    public double distanceTo(WorldPoint3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Euclidean distance ignoring elevation.
     *
     * <p>Suppression is a horizontal question and this is the method it uses. The world is 320
     * blocks tall against a 512-block suppression radius, so any two points in one column are
     * already within the radius vertically — vertical distance cannot separate two anchors, and
     * including it would only weaken a comparison that has to be made on x/z.
     */
    public double horizontalDistanceTo(WorldPoint3d other) {
        return Math.hypot(x - other.x, z - other.z);
    }
}

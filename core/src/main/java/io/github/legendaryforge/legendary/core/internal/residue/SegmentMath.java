package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;

/** Planar segment primitives shared by the density, flow and nexus queries. */
final class SegmentMath {

    // Three epsilon thresholds for different purposes, all numerically 1e-9.
    // They measure different scales (squared length, cross-product, and dimensionless parameter),
    // but currently coincide. They need not stay coupled; if one threshold needs to change
    // independently, split the numeric values.
    private static final double LENGTH_SQUARED_EPSILON = 1e-9;
    private static final double CROSS_PRODUCT_EPSILON = 1e-9;
    private static final double PARAMETER_EPSILON = 1e-9;

    private SegmentMath() {}

    /** Clamped parameter in {@code [0,1]} of the point on {@code ab} closest to {@code (px,pz)}. */
    static double projectionParameter(WorldPoint2d a, WorldPoint2d b, double px, double pz) {
        double abx = b.x() - a.x();
        double abz = b.z() - a.z();
        double lengthSquared = abx * abx + abz * abz;
        if (lengthSquared < LENGTH_SQUARED_EPSILON) {
            return 0.0;
        }
        double t = ((px - a.x()) * abx + (pz - a.z()) * abz) / lengthSquared;
        return Math.max(0.0, Math.min(1.0, t));
    }

    static WorldPoint2d pointAt(WorldPoint2d a, WorldPoint2d b, double t) {
        return new WorldPoint2d(a.x() + (b.x() - a.x()) * t, a.z() + (b.z() - a.z()) * t);
    }

    /**
     * Proper crossing point of {@code a1a2} and {@code b1b2}, or {@code null} if they do not cross.
     *
     * <p>Endpoint touches are excluded on purpose: consecutive segments of one arm always share an
     * endpoint, and counting those would report a nexus at every step.
     */
    static WorldPoint2d intersection(WorldPoint2d a1, WorldPoint2d a2, WorldPoint2d b1, WorldPoint2d b2) {
        double ax = a2.x() - a1.x();
        double az = a2.z() - a1.z();
        double bx = b2.x() - b1.x();
        double bz = b2.z() - b1.z();

        double denominator = ax * bz - az * bx;
        if (Math.abs(denominator) < CROSS_PRODUCT_EPSILON) {
            return null; // parallel or degenerate
        }

        double dx = b1.x() - a1.x();
        double dz = b1.z() - a1.z();
        double t = (dx * bz - dz * bx) / denominator;
        double u = (dx * az - dz * ax) / denominator;

        if (t <= PARAMETER_EPSILON
                || t >= 1.0 - PARAMETER_EPSILON
                || u <= PARAMETER_EPSILON
                || u >= 1.0 - PARAMETER_EPSILON) {
            return null; // touches an endpoint, or misses
        }
        return new WorldPoint2d(a1.x() + ax * t, a1.z() + az * t);
    }
}

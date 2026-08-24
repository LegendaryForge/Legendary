package io.github.legendaryforge.legendary.core.internal.legendary.arena.geom;

import java.util.Objects;

/** Internal, platform-agnostic arena bounds model. */
public final class ArenaBounds {

    private final Vec3d center;
    private final double radius;
    private final double radiusSquared;

    public ArenaBounds(Vec3d center, double radius) {
        this.center = Objects.requireNonNull(center, "center");
        if (radius <= 0.0) {
            throw new IllegalArgumentException("radius must be > 0");
        }
        this.radius = radius;
        this.radiusSquared = radius * radius;
    }

    public Vec3d center() {
        return center;
    }

    public double radius() {
        return radius;
    }

    public boolean contains(Vec3d position) {
        Objects.requireNonNull(position, "position");
        return center.distanceSquaredTo(position) <= radiusSquared;
    }
}

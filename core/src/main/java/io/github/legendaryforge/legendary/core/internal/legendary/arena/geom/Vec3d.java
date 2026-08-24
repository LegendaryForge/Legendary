package io.github.legendaryforge.legendary.core.internal.legendary.arena.geom;

import java.util.Objects;

/** Internal, platform-agnostic 3D vector for arena enforcement signals. */
public record Vec3d(double x, double y, double z) {
    public Vec3d {
        // no-op: record provides immutability; keep validation minimal
    }

    public double distanceSquaredTo(Vec3d other) {
        Objects.requireNonNull(other, "other");
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return (dx * dx) + (dy * dy) + (dz * dz);
    }
}

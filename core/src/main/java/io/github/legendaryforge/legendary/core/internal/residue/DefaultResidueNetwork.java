package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.CurrentParameters;
import io.github.legendaryforge.legendary.core.api.residue.FlowVector;
import io.github.legendaryforge.legendary.core.api.residue.ResidueNetwork;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Polyline implementation of {@link ResidueNetwork}. */
public final class DefaultResidueNetwork implements ResidueNetwork {

    private final CurrentGeometry geometry;
    private final double influenceRadius;

    public DefaultResidueNetwork(long worldSeed, CurrentParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        this.geometry = new CurrentGeometry(worldSeed, parameters);
        this.influenceRadius = parameters.influenceRadius();
    }

    @Override
    public double densityAt(double x, double z) {
        double nearest = nearestDistance(x, z);
        if (nearest >= influenceRadius) {
            return 0.0;
        }
        return 1.0 - (nearest / influenceRadius);
    }

    @Override
    public Optional<FlowVector> flowAt(double x, double z) {
        List<List<WorldPoint2d>> arms = geometry.arms();
        double best = Double.MAX_VALUE;
        WorldPoint2d towardConvergence = null;
        WorldPoint2d awayFromConvergence = null;

        for (List<WorldPoint2d> arm : arms) {
            for (int i = 1; i < arm.size(); i++) {
                WorldPoint2d a = arm.get(i - 1); // nearer the convergence
                WorldPoint2d b = arm.get(i);
                double t = SegmentMath.projectionParameter(a, b, x, z);
                WorldPoint2d closest = SegmentMath.pointAt(a, b, t);
                double d = Math.hypot(closest.x() - x, closest.z() - z);
                if (d < best) {
                    best = d;
                    towardConvergence = a;
                    awayFromConvergence = b;
                }
            }
        }

        if (towardConvergence == null || best >= influenceRadius) {
            return Optional.empty();
        }
        double dx = towardConvergence.x() - awayFromConvergence.x();
        double dz = towardConvergence.z() - awayFromConvergence.z();
        if (Math.hypot(dx, dz) < 1e-9) {
            return Optional.empty();
        }
        return Optional.of(new FlowVector(dx, dz));
    }

    @Override
    public List<WorldPoint2d> circlesWithin(double minX, double minZ, double maxX, double maxZ) {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
            throw new IllegalArgumentException(
                    "bounds must be finite: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        }
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("inverted bounds: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        }

        List<WorldPoint2d> segmentsA = flatten();
        List<WorldPoint2d> found = new ArrayList<>();
        for (int i = 0; i + 1 < segmentsA.size(); i += 2) {
            for (int j = i + 2; j + 1 < segmentsA.size(); j += 2) {
                WorldPoint2d hit = SegmentMath.intersection(
                        segmentsA.get(i), segmentsA.get(i + 1), segmentsA.get(j), segmentsA.get(j + 1));
                if (hit != null && hit.x() >= minX && hit.x() <= maxX && hit.z() >= minZ && hit.z() <= maxZ) {
                    found.add(hit);
                }
            }
        }
        return Collections.unmodifiableList(found);
    }

    @Override
    public WorldPoint2d grandConvergence() {
        return geometry.convergence();
    }

    private double nearestDistance(double x, double z) {
        double best = Double.MAX_VALUE;
        for (List<WorldPoint2d> arm : geometry.arms()) {
            for (int i = 1; i < arm.size(); i++) {
                WorldPoint2d a = arm.get(i - 1);
                WorldPoint2d b = arm.get(i);
                double t = SegmentMath.projectionParameter(a, b, x, z);
                WorldPoint2d closest = SegmentMath.pointAt(a, b, t);
                best = Math.min(best, Math.hypot(closest.x() - x, closest.z() - z));
            }
        }
        return best;
    }

    /** Every segment as a flat endpoint-pair list, so crossings between arms are found too. */
    private List<WorldPoint2d> flatten() {
        List<WorldPoint2d> flat = new ArrayList<>();
        for (List<WorldPoint2d> arm : geometry.arms()) {
            for (int i = 1; i < arm.size(); i++) {
                flat.add(arm.get(i - 1));
                flat.add(arm.get(i));
            }
        }
        return flat;
    }
}

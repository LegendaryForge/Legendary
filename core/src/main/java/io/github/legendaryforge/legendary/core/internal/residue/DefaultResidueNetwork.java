package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
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
    private final double nexusRadius;
    private final double nexusWeight;

    /**
     * @param elementId which element's current this is; required, and deliberately not defaulted —
     *     an overload without it would restore the convention that nothing enforced
     */
    public DefaultResidueNetwork(long worldSeed, ResourceId elementId, CurrentParameters parameters) {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(parameters, "parameters");
        this.geometry = new CurrentGeometry(worldSeed, elementId, parameters);
        this.influenceRadius = parameters.influenceRadius();
        this.nexusRadius = parameters.nexusRadius();
        this.nexusWeight = parameters.nexusWeight();
    }

    @Override
    public double densityAt(double x, double z) {
        double currentTerm = clamp01(1.0 - nearestDistance(x, z) / influenceRadius);
        if (currentTerm <= 0.0) {
            return 0.0;
        }
        double nexusTerm = clamp01(1.0 - nearestNexusDistance(x, z) / nexusRadius);
        // At a nexus both terms are 1, so density is exactly 1.0 and nowhere else. Moving along the
        // current holds currentTerm at 1 while nexusTerm falls; moving off it lowers both. The
        // maximum is therefore strict rather than a shoulder, which is the gradient a player walks.
        return (1.0 - nexusWeight) * currentTerm + nexusWeight * nexusTerm;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    private double nearestNexusDistance(double x, double z) {
        double best = Double.MAX_VALUE;
        for (WorldPoint2d n : geometry.crossings()) {
            best = Math.min(best, Math.hypot(n.x() - x, n.z() - z));
        }
        return best;
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
    public List<WorldPoint2d> nexusesWithin(double minX, double minZ, double maxX, double maxZ) {
        if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)) {
            throw new IllegalArgumentException(
                    "bounds must be finite: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        }
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("inverted bounds: " + minX + "," + minZ + " to " + maxX + "," + maxZ);
        }

        List<WorldPoint2d> found = new ArrayList<>();
        for (WorldPoint2d hit : geometry.crossings()) {
            if (hit.x() >= minX && hit.x() <= maxX && hit.z() >= minZ && hit.z() <= maxZ) {
                found.add(hit);
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
}

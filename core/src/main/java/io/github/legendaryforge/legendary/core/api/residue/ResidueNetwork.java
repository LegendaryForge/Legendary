package io.github.legendaryforge.legendary.core.api.residue;

import java.util.List;
import java.util.Optional;

/**
 * A deterministic residue current network for one element.
 *
 * <p>Pure: every result is a function of the world seed, the parameters and the queried position.
 * Nothing is placed, persisted, or read from the world, which is what lets this exist without any
 * worldgen dependency.
 */
public interface ResidueNetwork {

    /** Residue density in {@code [0,1]}: 1 on a current, falling to 0 at the influence radius. */
    double densityAt(double x, double z);

    /**
     * Direction of flow at this position, or empty when out of influence.
     *
     * <p>Flow points <em>toward</em> the Grand Convergence, so following it upstream always leads
     * there. Use {@link FlowVector#opposite()} for downstream.
     */
    Optional<FlowVector> flowAt(double x, double z);

    /**
     * Nexuses — proper self-crossings of the network — whose positions lie within the bounds.
     *
     * <p>A nexus is the natural crossing this computes. A Circle is a man-made structure that may
     * stand at one; the network knows nothing about those.
     */
    List<WorldPoint2d> nexusesWithin(double minX, double minZ, double maxX, double maxZ);

    /** The single point where every element's current meets. Identical for all elements in a world. */
    WorldPoint2d grandConvergence();
}

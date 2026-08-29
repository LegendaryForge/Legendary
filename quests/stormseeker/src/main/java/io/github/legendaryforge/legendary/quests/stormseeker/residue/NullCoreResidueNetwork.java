package io.github.legendaryforge.legendary.quests.stormseeker.residue;

import io.github.legendaryforge.legendary.core.api.residue.FlowVector;
import io.github.legendaryforge.legendary.core.api.residue.ResidueNetwork;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightning's wound: residue density falls to zero at the Grand Convergence and rises to normal at
 * the null radius.
 *
 * <p>This is <strong>not physics and not element-neutral</strong>, which is why it lives in the
 * questline rather than in {@code :core}. Lightning is the one element whose lore says its source
 * <em>"is nowhere to be found"</em>; the other five keep rich cores. A player approaching the
 * convergence sees five colours intensifying around them while yellow thins to nothing — the wound
 * is legible only because the crowd is there. Each future questline decides for itself whether its
 * own element is whole.
 *
 * <p>It also answers a real defect. Crystals sit on roughly 34x more ground near the convergence
 * than in the far field, so gathering at the centre out-competed prospecting for a good nexus.
 * Emptying Lightning's core removes that competition for this element without touching the others.
 *
 * <h2>What is deliberately <em>not</em> suppressed</h2>
 *
 * <p>Flow is untouched, and that is the point rather than an oversight: literacy teaches <em>more
 * crystals, more power</em> for two acts, and then the needle points upstream while the crystals die
 * out. The instrument says forward, the land says nothing lives here — no text required.
 *
 * <p>Nexuses are not removed from {@link #nexusesWithin} either. A nexus inside the null radius is
 * suppressed, not deleted, and remains a <em>local</em> maximum of its neighbourhood — see
 * {@code NullCoreResidueNetworkTest}, which owns that proof. What stops being true is the
 * <em>global</em> reading of N6: density {@code 1.0} no longer identifies a nexus everywhere,
 * because a nexus inside the null radius cannot reach 1.0.
 */
public final class NullCoreResidueNetwork implements ResidueNetwork {

    /**
     * 200 world units.
     *
     * <p>Chosen against two scans over 500 seeds at the {@code 6x240} target: it removes the worst
     * of the harvest competition (10.5% of nexuses fall inside it) while leaving <strong>no world
     * without a nexus</strong>. 300 remains available if play data says the core is still too rich.
     * <strong>400 and beyond reintroduces N8</strong> — worlds with no reachable nexus at all — and
     * is not available at any tuning.
     */
    public static final double LIGHTNING_NULL_RADIUS = 200.0;

    private final ResidueNetwork delegate;
    private final double nullRadius;
    private final WorldPoint2d convergence;

    public NullCoreResidueNetwork(ResidueNetwork delegate, double nullRadius) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (!(nullRadius > 0.0) || !Double.isFinite(nullRadius)) {
            throw new IllegalArgumentException("nullRadius must be positive and finite, got " + nullRadius);
        }
        this.nullRadius = nullRadius;
        this.convergence = delegate.grandConvergence();
    }

    /** The shipped Lightning configuration. */
    public static NullCoreResidueNetwork lightning(ResidueNetwork delegate) {
        return new NullCoreResidueNetwork(delegate, LIGHTNING_NULL_RADIUS);
    }

    /**
     * Fraction of the underlying density that survives at this position: 0 at the convergence,
     * rising linearly to 1 at the null radius.
     *
     * <p>The design states the endpoints ("falls to zero near the Grand Convergence, rising to
     * normal at the null radius") but not the curve between them. Linear is chosen to match the
     * other two ramps in the density function, both of which are linear in distance.
     */
    private double survival(double x, double z) {
        double d = Math.hypot(x - convergence.x(), z - convergence.z());
        return d >= nullRadius ? 1.0 : d / nullRadius;
    }

    @Override
    public double densityAt(double x, double z) {
        return survival(x, z) * delegate.densityAt(x, z);
    }

    @Override
    public Optional<FlowVector> flowAt(double x, double z) {
        return delegate.flowAt(x, z);
    }

    @Override
    public List<WorldPoint2d> nexusesWithin(double minX, double minZ, double maxX, double maxZ) {
        return delegate.nexusesWithin(minX, minZ, maxX, maxZ);
    }

    @Override
    public WorldPoint2d grandConvergence() {
        return delegate.grandConvergence();
    }
}

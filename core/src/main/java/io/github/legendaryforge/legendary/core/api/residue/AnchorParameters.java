package io.github.legendaryforge.legendary.core.api.residue;

/**
 * The anchor model's dials.
 *
 * <p>Separate from {@link LatticeParameters} rather than an extension of it, because the volume
 * model does not use that record's scoring weights at all — {@code fluidWeight},
 * {@code environmentWeight}, {@code skyWeight} and {@code qualifyingScore} belong to the
 * weighted-sum scoring that anchor-density design §2 replaced with counting.
 *
 * <p><strong>One suppression radius, not five.</strong> The per-element differences come from each
 * grounding's predicate and from {@code minimumStrengthFraction}, not from a radius tuned per
 * element. Uniform spacing is what makes currents connectable and crystal density predictable, and
 * it is the only dial that sets how far a player walks between crystals.
 *
 * @param cellSize lattice cell edge in blocks; one candidate per cell. Must not exceed
 *     {@code suppressionRadius}, or candidates would be sparser than anchors and selection would
 *     have nothing to choose between
 * @param jitterFraction how far a candidate may sit from its cell centre, as a fraction of
 *     {@code cellSize} per axis, in {@code [0, 0.5]}
 * @param suppressionRadius a site must beat everything within this horizontal distance to become an
 *     anchor. Sets anchor density at roughly one per {@code suppressionRadius²} of qualifying
 *     terrain
 * @param measurementRadius horizontal half-width of the window strength is counted over
 * @param sampleStep spacing between sampled positions in all three axes
 * @param minimumStrengthFraction absolute floor, as a fraction of samples taken. A region whose best
 *     candidate falls below this gets <strong>no anchor for that element</strong>, which is how
 *     rarity handles itself: nowhere near lava produces no Fire anchor, with nothing tuned to say so
 * @param influenceRadius {@code w} — crystals may spawn within this distance of a current
 * @param crossingTolerance {@code D} — two currents passing within this distance cross. Bounded by
 *     {@code influenceRadius}: beyond it a geode could form outside both crystal bands, in ground
 *     showing nothing that explains it
 */
public record AnchorParameters(
        int cellSize,
        double jitterFraction,
        int suppressionRadius,
        int measurementRadius,
        int sampleStep,
        double minimumStrengthFraction,
        int influenceRadius,
        int crossingTolerance) {

    /** Hytale's world height, y = 0..319, confirmed by the engine's own {@code canHoldY} predicate. */
    private static final int WORLD_HEIGHT = 320;

    public AnchorParameters {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive, got " + cellSize);
        }
        if (jitterFraction < 0 || jitterFraction > 0.5) {
            throw new IllegalArgumentException("jitterFraction must be in [0, 0.5], got " + jitterFraction);
        }
        if (suppressionRadius <= 0) {
            throw new IllegalArgumentException("suppressionRadius must be positive, got " + suppressionRadius);
        }
        if (cellSize > suppressionRadius) {
            throw new IllegalArgumentException("cellSize " + cellSize + " exceeds suppressionRadius "
                    + suppressionRadius
                    + ", so candidates would be sparser than anchors and selection would have no choice");
        }
        if (measurementRadius < 0) {
            throw new IllegalArgumentException("measurementRadius must be non-negative, got " + measurementRadius);
        }
        if (sampleStep <= 0) {
            throw new IllegalArgumentException("sampleStep must be positive, got " + sampleStep);
        }
        if (!(minimumStrengthFraction >= 0) || minimumStrengthFraction > 1) {
            throw new IllegalArgumentException(
                    "minimumStrengthFraction must be in [0,1], got " + minimumStrengthFraction);
        }
        if (influenceRadius <= 0) {
            throw new IllegalArgumentException("influenceRadius must be positive, got " + influenceRadius);
        }
        if (crossingTolerance <= 0) {
            throw new IllegalArgumentException("crossingTolerance must be positive, got " + crossingTolerance);
        }
        if (crossingTolerance > influenceRadius) {
            throw new IllegalArgumentException("crossingTolerance " + crossingTolerance + " exceeds influenceRadius "
                    + influenceRadius + ", which would admit a geode outside both crystal bands");
        }
    }

    /** Hytale's world height in blocks. A full-column window spans {@code 0 .. worldHeight() - 1}. */
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    /**
     * The values decided in {@code 2026-10-22-residue-sizing-decision.md}.
     *
     * <p>{@code suppressionRadius} 512 derives from one provisional operator judgement — about 15%
     * of the world near some current — through {@code R = 10w/C}. {@code minimumStrengthFraction} is
     * the one value here with no stated basis: it exists to stop a region with a trace of an element
     * minting an anchor, and 0.01 is a starting guess to be moved once the probe shows what real
     * terrain produces.
     */
    public static AnchorParameters defaults() {
        return new AnchorParameters(256, 0.35, 512, 128, 8, 0.01, 8, 8);
    }
}

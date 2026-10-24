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
 * <p><strong>The strength floor is not here.</strong> It moved to
 * {@link ElementGrounding#minimumStrengthSamples()} on 2026-10-23, because a single floor expressed
 * as a fraction of the window could not serve both an element that fills a fifth of a column and one
 * that runs through it as a river. See that method for the measurement that forced it. What survives
 * of the old field's intent is its description of the job: admit a lake, an ocean, a caldera — not a
 * 2x2 puddle.
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
        // A measurement reports samplesTaken as an int, so a window with more positions than an int
        // can hold could never report itself fully loaded. Staged so no product overflows the long
        // doing the checking: perAxis within an int bounds perAxis squared within a long, and
        // perAxis squared within an int bounds the whole product.
        long perAxis = horizontalPositions(measurementRadius, sampleStep);
        if (perAxis > Integer.MAX_VALUE
                || perAxis * perAxis > Integer.MAX_VALUE
                || perAxis * perAxis * verticalPositions(sampleStep) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("measurementRadius " + measurementRadius + " at sampleStep "
                    + sampleStep + " describes a window of more sample positions than an int can count, "
                    + "so no measurement of it could report itself fully loaded");
        }
    }

    /** Hytale's world height in blocks. A full-column window spans {@code 0 .. worldHeight() - 1}. */
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    /**
     * Sample positions one candidate's window holds when every one of them is loaded.
     *
     * <p>The denominator that makes a measurement's {@code samplesTaken} legible as a <em>fraction
     * of the window</em> rather than as a bare count. A caller compares the two to answer the one
     * question a count alone cannot: whether a low strength means the element is scarce there, or
     * means most of the window was never resident. Those are different answers, and on a live
     * server with one connected player the second is the common case.
     *
     * <p>Counts positions, so it truncates: the scan stops at the last offset within
     * {@code measurementRadius}, and a radius that is not a whole number of steps contributes no
     * extra position. Rounding up would make every fully-loaded window look partial.
     *
     * <p><strong>Derived here, iterated elsewhere.</strong> {@code VolumeStrength} walks these same
     * bounds from its own arithmetic, and the two disagreeing would fail nothing — it would quietly
     * report every full window as partial, or the reverse. They are pinned together by a test
     * ({@code aFullyLoadedWindowTakesExactlyWindowSampleCountSamples}), not by this sentence.
     */
    public int windowSampleCount() {
        long perAxis = horizontalPositions(measurementRadius, sampleStep);
        return (int) (perAxis * perAxis * verticalPositions(sampleStep));
    }

    /** Sampled offsets in {@code -radius .. radius} at {@code step}, both ends included. */
    private static long horizontalPositions(int radius, int step) {
        return 2L * radius / step + 1;
    }

    /** Sampled levels in {@code 0 .. WORLD_HEIGHT - 1} at {@code step}. */
    private static long verticalPositions(int step) {
        return (long) (WORLD_HEIGHT - 1) / step + 1;
    }

    /**
     * The values decided in {@code 2026-10-22-residue-sizing-decision.md}.
     *
     * <p>{@code suppressionRadius} 512 derives from one provisional operator judgement — about 15%
     * of the world near some current — through {@code R = 10w/C}. The strength floor that used to
     * live here, and was the one value with no stated basis, is now per element on
     * {@link ElementGrounding}.
     */
    public static AnchorParameters defaults() {
        return new AnchorParameters(256, 0.35, 512, 128, 8, 8, 8);
    }
}

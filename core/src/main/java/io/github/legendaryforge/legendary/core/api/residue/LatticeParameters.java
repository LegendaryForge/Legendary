package io.github.legendaryforge.legendary.core.api.residue;

/**
 * The candidate lattice's dials, and the scoring weights.
 *
 * <p><strong>Cell size is the one parameter with a referent</strong>, which the retired
 * single-convergence model's numbers never had: it is "how far should a player walk between
 * nexuses". That is a play-feel question with an answer, rather than a free number fitted to a
 * figure from a harness that no longer exists.
 *
 * <p><strong>Known coupling, recorded rather than solved.</strong> Cell size controls total supply
 * <em>and</em> mixed-nexus frequency — how often a candidate's neighbourhood contains two elements
 * is a function of cell size against biome scale. One dial, two outcomes. They may need decoupling;
 * that is §9 question 7 and it is open.
 *
 * @param cellSize lattice cell edge in blocks; one candidate per cell
 * @param jitterFraction how far a candidate may sit from its cell centre, as a fraction of
 *     {@code cellSize} in each axis. 0 is a rigid grid, which reads as artificial; 0.5 can place a
 *     candidate on a cell boundary
 * @param neighbourhoodRadius how far around a candidate terrain is examined, in blocks. This bounds
 *     the cost: scoring reads a neighbourhood, it does not scan a world
 * @param sampleStep spacing between sampled positions inside the neighbourhood. Larger is cheaper
 *     and can miss a narrow feature such as a river
 * @param verticalSpan how far above and below the surface to sample, in blocks
 * @param fluidWeight weight per distinct qualifying fluid found. Highest of the three: a fluid is a
 *     specific block the player can see, which is the strongest evidence of a site's character
 * @param environmentWeight weight for a qualifying environment at the candidate
 * @param skyWeight weight for sky light falling in a rule's range
 * @param qualifyingScore the score at or above which a typing counts as a real site. Two typings
 *     over this threshold is a mixed site
 */
public record LatticeParameters(
        int cellSize,
        double jitterFraction,
        int neighbourhoodRadius,
        int sampleStep,
        int verticalSpan,
        double fluidWeight,
        double environmentWeight,
        double skyWeight,
        double qualifyingScore) {

    public LatticeParameters {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive, got " + cellSize);
        }
        if (jitterFraction < 0 || jitterFraction > 0.5) {
            throw new IllegalArgumentException("jitterFraction must be in [0, 0.5], got " + jitterFraction);
        }
        if (neighbourhoodRadius < 0) {
            throw new IllegalArgumentException("neighbourhoodRadius must be non-negative, got " + neighbourhoodRadius);
        }
        if (sampleStep <= 0) {
            throw new IllegalArgumentException("sampleStep must be positive, got " + sampleStep);
        }
        if (verticalSpan < 0) {
            throw new IllegalArgumentException("verticalSpan must be non-negative, got " + verticalSpan);
        }
    }

    /**
     * Starting values for the first probe run, chosen to be legible rather than tuned.
     *
     * <p>A 256-block cell is roughly a two-minute walk; a 24-block neighbourhood at step 8 is 7x7x7
     * reads per candidate at most, which at the measured 99 ns environment and 34 ns sky-light costs
     * microseconds. These are the numbers the first run exists to argue with.
     */
    public static LatticeParameters defaults() {
        return new LatticeParameters(256, 0.35, 24, 8, 16, 2.0, 1.0, 1.0, 2.0);
    }
}

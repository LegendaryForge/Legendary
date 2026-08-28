package io.github.legendaryforge.legendary.core.api.residue;

/**
 * The complete per-element tuning surface for one current.
 *
 * @param armCount how many arms radiate from the Grand Convergence
 * @param stepsPerArm segments per arm; with {@code stepLength}, sets how far a current reaches
 * @param stepLength world units per segment
 * @param headingJitter maximum radians a heading may turn per step; 0 yields straight arms
 * @param influenceRadius world units beyond which density is 0 and flow is absent
 */
public record CurrentParameters(
        int armCount, int stepsPerArm, double stepLength, double headingJitter, double influenceRadius) {

    public CurrentParameters {
        if (armCount <= 0) {
            throw new IllegalArgumentException("armCount must be positive, got " + armCount);
        }
        if (stepsPerArm <= 0) {
            throw new IllegalArgumentException("stepsPerArm must be positive, got " + stepsPerArm);
        }
        if (!(stepLength > 0.0) || !Double.isFinite(stepLength)) {
            throw new IllegalArgumentException("stepLength must be positive and finite, got " + stepLength);
        }
        if (!(headingJitter >= 0.0) || !Double.isFinite(headingJitter)) {
            throw new IllegalArgumentException("headingJitter must be non-negative and finite, got " + headingJitter);
        }
        if (!(influenceRadius > 0.0) || !Double.isFinite(influenceRadius)) {
            throw new IllegalArgumentException("influenceRadius must be positive and finite, got " + influenceRadius);
        }
    }

    /**
     * Placeholder values that produce a visibly meandering, self-crossing network for tests and
     * development. These are NOT tuned content values — N1 of the design spec is explicit that the
     * real figure is set against play data, per element, in the questline module.
     */
    public static CurrentParameters defaults() {
        return new CurrentParameters(4, 160, 16.0, 0.35, 24.0);
    }
}

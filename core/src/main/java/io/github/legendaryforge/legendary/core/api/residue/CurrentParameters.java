package io.github.legendaryforge.legendary.core.api.residue;

/**
 * The complete per-element tuning surface for one current.
 *
 * @param armCount how many arms radiate from the Grand Convergence
 * @param stepsPerArm segments per arm; with {@code stepLength}, sets how far a current reaches
 * @param stepLength world units per segment
 * @param headingJitter maximum radians a heading may turn per step; 0 yields straight arms
 * @param influenceRadius world units beyond which density is 0 and flow is absent
 * @param nexusWeight share of density owned by nexus proximity; {@code 0 <= nexusWeight < 1}
 */
public record CurrentParameters(
        int armCount,
        int stepsPerArm,
        double stepLength,
        double headingJitter,
        double influenceRadius,
        double nexusWeight) {

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
        // 1.0 is excluded deliberately: at 1.0 the current term vanishes and ordinary current reads
        // as empty ground, which destroys the literacy the whole design rests on. 0.0 is permitted
        // and reproduces the unpeaked field exactly, which makes it a regression anchor and the
        // correct setting for a current that deliberately has no nexuses.
        if (!(nexusWeight >= 0.0) || !(nexusWeight < 1.0)) {
            throw new IllegalArgumentException("nexusWeight must be in [0,1), got " + nexusWeight);
        }
    }

    /**
     * World units beyond which a nexus contributes nothing: half the current's own influence radius.
     *
     * <p>Derived rather than stored. The peak must sit <em>inside</em> the current's influence band,
     * or density would rise before the player is on the current at all; and it must be narrow enough
     * to read as a peak rather than swamp the arm it sits on. Deriving it means the ratio survives a
     * retune of {@code influenceRadius}, which a stored constant would not. The 1:2 ratio itself is
     * provisional.
     */
    public double nexusRadius() {
        return influenceRadius / 2.0;
    }

    /**
     * Placeholder values that produce a visibly meandering, self-crossing network for tests and
     * development. These are NOT tuned content values — N1 of the design spec is explicit that the
     * real figure is set against play data, per element, in the questline module.
     */
    public static CurrentParameters defaults() {
        return new CurrentParameters(4, 160, 16.0, 0.35, 24.0, 0.3);
    }
}

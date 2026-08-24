package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

/**
 * Tuning knobs for Phase C mechanics.
 *
 * <p>This lives at the content layer. Values are intentionally conservative defaults.
 * The important contract is: Systems read these values, not hardcoded thresholds.
 */
public record FlowingTrialTuning(
        double emergeThreshold,
        double activeThreshold,
        int stableTicksRequired,
        double alignedThreshold,
        double misalignedThreshold,
        double progressGainRate,
        double progressDecayRate,
        double completionThreshold,
        double hintDecayRate) {

    public static FlowingTrialTuning defaults() {
        return new FlowingTrialTuning(10.0, 20.0, 30, 0.70, 0.30, 1.0, 0.5, 100.0, 0.25);
    }
}

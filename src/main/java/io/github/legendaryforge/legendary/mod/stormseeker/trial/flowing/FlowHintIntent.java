package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

/**
 * Presentation-only intent derived from alignment + movement.
 * This is not authoritative state.
 */
public record FlowHintIntent(double intensity, double stability, double directionHintStrength) {

    public static FlowHintIntent zero() {
        return new FlowHintIntent(0.0, 0.0, 0.0);
    }
}

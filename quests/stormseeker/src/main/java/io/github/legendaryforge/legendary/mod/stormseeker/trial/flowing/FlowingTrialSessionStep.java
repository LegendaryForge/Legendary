package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

/**
 * Result of a single Flowing Trial session step.
 *
 * <p>This is the unit the engine adapter can consume: hint intent + completion + whether a sigil
 * was granted on this tick.
 */
public record FlowingTrialSessionStep(
        FlowingTrialStatus status, FlowHintIntent hint, boolean completedThisTick, boolean sigilGrantedThisTick) {}

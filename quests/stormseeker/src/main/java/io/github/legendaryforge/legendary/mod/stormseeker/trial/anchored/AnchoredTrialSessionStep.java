package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.Objects;

/**
 * One tick result of Anchored Trial evaluation.
 *
 * <p>Phase 2E initial mechanic (minimal, deterministic):
 * <ul>
 *   <li>If the player is stationary for N consecutive ticks, grant Sigil B.</li>
 *   <li>Stationary = !sample.moving().</li>
 * </ul>
 *
 * <p>This is intentionally simple scaffolding that can be replaced by richer mechanics later
 * without changing host-side milestone semantics.
 */
public record AnchoredTrialSessionStep(boolean stationaryThisTick, int stationaryStreak, boolean sigilGrantedThisTick) {

    public static AnchoredTrialSessionStep from(MotionSample sample, int stationaryStreak, boolean granted) {
        Objects.requireNonNull(sample, "sample");
        return new AnchoredTrialSessionStep(!sample.moving(), stationaryStreak, granted);
    }
}

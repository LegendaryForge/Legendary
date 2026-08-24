package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.Objects;

/**
 * Per-player Anchored Trial evaluation session.
 *
 * <p>Owns only ephemeral evaluation state (streak counters). Authoritative quest state remains
 * in {@link StormseekerProgress}.
 */
public final class AnchoredTrialSession {

    // Minimal Phase 2E mechanic: hold still for this many consecutive ticks.
    // Chosen to be deterministic and easily testable; tune later during gameplay iteration.
    public static final int REQUIRED_STATIONARY_TICKS = 40;

    private final StormseekerProgress progress;
    private int stationaryStreak;

    public AnchoredTrialSession(StormseekerProgress progress) {
        this.progress = Objects.requireNonNull(progress, "progress");
        this.stationaryStreak = 0;
    }

    public StormseekerProgress progress() {
        return progress;
    }

    /**
     * Advances the anchored trial by one tick using the provided motion sample.
     *
     * <p>Grants Sigil B when the stationary streak reaches {@link #REQUIRED_STATIONARY_TICKS}.
     * This call is idempotent once Sigil B is granted.
     */
    public AnchoredTrialSessionStep step(MotionSample sample) {
        Objects.requireNonNull(sample, "sample");

        if (progress.hasSigilB()) {
            // Already complete; keep streak stable but do not grant again.
            stationaryStreak = 0;
            return AnchoredTrialSessionStep.from(sample, stationaryStreak, false);
        }

        if (sample.moving()) {
            stationaryStreak = 0;
            return AnchoredTrialSessionStep.from(sample, stationaryStreak, false);
        }

        stationaryStreak++;

        boolean granted = false;
        if (stationaryStreak >= REQUIRED_STATIONARY_TICKS) {
            // Gameplay-side mutation: grant Sigil B (host tick will observe and emit milestones).
            granted = progress.grantSigilB();
            stationaryStreak = 0; // reset after completion edge
        }

        return AnchoredTrialSessionStep.from(sample, stationaryStreak, granted);
    }
}

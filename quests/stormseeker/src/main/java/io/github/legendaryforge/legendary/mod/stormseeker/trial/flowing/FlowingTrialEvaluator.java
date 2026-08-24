package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

/**
 * Pure evaluator for Phase C mechanics (detection, readability, completion).
 *
 * <p>Engine/ECS integration should call this once per tick per relevant player.
 */
public final class FlowingTrialEvaluator {

    private FlowingTrialEvaluator() {}

    public static FlowingTrialStepResult step(FlowingTrialState in, MotionSample raw, FlowingTrialTuning t) {
        FlowingTrialState s = in.copy();

        if (s.status == FlowingTrialStatus.COMPLETED) {
            // Trial is done; hints should fall to zero unless you want a brief post-complete flourish.
            return new FlowingTrialStepResult(s, FlowHintIntent.zero(), false);
        }

        MotionSample sample = raw.moving() ? raw : new MotionSample(0, 0, 0, false);
        MotionSample dir = sample.normalizedOrZero();

        // --- Detection: build a candidate score from sustained movement + coherence ---
        double coherence = computeCoherence(s, dir);
        // Reward coherent movement; penalize idling and chaotic direction changes.
        double moveReward = sample.moving() ? (0.75 + 0.25 * coherence) : 0.0;
        double chaosPenalty = sample.moving() ? (1.0 - coherence) * 0.35 : 0.20;

        s.candidateScore = clamp0(s.candidateScore + moveReward - chaosPenalty);

        // Maintain stable ticks when the player is "in flow".
        if (sample.moving() && coherence >= 0.60) {
            s.stableTicks += 1;
        } else {
            s.stableTicks = Math.max(0, s.stableTicks - 1);
        }

        // Status transitions: INACTIVE -> EMERGING -> ACTIVE (regression allowed)
        if (s.status == FlowingTrialStatus.INACTIVE && s.candidateScore >= t.emergeThreshold()) {
            s.status = FlowingTrialStatus.EMERGING;
        }
        if (s.status == FlowingTrialStatus.EMERGING
                && s.candidateScore >= t.activeThreshold()
                && s.stableTicks >= t.stableTicksRequired()) {
            s.status = FlowingTrialStatus.ACTIVE;
        }
        if (s.status == FlowingTrialStatus.ACTIVE && s.candidateScore < t.emergeThreshold() * 0.75) {
            // soft regression only; no hard fail
            s.status = FlowingTrialStatus.EMERGING;
        }

        // --- Alignment: compute alignment score only once EMERGING/ACTIVE ---
        if (s.status == FlowingTrialStatus.INACTIVE) {
            s.alignmentScore = approach(s.alignmentScore, 0.0, 0.15);
        } else {
            // Treat coherence as the primary signal; smooth over time.
            s.alignmentScore = approach(s.alignmentScore, coherence, 0.20);
        }

        if (s.alignmentScore >= t.alignedThreshold()) {
            s.alignment = FlowAlignment.ALIGNED;
        } else if (s.alignmentScore <= t.misalignedThreshold()) {
            s.alignment = FlowAlignment.MISALIGNED;
        } else {
            s.alignment = FlowAlignment.NEUTRAL;
        }

        // --- Readability intent: only meaningful while moving ---
        FlowHintIntent hint;
        if (!sample.moving() || s.status == FlowingTrialStatus.INACTIVE) {
            hint = FlowHintIntent.zero();
        } else {
            double base =
                    switch (s.alignment) {
                        case ALIGNED -> 1.0;
                        case NEUTRAL -> 0.5;
                        case MISALIGNED -> 0.15;
                    };
            // intensity increases with alignment score; stability drops when misaligned
            double intensity = clamp01(base * s.alignmentScore);
            double stability = clamp01((s.alignment == FlowAlignment.MISALIGNED) ? 0.25 : 0.85);
            double dirStrength = clamp01((s.alignment == FlowAlignment.ALIGNED) ? 1.0 : 0.35);
            // decay factor is applied by the renderer/integration, but we expose intent now
            hint = new FlowHintIntent(intensity, stability, dirStrength);
        }

        // --- Completion: consistency-based, no location ---
        boolean completedThisTick = false;
        if (s.status == FlowingTrialStatus.ACTIVE) {
            if (sample.moving() && s.alignment == FlowAlignment.ALIGNED) {
                s.progress += t.progressGainRate();
            } else {
                s.progress = Math.max(0.0, s.progress - t.progressDecayRate());
            }

            if (s.progress >= t.completionThreshold()) {
                s.status = FlowingTrialStatus.COMPLETED;
                completedThisTick = true;
            }
        }

        // Update last direction memory at end (only if moving)
        if (sample.moving()) {
            s.hasLastDir = true;
            s.lastDirX = dir.dx();
            s.lastDirY = dir.dy();
            s.lastDirZ = dir.dz();
        }

        return new FlowingTrialStepResult(s, hint, completedThisTick);
    }

    private static double computeCoherence(FlowingTrialState s, MotionSample dir) {
        if (!dir.moving()) {
            return 0.0;
        }
        if (!s.hasLastDir) {
            return 0.75; // first sample gets neutral-positive coherence
        }
        double dot = s.lastDirX * dir.dx() + s.lastDirY * dir.dy() + s.lastDirZ * dir.dz();
        // dot in [-1,1], map to [0,1]
        return clamp01((dot + 1.0) * 0.5);
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static double clamp0(double v) {
        return Math.max(0.0, v);
    }

    private static double approach(double current, double target, double maxDelta) {
        double delta = target - current;
        if (delta > maxDelta) return current + maxDelta;
        if (delta < -maxDelta) return current - maxDelta;
        return target;
    }
}

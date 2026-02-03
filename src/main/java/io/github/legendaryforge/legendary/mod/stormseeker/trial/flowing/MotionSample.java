package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

/**
 * Per-tick motion input sample for Phase C trial evaluation.
 *
 * <p>Engine/ECS integration will translate actual movement into these samples.
 */
public record MotionSample(double dx, double dy, double dz, boolean moving) {

    /** Convenience: a non-moving sample. */
    public static MotionSample idle() {
        return new MotionSample(0, 0, 0, false);
    }

    /** Convenience: a moving sample with the provided delta vector. */
    public static MotionSample moving(double dx, double dy, double dz) {
        return new MotionSample(dx, dy, dz, true);
    }

    public double distance() {
        double d2 = dx * dx + dy * dy + dz * dz;
        return Math.sqrt(d2);
    }

    public double norm() {
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public MotionSample normalizedOrZero() {
        double n = norm();
        if (n <= 1e-9) {
            return new MotionSample(0, 0, 0, false);
        }
        return new MotionSample(dx / n, dy / n, dz / n, moving);
    }
}

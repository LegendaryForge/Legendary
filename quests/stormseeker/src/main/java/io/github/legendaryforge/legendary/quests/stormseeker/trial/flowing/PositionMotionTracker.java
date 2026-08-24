package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

/**
 * Turns a stream of absolute player positions into per-tick {@link MotionSample} deltas.
 *
 * <p>Extracted from a private nested class inside {@code mod/hytale}'s
 * HytaleStormseekerHost. It touches no platform type — the host reads coordinates from the
 * engine and passes plain doubles here — but living in that module meant it could not be
 * compiled, let alone tested, on any machine without the game jar.
 *
 * <p>Not thread-safe. The host holds one instance per player and drives it from the tick
 * loop, which is the same single-threaded contract the nested class had.
 */
public final class PositionMotionTracker {

    private static final MotionSample ZERO_MOTION = new MotionSample(0, 0, 0, false);

    private final double movingThreshold;

    private double prevX = Double.NaN;
    private double prevY = Double.NaN;
    private double prevZ = Double.NaN;
    private MotionSample lastMotion = ZERO_MOTION;

    public PositionMotionTracker(double movingThreshold) {
        this.movingThreshold = movingThreshold;
    }

    /**
     * Records a position and returns the resulting motion sample.
     *
     * <p>The first call only seeds the previous position and reports zero motion: with no
     * prior sample a delta is meaningless, and treating the absolute position as one would
     * make every player read as moving the moment they connect.
     */
    public MotionSample accept(double x, double y, double z) {
        if (Double.isNaN(prevX)) {
            prevX = x;
            prevY = y;
            prevZ = z;
            lastMotion = ZERO_MOTION;
            return lastMotion;
        }

        double dx = x - prevX;
        double dy = y - prevY;
        double dz = z - prevZ;
        boolean moving = Math.sqrt(dx * dx + dy * dy + dz * dz) > movingThreshold;

        lastMotion = new MotionSample(dx, dy, dz, moving);
        prevX = x;
        prevY = y;
        prevZ = z;
        return lastMotion;
    }

    /** The most recent sample, or zero motion if none has been accepted. */
    public MotionSample latest() {
        return lastMotion;
    }
}

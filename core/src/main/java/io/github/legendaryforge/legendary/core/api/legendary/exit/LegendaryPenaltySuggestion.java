package io.github.legendaryforge.legendary.core.api.legendary.exit;

import java.time.Duration;
import java.util.Objects;

/**
 * Advisory penalty suggestion emitted when an encounter ends.
 *
 * <p>Platform adapters decide how (or whether) to implement the penalty (debuff, lockout, etc.).
 */
public final class LegendaryPenaltySuggestion {

    private final String key;
    private final Duration duration;

    private LegendaryPenaltySuggestion(String key, Duration duration) {
        this.key = Objects.requireNonNull(key, "key");
        this.duration = Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    /**
     * A stable key describing the penalty intent (e.g., "coward_exit").
     */
    public String key() {
        return key;
    }

    /** Suggested penalty duration (platform may clamp/ignore). */
    public Duration duration() {
        return duration;
    }

    public static LegendaryPenaltySuggestion of(String key, Duration duration) {
        return new LegendaryPenaltySuggestion(key, duration);
    }

    @Override
    public String toString() {
        return key + " for " + duration;
    }
}

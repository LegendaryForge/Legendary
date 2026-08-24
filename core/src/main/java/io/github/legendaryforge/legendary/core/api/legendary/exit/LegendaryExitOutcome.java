package io.github.legendaryforge.legendary.core.api.legendary.exit;

import java.util.Objects;
import java.util.Optional;

/**
 * Describes how a Legendary Encounter instance ended and what should happen next.
 */
public final class LegendaryExitOutcome {

    private final LegendaryExitReason reason;
    private final boolean resetImmediately;
    private final LegendaryPenaltySuggestion penaltySuggestion;

    private LegendaryExitOutcome(
            LegendaryExitReason reason, boolean resetImmediately, LegendaryPenaltySuggestion penaltySuggestion) {
        this.reason = Objects.requireNonNull(reason, "reason");
        this.resetImmediately = resetImmediately;
        this.penaltySuggestion = penaltySuggestion;
    }

    public static LegendaryExitOutcome resetNow(LegendaryExitReason reason) {
        return new LegendaryExitOutcome(reason, true, null);
    }

    public static LegendaryExitOutcome resetNowWithPenalty(
            LegendaryExitReason reason, LegendaryPenaltySuggestion penaltySuggestion) {
        Objects.requireNonNull(penaltySuggestion, "penaltySuggestion");
        return new LegendaryExitOutcome(reason, true, penaltySuggestion);
    }

    public LegendaryExitReason reason() {
        return reason;
    }

    /** Whether the encounter instance should reset immediately (default true for Legendary encounters). */
    public boolean resetImmediately() {
        return resetImmediately;
    }

    /** Optional advisory penalty to apply to each participant player. */
    public Optional<LegendaryPenaltySuggestion> penaltySuggestion() {
        return Optional.ofNullable(penaltySuggestion);
    }

    @Override
    public String toString() {
        return penaltySuggestion == null
                ? reason + " resetImmediately=" + resetImmediately
                : reason + " resetImmediately=" + resetImmediately + " penalty=" + penaltySuggestion;
    }
}

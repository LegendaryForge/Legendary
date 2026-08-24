package io.github.legendaryforge.legendary.core.api.legendary.access;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of evaluating access to a Legendary Encounter.
 *
 * <p>This is a pure decision object: no platform behavior, only intent.
 */
public final class LegendaryAccessDecision {

    private final LegendaryAccessLevel level;
    private final String reason;

    private LegendaryAccessDecision(LegendaryAccessLevel level, String reason) {
        this.level = Objects.requireNonNull(level, "level");
        this.reason = reason;
    }

    public static LegendaryAccessDecision allowParticipation() {
        return new LegendaryAccessDecision(LegendaryAccessLevel.PARTICIPATE, null);
    }

    public static LegendaryAccessDecision allowSpectate(String reason) {
        return new LegendaryAccessDecision(LegendaryAccessLevel.SPECTATE, reason);
    }

    public static LegendaryAccessDecision deny(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new LegendaryAccessDecision(LegendaryAccessLevel.DENY, reason);
    }

    public LegendaryAccessLevel level() {
        return level;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public String toString() {
        return reason == null ? level.name() : level.name() + " (" + reason + ")";
    }
}

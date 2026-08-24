package io.github.legendaryforge.legendary.core.internal.legendary.start;

import java.util.Objects;
import java.util.Optional;

/** Internal decision for whether a Legendary Encounter may be started. */
public final class LegendaryStartDecision {

    private final boolean allowed;
    private final String reason;

    private LegendaryStartDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static LegendaryStartDecision allow() {
        return new LegendaryStartDecision(true, null);
    }

    public static LegendaryStartDecision deny(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new LegendaryStartDecision(false, reason);
    }

    public boolean allowed() {
        return allowed;
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public String toString() {
        return reason == null ? (allowed ? "ALLOW" : "DENY") : (allowed ? "ALLOW" : "DENY") + " (" + reason + ")";
    }
}

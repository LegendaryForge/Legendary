package io.github.legendaryforge.legendary.core.internal.legendary.start;

import java.util.Objects;

/** Internal unchecked exception thrown when a Legendary encounter start is denied by policy. */
public final class LegendaryStartDeniedException extends RuntimeException {

    private final String reason;

    public LegendaryStartDeniedException(String reason) {
        super("legendary_start_denied: " + Objects.requireNonNull(reason, "reason"));
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}

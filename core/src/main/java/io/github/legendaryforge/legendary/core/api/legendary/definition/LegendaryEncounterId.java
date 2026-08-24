package io.github.legendaryforge.legendary.core.api.legendary.definition;

import java.util.Objects;

/**
 * Stable identifier for a Legendary Encounter definition (public API).
 *
 * <p>Constraints:
 * <ul>
 *   <li>Lowercase ASCII recommended.</li>
 *   <li>Allowed characters: a-z, 0-9, underscore, dash, dot.</li>
 * </ul>
 */
public final class LegendaryEncounterId {

    private static final String PATTERN = "^[a-z0-9_.-]+$";

    private final String value;

    private LegendaryEncounterId(String value) {
        this.value = value;
    }

    public static LegendaryEncounterId of(String value) {
        Objects.requireNonNull(value, "value");
        String v = value.trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (!v.matches(PATTERN)) {
            throw new IllegalArgumentException("value must match " + PATTERN + " but was: " + v);
        }
        return new LegendaryEncounterId(v);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LegendaryEncounterId that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

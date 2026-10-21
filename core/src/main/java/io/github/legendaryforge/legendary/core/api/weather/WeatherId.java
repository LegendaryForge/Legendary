package io.github.legendaryforge.legendary.core.api.weather;

import java.util.Objects;

/**
 * A weather identity, as the engine's stable string id (e.g. {@code Zone1_Rain}).
 *
 * <p>Deliberately NOT an index. A weather index is an asset-map ordinal; holding one in {@code core}
 * would mean carrying an ordinal across game updates with no way to notice it shifted. The ordinal
 * stays inside the adapter.
 */
public record WeatherId(String value) {

    public WeatherId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("WeatherId value must not be blank");
        }
    }
}

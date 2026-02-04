package io.github.legendaryforge.legendary.mod.runtime;

import java.util.Objects;

/**
 * Opaque player reference used by Legendary runtime adapters.
 *
 * <p>Engine integrations should map their player identity to this value.
 */
public record PlayerRef(String id) {

    public PlayerRef {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}

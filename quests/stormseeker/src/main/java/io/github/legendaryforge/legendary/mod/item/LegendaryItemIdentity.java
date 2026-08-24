package io.github.legendaryforge.legendary.mod.item;

import java.util.Objects;
import java.util.UUID;

/**
 * Minimal identity for owner-bound quest/legendary items.
 *
 * <p>Ownership and role are enforced at use-time (equip/consume/ritual), not existence-time.
 * This preserves sandbox behavior while preventing illegitimate authority transfer.
 */
public record LegendaryItemIdentity(UUID ownerId, String questlineId, LegendaryItemRole role) {

    public LegendaryItemIdentity {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(questlineId, "questlineId");
        Objects.requireNonNull(role, "role");
        if (questlineId.isBlank()) {
            throw new IllegalArgumentException("questlineId must not be blank");
        }
    }
}

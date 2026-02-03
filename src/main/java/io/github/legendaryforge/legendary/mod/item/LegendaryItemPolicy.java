package io.github.legendaryforge.legendary.mod.item;

import java.util.Objects;
import java.util.UUID;

/** Use-time enforcement helpers for owner-bound items. */
public final class LegendaryItemPolicy {

    private LegendaryItemPolicy() {}

    public static boolean isOwner(UUID actorId, LegendaryItemIdentity item) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(item, "item");
        return actorId.equals(item.ownerId());
    }

    /**
     * Enforces that only the owner can perform authoritative actions with this item
     * (equip, ritual consumption, quest progress interactions).
     *
     * <p>Callers can convert the false result into a deny reason appropriate for their context.
     */
    public static boolean canAuthoritativelyUse(UUID actorId, LegendaryItemIdentity item) {
        return isOwner(actorId, item);
    }
}

package io.github.legendaryforge.legendary.core.api.legendary.reward;

import java.util.Objects;

/**
 * A single reward intent.
 *
 * <p>The {@code key} meaning depends on {@link LegendaryRewardType}:
 * <ul>
 *   <li>ITEM: a stable item identifier (often a ResourceId string)</li>
 *   <li>TOKEN: token/currency key</li>
 *   <li>QUEST_PROGRESS: quest/unlock key</li>
 *   <li>CUSTOM: adapter/mod-defined key</li>
 * </ul>
 */
public final class LegendaryReward {

    private final LegendaryRewardType type;
    private final String key;
    private final int quantity;

    private LegendaryReward(LegendaryRewardType type, String key, int quantity) {
        this.type = Objects.requireNonNull(type, "type");
        this.key = Objects.requireNonNull(key, "key").trim();
        if (this.key.isEmpty()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }

    public static LegendaryReward of(LegendaryRewardType type, String key, int quantity) {
        return new LegendaryReward(type, key, quantity);
    }

    public LegendaryRewardType type() {
        return type;
    }

    public String key() {
        return key;
    }

    public int quantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return type + ":" + key + " x" + quantity;
    }
}

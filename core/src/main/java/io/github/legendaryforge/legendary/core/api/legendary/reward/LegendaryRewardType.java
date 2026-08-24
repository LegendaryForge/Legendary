package io.github.legendaryforge.legendary.core.api.legendary.reward;

/**
 * High-level reward intent types. Platform adapters decide how to realize these.
 */
public enum LegendaryRewardType {
    /** Grant an item-like reward identified by a ResourceId (or equivalent). */
    ITEM,

    /** Grant a token/currency-like reward identified by a key. */
    TOKEN,

    /** Advance a quest/unlock identified by a key. */
    QUEST_PROGRESS,

    /** Custom reward handled by downstream mods/adapters. */
    CUSTOM
}

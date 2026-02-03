package io.github.legendaryforge.legendary.mod.item;

/**
 * High-level roles for owner-bound legendary/quest-critical items.
 *
 * <p>This is intentionally engine-agnostic. Concrete item definitions can refine this with
 * more specific identifiers later (e.g., resource ids per questline).
 */
public enum LegendaryItemRole {
    SIGIL,
    FRAME,
    LEGENDARY_WEAPON
}

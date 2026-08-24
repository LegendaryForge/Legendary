package io.github.legendaryforge.legendary.core.api.legendary.access;

/**
 * Access level granted by a Legendary access policy.
 */
public enum LegendaryAccessLevel {
    /** Full participation: can affect the encounter. */
    PARTICIPATE,

    /** View-only: cannot affect the encounter. */
    SPECTATE,

    /** No access. */
    DENY
}

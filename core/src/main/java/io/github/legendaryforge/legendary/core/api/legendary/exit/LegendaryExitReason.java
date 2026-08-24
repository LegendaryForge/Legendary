package io.github.legendaryforge.legendary.core.api.legendary.exit;

/**
 * Why a Legendary Encounter instance ended and reset.
 */
public enum LegendaryExitReason {

    /** Party wiped / failed the encounter. */
    WIPE,

    /** All participants left the encounter (treated as a reset condition). */
    ALL_PARTICIPANTS_LEFT,

    /** Ended by an admin/system/controller. */
    FORCED,

    /** Ended due to timeout. */
    TIMEOUT
}

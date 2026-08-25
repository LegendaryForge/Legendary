package io.github.legendaryforge.legendary.quests.stormseeker.quest;

/**
 * Canonical Stormseeker questline phases.
 *
 * <p>Rules:
 * <ul>
 *   <li>Exactly one active phase at a time.</li>
 *   <li>Phases only advance forward (no skipping, no rollback).</li>
 *   <li>{@link #UNTOUCHED} and {@link #COMPLETE} bracket the six numbered phases: the questline
 *       has not begun, and the questline is over. Neither is a phase the player works through.</li>
 * </ul>
 *
 * <p>Renumbered 2026-08-24. The previous scheme ran {@code PHASE_0 .. PHASE_5} with a fractional
 * {@code PHASE_1_5_ATTUNEMENT} left over from the v3.0 redesign, and had no way to say "has not
 * started" — a new player was already in {@code PHASE_0}. Persisted names from that scheme are not
 * readable by this enum, which is deliberate: no migration was kept.
 */
public enum StormseekerPhase {

    /** The questline has not begun. The player has not yet been marked. */
    UNTOUCHED,

    /** A storm leaves something behind, and the elemental begins to watch. */
    PHASE_1_THE_MARK,

    /** Follow the elemental's trail to the circle. */
    PHASE_2_THE_TREK,

    /** The rite at the circle. Storm-sense is granted. */
    PHASE_3_THE_WAKING,

    /** The Flowing and Anchored trials. Both sigils. */
    PHASE_4_THE_TRIALS,

    /** Gather, then craft the inert frame. */
    PHASE_5_THE_FRAME,

    /** Charge and ground the frame, at the circle, during a storm. */
    PHASE_6_THE_FORGING,

    /** The questline is finished. */
    COMPLETE;

    public boolean isFinal() {
        return this == COMPLETE;
    }

    /** True while the player is working through a numbered phase. */
    public boolean isActive() {
        return this != UNTOUCHED && this != COMPLETE;
    }

    public StormseekerPhase next() {
        return switch (this) {
            case UNTOUCHED -> PHASE_1_THE_MARK;
            case PHASE_1_THE_MARK -> PHASE_2_THE_TREK;
            case PHASE_2_THE_TREK -> PHASE_3_THE_WAKING;
            case PHASE_3_THE_WAKING -> PHASE_4_THE_TRIALS;
            case PHASE_4_THE_TRIALS -> PHASE_5_THE_FRAME;
            case PHASE_5_THE_FRAME -> PHASE_6_THE_FORGING;
            case PHASE_6_THE_FORGING -> COMPLETE;
            case COMPLETE -> COMPLETE;
        };
    }
}

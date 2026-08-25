package io.github.legendaryforge.legendary.quests.stormseeker.quest;

/**
 * Canonical Stormseeker quest step identifiers used in gate attributes.
 *
 * <p>These are intentionally coarse:
 * <ul>
 *   <li>They start at {@code PHASE_5_THE_FRAME} because earlier phases should not gate activations.</li>
 *   <li>They are stable identifiers (treat as public contract).</li>
 * </ul>
 *
 * <p>Renumbered 2026-08-24 alongside {@link StormseekerPhase}. The old ids
 * ({@code stormseeker.phase3.incomplete_form}, {@code .phase4.storms_answer},
 * {@code .phase5.epilogue}) are gone rather than aliased — a deliberate clean break, taken while
 * nothing persisted depends on them. Once content ships, add new ids instead of renaming these.
 */
public final class StormseekerQuestSteps {

    /** Player is gathering and crafting the frame. */
    public static final String PHASE_5_THE_FRAME = "stormseeker.phase5.frame";

    /** Player is charging and grounding the frame. */
    public static final String PHASE_6_THE_FORGING = "stormseeker.phase6.forging";

    /** Player has finished the questline. */
    public static final String COMPLETE = "stormseeker.complete";

    private StormseekerQuestSteps() {}
}

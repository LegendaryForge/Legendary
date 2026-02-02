package io.github.legendaryforge.legendary.mod.stormseeker.quest;

/**
 * Canonical Stormseeker quest step identifiers used in gate attributes.
 *
 * <p>These are intentionally coarse for the scaffold:
 * <ul>
 *   <li>They start at Phase 3 because earlier phases should not be gating activations.</li>
 *   <li>They are stable identifiers (treat as public contract).</li>
 * </ul>
 *
 * <p>Later, if we need finer-grained steps, we add new identifiers without renaming existing ones.
 */
public final class StormseekerQuestSteps {

    /** Player is in Phase 3 (incomplete form / assembly). */
    public static final String PHASE_3_INCOMPLETE_FORM = "stormseeker.phase3.incomplete_form";

    /** Player is in Phase 4 (storm's answer / correction). */
    public static final String PHASE_4_STORMS_ANSWER = "stormseeker.phase4.storms_answer";

    /** Player is in Phase 5 (final tempering). */
    public static final String PHASE_5_FINAL_TEMPERING = "stormseeker.phase5.final_tempering";

    private StormseekerQuestSteps() {}
}

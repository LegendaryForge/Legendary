package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import java.util.Optional;

/**
 * Maps Stormseeker phase state to canonical quest step identifiers used for gate attributes.
 *
 * <p>Scaffold rule:
 * <ul>
 *   <li>Only Phase 3+ participates in activation gating (earlier phases are not gate-relevant).</li>
 *   <li>This mapping is intentionally coarse and stable; do not rename existing step ids.</li>
 * </ul>
 */
public final class StormseekerQuestStepMapper {

    private StormseekerQuestStepMapper() {}

    /**
     * Returns the canonical quest step for the given progress, if the current phase participates in gating.
     */
    public static Optional<String> stepFor(StormseekerProgress progress) {
        return stepFor(progress.phase());
    }

    /**
     * Returns the canonical quest step for the given phase, if the phase participates in gating.
     */
    public static Optional<String> stepFor(StormseekerPhase phase) {
        return switch (phase) {
            case UNTOUCHED, PHASE_1_THE_MARK, PHASE_2_THE_TREK, PHASE_3_THE_WAKING, PHASE_4_THE_TRIALS ->
                Optional.empty();
            case PHASE_5_THE_FRAME -> Optional.of(StormseekerQuestSteps.PHASE_5_THE_FRAME);
            case PHASE_6_THE_FORGING -> Optional.of(StormseekerQuestSteps.PHASE_6_THE_FORGING);
            case COMPLETE -> Optional.of(StormseekerQuestSteps.COMPLETE);
        };
    }
}

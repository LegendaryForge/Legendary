package io.github.legendaryforge.legendary.mod.stormseeker.quest;

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
            case PHASE_0_UNEASE, PHASE_1_ATTUNEMENT, PHASE_1_5_AFTERSHOCK, PHASE_2_DUAL_SIGILS -> Optional.empty();
            case PHASE_3_INCOMPLETE_FORM -> Optional.of(StormseekerQuestSteps.PHASE_3_INCOMPLETE_FORM);
            case PHASE_4_STORMS_ANSWER -> Optional.of(StormseekerQuestSteps.PHASE_4_STORMS_ANSWER);
            case PHASE_5_FINAL_TEMPERING -> Optional.of(StormseekerQuestSteps.PHASE_5_FINAL_TEMPERING);
        };
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;

/**
 * Phase 1 seam: grants the Coursing Lodestone (Flowing) via StormseekerProgress and emits durable milestones.
 */
public final class FlowingLodestoneIssuer {

    private FlowingLodestoneIssuer() {}

    /**
     * Idempotently grants the Coursing Lodestone and emits COURSING_LODESTONE_GRANTED. If the Standing Lodestone is already granted,
     * also emits DUAL_LODESTONES_GRANTED.
     *
     * @return true if this call newly granted the Coursing Lodestone; false if it was already granted.
     */
    public static boolean tryGrantCoursingLodestone(String playerId, StormseekerProgress progress) {
        if (progress.hasCoursingLodestone()) {
            return false;
        }

        progress.grantCoursingLodestone();
        if (progress.hasStandingLodestone()) {}

        return true;
    }

    /**
     * Back-compat shim for existing FlowingLodestoneGrantSystem wiring.
     *
     * NOTE: This path does not emit host milestones because it has no runtime/playerId context.
     */
    public static boolean grantIfMissing(StormseekerProgress progress) {
        if (progress.hasCoursingLodestone()) {
            return false;
        }
        progress.grantCoursingLodestone();
        return true;
    }
}

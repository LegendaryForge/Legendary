package io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;

/**
 * Phase 2 seam: grants the Standing Lodestone (Anchored) via StormseekerProgress and emits durable milestones.
 *
 * Eligibility rules and the Anchored trial system will wire into this later.
 */
public final class AnchoredLodestoneIssuer {

    public boolean tryGrantStandingLodestone(String playerId, StormseekerProgress progress) {
        boolean newlyGranted = progress.grantStandingLodestone();
        if (!newlyGranted) {
            return false;
        }
        if (progress.hasCoursingLodestone()) {}

        return true;
    }
}

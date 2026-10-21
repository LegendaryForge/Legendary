package io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;

/**
 * Phase 2: grants the Standing Lodestone (Anchored) via StormseekerProgress.
 *
 * <p>Important: this class MUST NOT emit milestones. Host ticks observe durable edges and emit milestones.
 */
public final class AnchoredLodestoneGrantSystem {

    /**
     * Idempotently grants the Standing Lodestone (Anchored) via StormseekerProgress.
     *
     * @return true if this call newly granted the Standing Lodestone; false if it was already granted.
     */
    public static boolean tryGrantStandingLodestone(String playerId, StormseekerProgress progress) {
        // playerId is currently unused but kept for symmetry/future logging hooks.
        return progress.grantStandingLodestone();
    }
}

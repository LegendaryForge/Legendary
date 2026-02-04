package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;

/**
 * Phase 2 seam: grants Sigil B (Anchored) via StormseekerProgress and emits durable milestones.
 *
 * Eligibility rules and the Anchored trial system will wire into this later.
 */
public final class AnchoredSigilIssuer {

    public boolean tryGrantSigilB(String playerId, StormseekerProgress progress) {
        boolean newlyGranted = progress.grantSigilB();
        if (!newlyGranted) {
            return false;
        }
        if (progress.hasSigilA()) {}

        return true;
    }
}

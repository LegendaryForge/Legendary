package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;

/**
 * Phase 2 seam: grants Sigil B (Anchored) via StormseekerProgress and emits durable milestones.
 *
 * Eligibility rules and the Anchored trial system will wire into this later.
 */
public final class AnchoredSigilIssuer {

    public boolean tryGrantSigilB(StormseekerHostRuntime runtime, String playerId, StormseekerProgress progress) {
        boolean newlyGranted = progress.grantSigilB();
        if (!newlyGranted) {
            return false;
        }

        runtime.emitStormseekerMilestone(
                new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.SIGIL_B_GRANTED));

        if (progress.hasSigilA()) {
            runtime.emitStormseekerMilestone(
                    new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED));
        }

        return true;
    }
}

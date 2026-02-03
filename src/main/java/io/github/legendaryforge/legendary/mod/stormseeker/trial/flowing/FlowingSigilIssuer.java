package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;

/**
 * Phase 1 seam: grants Sigil A (Flowing) via StormseekerProgress and emits durable milestones.
 */
public final class FlowingSigilIssuer {

    private FlowingSigilIssuer() {}

    /**
     * Idempotently grants Sigil A and emits SIGIL_A_GRANTED. If Sigil B is already granted,
     * also emits DUAL_SIGILS_GRANTED.
     *
     * @return true if this call newly granted Sigil A; false if it was already granted.
     */
    public static boolean tryGrantSigilA(
            StormseekerHostRuntime runtime, String playerId, StormseekerProgress progress) {
        if (progress.hasSigilA()) {
            return false;
        }

        progress.grantSigilA();

        runtime.emitStormseekerMilestone(
                new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.SIGIL_A_GRANTED));

        if (progress.hasSigilB()) {
            runtime.emitStormseekerMilestone(
                    new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED));
        }

        return true;
    }

    /**
     * Back-compat shim for existing FlowingSigilGrantSystem wiring.
     *
     * NOTE: This path does not emit host milestones because it has no runtime/playerId context.
     */
    public static boolean grantIfMissing(StormseekerProgress progress) {
        if (progress.hasSigilA()) {
            return false;
        }
        progress.grantSigilA();
        return true;
    }
}

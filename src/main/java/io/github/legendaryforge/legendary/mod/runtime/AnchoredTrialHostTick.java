package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.HashSet;
import java.util.Set;

/**
 * Phase 2 host-side tick surface for the Anchored Trial.
 *
 * This does not implement the Anchored Trial itself. It only emits durable host milestones
 * when progress transitions occur (edge-based), mirroring Phase 1 semantics.
 */
public final class AnchoredTrialHostTick {

    private final Set<String> playersWithSigilB = new HashSet<>();

    public void tick(StormseekerHostRuntime runtime) {
        for (String playerId : runtime.playerIds()) {
            StormseekerProgress progress = runtime.progress(playerId);

            if (progress.hasSigilB() && playersWithSigilB.add(playerId)) {
                runtime.emitStormseekerMilestone(
                        new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.SIGIL_B_GRANTED));

                if (progress.hasSigilA()) {
                    runtime.emitStormseekerMilestone(
                            new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.DUAL_SIGILS_GRANTED));
                }
            }
        }
    }
}

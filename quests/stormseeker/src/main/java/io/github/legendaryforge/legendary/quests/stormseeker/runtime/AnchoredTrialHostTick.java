package io.github.legendaryforge.legendary.quests.stormseeker.runtime;

import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerPhaseMilestone;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored.AnchoredTrialSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 2 host tick seam for Anchored Trial.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Drive per-player Anchored Trial session stepping (gameplay-side evaluation).</li>
 *   <li>Emit durable host milestones when progress transitions occur (edge-based).</li>
 * </ul>
 *
 * <p>Locked rules:
 * <ul>
 *   <li>Milestones are edge-based durable signals.</li>
 *   <li>Host tick does not "decide" entitlement; it only observes progress edges.</li>
 * </ul>
 */
public final class AnchoredTrialHostTick {

    private final Map<String, AnchoredTrialSession> sessions = new HashMap<>();
    private final Set<String> playersWithStandingLodestone = new HashSet<>();

    private final Set<String> playersWithDualLodestones = new HashSet<>();

    public void tick(StormseekerHostRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");

        for (String playerId : runtime.playerIds()) {
            Objects.requireNonNull(playerId, "playerId");

            StormseekerProgress progress = runtime.progress(playerId);

            // Gameplay stepping (authoritative mutation happens inside session.step via StormseekerProgress).
            AnchoredTrialSession session = sessions.computeIfAbsent(playerId, id -> new AnchoredTrialSession(progress));
            var step = session.step(runtime.motionSample(playerId));
            runtime.onAnchoredTrialStep(playerId, step);

            // Host-facing milestone emission is edge-based.
            if (progress.hasStandingLodestone() && playersWithStandingLodestone.add(playerId)) {
                runtime.emitStormseekerMilestone(new StormseekerMilestoneOutcome(
                        playerId, StormseekerPhaseMilestone.STANDING_LODESTONE_GRANTED));
            }

            if (progress.hasCoursingLodestone()
                    && progress.hasStandingLodestone()
                    && playersWithDualLodestones.add(playerId)) {
                runtime.emitStormseekerMilestone(
                        new StormseekerMilestoneOutcome(playerId, StormseekerPhaseMilestone.DUAL_LODESTONES_GRANTED));
            }
        }
    }

    public boolean removePlayer(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        playersWithStandingLodestone.remove(playerId);
        playersWithDualLodestones.remove(playerId);
        return sessions.remove(playerId) != null;
    }

    public void clear() {
        sessions.clear();
        playersWithStandingLodestone.clear();
        playersWithDualLodestones.clear();
    }
}

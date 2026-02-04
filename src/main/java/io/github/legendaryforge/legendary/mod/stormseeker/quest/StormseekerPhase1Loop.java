package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.questline.objective.ObjectiveStatus;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 1 coordinator: enforces Attunement eligibility and drives the Flowing Trial loop once per host tick.
 *
 * <p>This class is deliberately engine-agnostic and contains no scheduling assumptions.
 *
 * <p>Contract: {@link #tick(StormseekerHostRuntime)} returns a per-player read model suitable for hosts/UI:
 * denial reason (or null), objective snapshot, and participation state for this tick.
 */
public final class StormseekerPhase1Loop {

    private final StormseekerAttunementService attunement;
    private final StormseekerObjectiveSnapshotService snapshotService;

    public StormseekerPhase1Loop() {
        this(new StormseekerAttunementService(), new StormseekerObjectiveSnapshotService());
    }

    public StormseekerPhase1Loop(
            StormseekerAttunementService attunement, StormseekerObjectiveSnapshotService snapshotService) {
        this.attunement = Objects.requireNonNull(attunement, "attunement");
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
    }

    /**
     * Called once per host tick.
     *
     * <p>Reconciles participation against current eligibility, then advances the trial loop for participants.
     *
     * @return host-facing per-player views for this tick (one entry per {@link StormseekerHostRuntime#playerIds()}).
     */
    public List<StormseekerPhase1TickView> tick(StormseekerHostRuntime host) {
        Objects.requireNonNull(host, "host");

        // Snapshot current host-visible players.
        Set<String> present = new HashSet<>();
        var playerIds = new java.util.ArrayList<String>();
        for (String playerId : host.playerIds()) {
            playerIds.add(playerId);
        }
        playerIds.sort(java.util.Comparator.comparing(String::valueOf));

        for (String playerId : playerIds) {
            Objects.requireNonNull(playerId, "playerId");
            present.add(playerId);
        }

        List<StormseekerPhase1TickView> views = new ArrayList<>(present.size());

        // Enforce eligibility for all present players and build the host-facing read model.
        for (String playerId : present) {
            StormseekerProgress progress = host.progress(playerId);
            ResourceId deny = attunement.denyEnterFlowingTrialReason(progress);
            boolean participatingThisTick = (deny == null);

            if (participatingThisTick) {
                attunement.enterFlowingTrial(playerId, progress);
            } else {
                attunement.leaveFlowingTrial(playerId);
            }

            List<ObjectiveStatus> objectives = snapshotService.snapshot(progress);
            var view = new StormseekerPhase1TickView(playerId, deny, objectives, participatingThisTick);
            host.emitPhase1TickView(view);
            views.add(view);
        }

        // Drive the Flowing Trial loop for participating players only.
        attunement.tick(host);

        return views;
    }
}

package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.mod.questline.objective.ObjectiveStatus;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 1 coordinator: builds per-player objective snapshots once per host tick.
 *
 * <p>This class is deliberately engine-agnostic and contains no scheduling assumptions.
 *
 * <p>Contract: {@link #tick(StormseekerHostRuntime)} returns a per-player read model suitable for hosts/UI:
 * denial reason (or null), objective snapshot, and participation state for this tick.
 *
 * <p>Note: Attunement eligibility and Flowing Trial integration are not yet wired.
 * This loop currently provides objective snapshots only.
 */
public final class StormseekerPhase1Loop {

    private final StormseekerObjectiveSnapshotService snapshotService;

    public StormseekerPhase1Loop() {
        this(new StormseekerObjectiveSnapshotService());
    }

    public StormseekerPhase1Loop(StormseekerObjectiveSnapshotService snapshotService) {
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
    }

    /**
     * Called once per host tick.
     *
     * <p>Builds objective snapshots for all present players.
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

        for (String playerId : present) {
            StormseekerProgress progress = host.progress(playerId);

            List<ObjectiveStatus> objectives = snapshotService.snapshot(progress);
            var view = new StormseekerPhase1TickView(playerId, null, objectives, false);
            host.emitPhase1TickView(view);
            views.add(view);
        }

        return views;
    }
}

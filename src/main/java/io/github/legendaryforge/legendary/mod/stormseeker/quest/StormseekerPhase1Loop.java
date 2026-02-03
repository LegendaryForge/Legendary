package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Phase 1 coordinator: enforces Attunement eligibility and drives the Flowing Trial loop once per host tick.
 *
 * <p>This class is deliberately engine-agnostic and contains no scheduling assumptions.
 */
public final class StormseekerPhase1Loop {

    private final StormseekerAttunementService attunement;

    public StormseekerPhase1Loop() {
        this(new StormseekerAttunementService());
    }

    public StormseekerPhase1Loop(StormseekerAttunementService attunement) {
        this.attunement = Objects.requireNonNull(attunement, "attunement");
    }

    /**
     * Called once per host tick.
     *
     * <p>Reconciles participation against current eligibility, then advances the trial loop for participants.
     */
    public void tick(StormseekerHostRuntime host) {
        Objects.requireNonNull(host, "host");

        // Snapshot current host-visible players.
        Set<String> present = new HashSet<>();
        for (String playerId : host.playerIds()) {
            Objects.requireNonNull(playerId, "playerId");
            present.add(playerId);
        }

        // Remove participation for players no longer present.
        // (We cannot iterate participation directly here without exposing internals, so we rely on host visibility:
        // hosts should include all connected/loaded players in playerIds().)
        //
        // NOTE: Participation retention is already handled by FlowingTrialHostDriver -> hostTick.retainOnly(),
        // but this ensures the participation set itself doesn't retain stale IDs.
        //
        // Because FlowingTrialParticipation is internal to the service, we conservatively call leave for all
        // non-present IDs only if the host provides a stable universe of connected players.
        //
        // If a host provides only "nearby" players, it should call leave explicitly on disconnect/leave events.

        // Enforce eligibility for all present players.
        for (String playerId : present) {
            StormseekerProgress progress = host.progress(playerId);
            if (attunement.canEnterFlowingTrial(progress)) {
                attunement.enterFlowingTrial(playerId, progress);
            } else {
                attunement.leaveFlowingTrial(playerId);
            }
        }

        // Drive the Flowing Trial loop for participating players only.
        attunement.tick(host);
    }
}

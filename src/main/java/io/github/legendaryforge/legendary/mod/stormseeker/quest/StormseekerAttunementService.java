package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostDriver;
import io.github.legendaryforge.legendary.mod.runtime.FlowingTrialHostTick;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialParticipation;
import java.util.Objects;

/**
 * Phase 1 (Attunement) control surface for the Flowing Trial.
 *
 * <p>Engine integration will call enter/leave based on triggers (zone, interaction, UI).
 * This service remains engine-agnostic and only manipulates authoritative participation state.
 */
public final class StormseekerAttunementService {

    public static final ResourceId DENY_ENTER_NOT_IN_PHASE_1 =
            ResourceId.of("stormseeker", "deny_enter_not_in_phase_1");

    public static final ResourceId DENY_ENTER_ALREADY_HAS_SIGIL_A =
            ResourceId.of("stormseeker", "deny_enter_already_has_sigil_a");

    private final FlowingTrialParticipation participation = new FlowingTrialParticipation();
    private final FlowingTrialHostDriver driver;

    public StormseekerAttunementService() {
        this(new FlowingTrialHostDriver(new FlowingTrialHostTick()));
    }

    public StormseekerAttunementService(FlowingTrialHostDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    public boolean canEnterFlowingTrial(StormseekerProgress progress) {
        return denyEnterFlowingTrialReason(progress) == null;
    }

    /**
     * Returns a stable denial reason when a player cannot enter the Flowing Trial loop.
     *
     * @return denial reason id, or null if entry is allowed
     */
    public ResourceId denyEnterFlowingTrialReason(StormseekerProgress progress) {
        Objects.requireNonNull(progress, "progress");

        // Deny if already earned Sigil A (idempotent: no re-entry needed).
        if (progress.hasSigilA()) {
            return DENY_ENTER_ALREADY_HAS_SIGIL_A;
        }

        // Deny if not yet in Phase 1 Attunement (scaffold semantics).
        if (progress.phase() == StormseekerPhase.PHASE_0_UNEASE) {
            return DENY_ENTER_NOT_IN_PHASE_1;
        }

        return null;
    }

    /**
     * Attempts to enter the Flowing Trial loop.
     *
     * <p>Returns false if entry is not applicable (e.g., already has Sigil A).
     */
    public boolean enterFlowingTrial(String playerId, StormseekerProgress progress) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(progress, "progress");

        if (!canEnterFlowingTrial(progress)) {
            return false;
        }

        return participation.enter(playerId);
    }

    public boolean leaveFlowingTrial(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return participation.leave(playerId);
    }

    /** Advances the Flowing Trial loop for all currently participating players. */
    public void tick(StormseekerHostRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        driver.tick(runtime, participation);
    }

    /** Visible for testing. */
    boolean isParticipatingForTesting(String playerId) {
        return participation.contains(playerId);
    }
}

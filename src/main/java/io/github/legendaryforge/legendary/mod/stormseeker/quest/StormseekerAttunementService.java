package io.github.legendaryforge.legendary.mod.stormseeker.quest;

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

    private final FlowingTrialParticipation participation = new FlowingTrialParticipation();
    private final FlowingTrialHostDriver driver;

    public StormseekerAttunementService() {
        this(new FlowingTrialHostDriver(new FlowingTrialHostTick()));
    }

    public StormseekerAttunementService(FlowingTrialHostDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    /**
     * Attempts to enter the Flowing Trial loop.
     *
     * <p>Returns false if entry is not applicable (e.g., already has Sigil A).
     */
    public boolean enterFlowingTrial(String playerId, StormseekerProgress progress) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(progress, "progress");

        if (progress.hasSigilA()) {
            return false;
        }

        // Scaffold rule: only allow entry once Stormseeker has begun (Phase 1+).
        switch (progress.phase()) {
            case PHASE_0_UNEASE -> {
                return false;
            }
            default -> {
                // Phase 1+ allowed
            }
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

package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostDriver;
import io.github.legendaryforge.legendary.mod.runtime.AnchoredTrialHostTick;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialParticipation;
import java.util.Objects;

/**
 * Phase 2 control surface for entering/leaving and ticking the Anchored Trial.
 *
 * <p>Engine integration will call enter/leave based on triggers (zone, interaction, UI). This
 * service remains engine-agnostic and only manipulates authoritative participation state and host
 * tick seams.
 */
public final class StormseekerAnchoredTrialService {

    public static final ResourceId DENY_ENTER_ALREADY_HAS_SIGIL_B =
            ResourceId.of("stormseeker", "deny_enter_already_has_sigil_b");

    private final AnchoredTrialParticipation participation = new AnchoredTrialParticipation();
    private final AnchoredTrialHostDriver driver;

    public StormseekerAnchoredTrialService() {
        this(new AnchoredTrialHostDriver(new AnchoredTrialHostTick()));
    }

    public StormseekerAnchoredTrialService(AnchoredTrialHostDriver driver) {
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    public boolean canEnterAnchoredTrial(StormseekerProgress progress) {
        return denyEnterAnchoredTrialReason(progress) == null;
    }

    /** Returns denial reason id, or null if entry is allowed. */
    public ResourceId denyEnterAnchoredTrialReason(StormseekerProgress progress) {
        Objects.requireNonNull(progress, "progress");
        if (progress.hasSigilB()) {
            return DENY_ENTER_ALREADY_HAS_SIGIL_B;
        }
        return null;
    }

    public boolean enterAnchoredTrial(String playerId, StormseekerProgress progress) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(progress, "progress");

        if (!canEnterAnchoredTrial(progress)) {
            return false;
        }
        return participation.enter(playerId);
    }

    public boolean leaveAnchoredTrial(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return participation.leave(playerId);
    }

    /**
     * Explicit leave+cleanup helper (policy seam).
     *
     * <p>Leaving participation does not reset host tick session state; cleanup is explicit. If an
     * integration wants leaving to also cleanup Anchored Trial host tick state, call this method.
     */
    public void leaveAndCleanup(StormseekerHostRuntime runtime, String playerId) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(playerId, "playerId");
        driver.leaveAndCleanup(runtime, participation, playerId);
    }

    /** Advances the Anchored Trial loop for all currently participating players. */
    public void tick(StormseekerHostRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        driver.tick(runtime, participation);
    }

    /** Visible for testing. */
    boolean isParticipatingForTesting(String playerId) {
        return participation.contains(playerId);
    }
}

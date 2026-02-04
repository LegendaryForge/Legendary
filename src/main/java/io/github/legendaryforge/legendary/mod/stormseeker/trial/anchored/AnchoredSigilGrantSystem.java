package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;

/**
 * Phase 2: grants Sigil B (Anchored) via StormseekerProgress.
 *
 * <p>Important: this class MUST NOT emit milestones. Host ticks observe durable edges and emit milestones.
 */
public final class AnchoredSigilGrantSystem {

    /**
     * Idempotently grants Sigil B (Anchored) via StormseekerProgress.
     *
     * @return true if this call newly granted Sigil B; false if it was already granted.
     */
    public static boolean tryGrantSigilB(String playerId, StormseekerProgress progress) {
        // playerId is currently unused but kept for symmetry/future logging hooks.
        return progress.grantSigilB();
    }
}

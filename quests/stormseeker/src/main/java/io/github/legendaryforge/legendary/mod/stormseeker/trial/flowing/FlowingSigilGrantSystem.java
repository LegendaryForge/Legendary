package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.Objects;

/**
 * Phase C: translates Flowing Trial completion into authoritative sigil proof.
 *
 * <p>Runtime integration calls {@link #onFlowingTrialCompleted(StormseekerProgress)} when the
 * evaluator reports completion for a player. This system is intentionally engine-agnostic.
 */
public final class FlowingSigilGrantSystem {

    /**
     * Idempotently grants Sigil A (Flowing) via StormseekerProgress.
     *
     * @return true if newly granted on this call; false if already granted.
     */
    public boolean onFlowingTrialCompleted(StormseekerProgress progress) {
        Objects.requireNonNull(progress, "progress");
        return FlowingSigilIssuer.grantIfMissing(progress);
    }
}

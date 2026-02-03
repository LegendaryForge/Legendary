package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import java.util.Objects;

/**
 * Phase C: grants the Flowing sigil proof (Sigil A) via StormseekerProgress.
 *
 * <p>This is intentionally content-agnostic and item-agnostic. Item safety / persistence is handled
 * by the runtime environment; this class only flips the authoritative proof bit and triggers
 * phase advancement if eligible.
 */
public final class FlowingSigilIssuer {

    private FlowingSigilIssuer() {}

    /**
     * Idempotently grants Sigil A and advances progress if eligible.
     *
     * @return true if this call newly granted Sigil A; false if it was already granted.
     */
    public static boolean grantIfMissing(StormseekerProgress progress) {
        Objects.requireNonNull(progress, "progress");
        if (progress.hasSigilA()) {
            return false;
        }
        progress.grantSigilA();
        progress.advanceIfEligible();
        return true;
    }
}

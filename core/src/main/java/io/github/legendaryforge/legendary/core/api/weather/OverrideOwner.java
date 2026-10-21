package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

/**
 * Identity of whatever holds a per-player weather override.
 *
 * <p>Keyed by <b>value</b>, and that is the whole point. The engine compares override owners by
 * reference identity ({@code if_acmpeq}) on a claimant {@code Ref} that is arbitrary — distinct from
 * the subject — so the natural implementation passes the player's own {@code Ref} as claimant
 * everywhere. Measured 2026-10-20: a same-claimant re-claim is then a <b>silent update, not a
 * refusal</b>, so two of our own subsystems gating one player would overwrite each other with no
 * error. Our owner identity has to be finer than the engine's, which is what this type provides.
 */
public record OverrideOwner(ResourceId id) {

    public OverrideOwner {
        Objects.requireNonNull(id, "id");
    }
}

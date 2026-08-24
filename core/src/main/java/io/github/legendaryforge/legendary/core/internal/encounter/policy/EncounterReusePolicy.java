package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;

/**
 * Internal policy interface for determining whether an existing encounter should be reused.
 *
 * <p>This interface defines policy only. It performs no gameplay or platform logic.</p>
 *
 * <p>All implementations must be deterministic.</p>
 */
public interface EncounterReusePolicy {

    /**
     * Returns {@code true} if {@code existing} should be reused for {@code key} rather than creating
     * a new encounter instance.
     */
    boolean shouldReuse(EncounterKey key, EncounterInstance existing);
}

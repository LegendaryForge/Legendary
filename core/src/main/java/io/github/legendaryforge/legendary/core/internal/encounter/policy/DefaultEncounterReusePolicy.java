package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;

/**
 * Default encounter reuse policy.
 *
 * <p>This reference implementation never reuses encounters. Each create request results
 * in a new encounter instance, preserving legacy behavior.</p>
 */
public final class DefaultEncounterReusePolicy implements EncounterReusePolicy {

    @Override
    public boolean shouldReuse(EncounterKey key, EncounterInstance existing) {
        return false;
    }
}

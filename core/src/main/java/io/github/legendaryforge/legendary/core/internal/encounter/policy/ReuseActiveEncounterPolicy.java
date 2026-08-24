package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterState;

/**
 * Encounter reuse policy that reuses an existing encounter if it is still active.
 */
public final class ReuseActiveEncounterPolicy implements EncounterReusePolicy {

    @Override
    public boolean shouldReuse(EncounterKey key, EncounterInstance existing) {
        return existing.state() != EncounterState.ENDED;
    }
}

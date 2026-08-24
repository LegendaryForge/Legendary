package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import io.github.legendaryforge.legendary.core.api.legendary.exit.LegendaryPenaltySuggestedEvent;

/**
 * Platform seam for applying advisory Legendary penalties.
 *
 * <p>Adapters decide how to implement penalties (debuffs, lockouts, flags, etc.).
 */
@FunctionalInterface
public interface LegendaryPenaltyApplier {

    void apply(LegendaryPenaltySuggestedEvent event);

    static LegendaryPenaltyApplier noop() {
        return event -> {};
    }
}

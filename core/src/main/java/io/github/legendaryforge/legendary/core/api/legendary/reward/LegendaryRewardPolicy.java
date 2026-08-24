package io.github.legendaryforge.legendary.core.api.legendary.reward;

import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;

/**
 * Provides reward plans for Legendary Encounter definitions.
 */
public interface LegendaryRewardPolicy {

    LegendaryRewardPlan planFor(LegendaryEncounterDefinition definition);
}

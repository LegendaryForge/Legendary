package io.github.legendaryforge.legendary.core.api.legendary.access;

/**
 * Evaluates whether a player may participate in or spectate a Legendary Encounter instance.
 */
public interface LegendaryAccessPolicy {

    LegendaryAccessDecision evaluate(LegendaryAccessRequest request);
}

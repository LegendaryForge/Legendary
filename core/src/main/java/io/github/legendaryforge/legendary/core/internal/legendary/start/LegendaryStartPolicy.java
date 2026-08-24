package io.github.legendaryforge.legendary.core.internal.legendary.start;

/** Internal policy for gating the start of a Legendary Encounter attempt. */
public interface LegendaryStartPolicy {

    LegendaryStartDecision evaluate(LegendaryStartRequest request);
}

package io.github.legendaryforge.legendary.content.encounter.toystorm;

import io.github.legendaryforge.legendary.content.reward.RewardDecision;

/** Deterministic reward decision for ToyStorm derived from {@link ToyStormEndSummary}. */
public record ToyStormRewardDecision(boolean eligible) implements RewardDecision {}

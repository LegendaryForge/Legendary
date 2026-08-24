package io.github.legendaryforge.legendary.harness.content.encounter.toystorm;

import io.github.legendaryforge.legendary.harness.content.reward.RewardDecision;

/** Deterministic reward decision for ToyStorm derived from {@link ToyStormEndSummary}. */
public record ToyStormRewardDecision(boolean eligible) implements RewardDecision {}

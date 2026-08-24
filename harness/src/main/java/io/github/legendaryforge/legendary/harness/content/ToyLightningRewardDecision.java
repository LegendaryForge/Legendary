package io.github.legendaryforge.legendary.harness.content;

import io.github.legendaryforge.legendary.harness.content.reward.RewardDecision;
import java.util.UUID;

public record ToyLightningRewardDecision(UUID instanceId, ToyLightningScript.RewardTier rewardTier, int rewardPoints)
        implements RewardDecision {}

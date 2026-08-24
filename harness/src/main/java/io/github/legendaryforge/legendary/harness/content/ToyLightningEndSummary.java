package io.github.legendaryforge.legendary.harness.content;

import java.util.UUID;

public record ToyLightningEndSummary(
        UUID instanceId,
        int participantsAtEnd,
        int chargeAtEnd,
        int starts,
        ToyLightningScript.RewardTier rewardTier) {}

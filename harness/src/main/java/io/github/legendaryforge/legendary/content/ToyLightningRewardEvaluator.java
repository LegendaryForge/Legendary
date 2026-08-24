package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.content.reward.RewardEvaluator;
import java.util.Objects;

public final class ToyLightningRewardEvaluator
        implements RewardEvaluator<ToyLightningEndSummary, ToyLightningRewardDecision> {

    public ToyLightningRewardEvaluator() {}

    @Override
    public ToyLightningRewardDecision evaluate(ToyLightningEndSummary summary) {
        return decide(summary);
    }

    public static ToyLightningRewardDecision decide(ToyLightningEndSummary summary) {
        Objects.requireNonNull(summary, "summary");

        int points =
                switch (summary.rewardTier()) {
                    case NONE -> 0;
                    case MINOR -> 10;
                    case MAJOR -> 25;
                };

        return new ToyLightningRewardDecision(summary.instanceId(), summary.rewardTier(), points);
    }
}

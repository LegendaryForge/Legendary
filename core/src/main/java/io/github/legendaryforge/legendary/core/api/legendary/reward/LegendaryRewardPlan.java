package io.github.legendaryforge.legendary.core.api.legendary.reward;

import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterId;
import java.util.List;
import java.util.Objects;

/**
 * Describes what rewards should be granted for a Legendary Encounter when triggered.
 */
public final class LegendaryRewardPlan {

    private final LegendaryEncounterId encounterId;
    private final LegendaryRewardTrigger trigger;
    private final LegendaryRewardEligibilityRule eligibilityRule;
    private final List<LegendaryReward> rewards;

    private LegendaryRewardPlan(
            LegendaryEncounterId encounterId,
            LegendaryRewardTrigger trigger,
            LegendaryRewardEligibilityRule eligibilityRule,
            List<LegendaryReward> rewards) {
        this.encounterId = Objects.requireNonNull(encounterId, "encounterId");
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.eligibilityRule = Objects.requireNonNull(eligibilityRule, "eligibilityRule");
        this.rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        if (this.rewards.isEmpty()) {
            throw new IllegalArgumentException("rewards must not be empty");
        }
    }

    public static LegendaryRewardPlan onSuccess(
            LegendaryEncounterId encounterId,
            LegendaryRewardEligibilityRule eligibilityRule,
            List<LegendaryReward> rewards) {
        return new LegendaryRewardPlan(encounterId, LegendaryRewardTrigger.SUCCESS, eligibilityRule, rewards);
    }

    public LegendaryEncounterId encounterId() {
        return encounterId;
    }

    public LegendaryRewardTrigger trigger() {
        return trigger;
    }

    public LegendaryRewardEligibilityRule eligibilityRule() {
        return eligibilityRule;
    }

    public List<LegendaryReward> rewards() {
        return rewards;
    }
}

package io.github.legendaryforge.legendary.core.api.legendary.reward;

/**
 * Eligibility constraints for receiving rewards.
 */
public final class LegendaryRewardEligibilityRule {

    private final boolean mustBePresentAtStart;
    private final boolean mustBePresentAtSuccess;
    private final boolean mustBeAliveAtSuccess;

    private LegendaryRewardEligibilityRule(
            boolean mustBePresentAtStart, boolean mustBePresentAtSuccess, boolean mustBeAliveAtSuccess) {
        this.mustBePresentAtStart = mustBePresentAtStart;
        this.mustBePresentAtSuccess = mustBePresentAtSuccess;
        this.mustBeAliveAtSuccess = mustBeAliveAtSuccess;
    }

    public static LegendaryRewardEligibilityRule defaultRule() {
        return new LegendaryRewardEligibilityRule(true, true, true);
    }

    public boolean mustBePresentAtStart() {
        return mustBePresentAtStart;
    }

    public boolean mustBePresentAtSuccess() {
        return mustBePresentAtSuccess;
    }

    public boolean mustBeAliveAtSuccess() {
        return mustBeAliveAtSuccess;
    }
}

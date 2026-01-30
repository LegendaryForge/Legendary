package io.github.legendaryforge.legendary.content.encounter.toystorm;

import io.github.legendaryforge.legendary.content.reward.RewardEvaluator;
import java.util.Objects;

/**
 * Deterministic evaluator for ToyStorm.
 *
 * <p>Dogfood rule: eligible iff there is at least 1 participant at end.
 */
public final class ToyStormRewardEvaluator implements RewardEvaluator<ToyStormEndSummary, ToyStormRewardDecision> {

    @Override
    public ToyStormRewardDecision evaluate(ToyStormEndSummary summary) {
        Objects.requireNonNull(summary, "summary");
        return new ToyStormRewardDecision(summary.participantsAtEnd() > 0);
    }
}

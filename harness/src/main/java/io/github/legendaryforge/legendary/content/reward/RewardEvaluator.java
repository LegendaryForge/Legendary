package io.github.legendaryforge.legendary.content.reward;

import java.util.Objects;

/**
 * Deterministic evaluator that maps an encounter end summary into a reward decision.
 *
 * <p>Lives in LegendaryContent: we dogfood reward seams here before considering any promotion.
 */
public interface RewardEvaluator<S, D extends RewardDecision> {

    D evaluate(S summary);

    default D evaluateNonNull(S summary) {
        Objects.requireNonNull(summary, "summary");
        return evaluate(summary);
    }
}

package io.github.legendaryforge.legendary.core.internal.legendary.start;

/** Default start-gate policy: fail closed when the party has an active penalty. */
public final class DefaultLegendaryStartPolicy implements LegendaryStartPolicy {

    @Override
    public LegendaryStartDecision evaluate(LegendaryStartRequest request) {
        if (request.anyPartyMemberHasActivePenalty()) {
            return LegendaryStartDecision.deny("party_has_active_penalty");
        }
        return LegendaryStartDecision.allow();
    }
}

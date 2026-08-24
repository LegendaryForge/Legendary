package io.github.legendaryforge.legendary.core.api.legendary.access;

import java.util.UUID;

/**
 * Default access policy for Legendary Encounters:
 *
 * <ul>
 *   <li>Only owner party members may participate.</li>
 *   <li>Late joiners to the owner party may spectate until the encounter resets.</li>
 *   <li>Non-party players may spectate only when visibility is WORLD_VISIBLE.</li>
 *   <li>Otherwise access is denied.</li>
 * </ul>
 */
public final class DefaultLegendaryAccessPolicy implements LegendaryAccessPolicy {

    @Override
    public LegendaryAccessDecision evaluate(LegendaryAccessRequest request) {
        UUID ownerPartyId = request.ownerPartyId();
        UUID playerPartyId = request.playerPartyId().orElse(null);

        boolean inOwnerParty = ownerPartyId.equals(playerPartyId);
        if (inOwnerParty) {
            if (request.lateJoiner()) {
                return LegendaryAccessDecision.allowSpectate("late_joiner");
            }
            return LegendaryAccessDecision.allowParticipation();
        }

        if (request.visibilityMode() == LegendaryVisibilityMode.WORLD_VISIBLE) {
            return LegendaryAccessDecision.allowSpectate("world_visible");
        }

        return LegendaryAccessDecision.deny("not_in_owner_party");
    }
}

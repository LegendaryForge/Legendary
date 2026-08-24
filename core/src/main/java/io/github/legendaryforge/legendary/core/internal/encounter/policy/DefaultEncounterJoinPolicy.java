package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.identity.PlayerDirectory;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Minimal, platform-agnostic reference implementation of {@link EncounterJoinPolicy}.
 *
 * <p>This implementation is conservative and deterministic. Where platform information would be
 * required (e.g., party membership, invites), it fails closed.</p>
 */
public final class DefaultEncounterJoinPolicy implements EncounterJoinPolicy {

    @Override
    public JoinResult evaluate(
            UUID playerId,
            EncounterDefinition definition,
            EncounterContext context,
            ParticipationRole role,
            Optional<PlayerDirectory> players,
            Optional<PartyDirectory> parties) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(parties, "parties");

        if (players.isPresent() && !players.get().isKnown(playerId)) {
            return JoinResult.DENIED_POLICY;
        }

        // Spectator gating first.
        if (role == ParticipationRole.SPECTATOR) {
            if (definition.spectatorPolicy() == SpectatorPolicy.DISALLOW) {
                return JoinResult.DENIED_POLICY;
            }
            // View-only spectators are governed solely by SpectatorPolicy in the v0.1 reference policy.
            // EncounterAccessPolicy applies to active participation, not passive viewing.
            return JoinResult.SUCCESS;
        }

        EncounterAccessPolicy access = definition.accessPolicy();
        switch (access) {
            case PUBLIC:
                return JoinResult.SUCCESS;

            case PARTY_ONLY:
                return evaluatePartyOnly(playerId, context, parties);

            case INVITE_ONLY:
            default:
                // No invite mechanism in v0.1 reference policy.
                return JoinResult.DENIED_POLICY;
        }
    }

    private static JoinResult evaluatePartyOnly(
            UUID playerId, EncounterContext context, Optional<PartyDirectory> parties) {
        Optional<UUID> partyId = context.partyId();
        if (partyId.isEmpty()) {
            return JoinResult.DENIED_POLICY;
        }
        if (parties.isEmpty()) {
            return JoinResult.DENIED_POLICY;
        }
        return parties.get().isMember(partyId.get(), playerId) ? JoinResult.SUCCESS : JoinResult.DENIED_POLICY;
    }
}

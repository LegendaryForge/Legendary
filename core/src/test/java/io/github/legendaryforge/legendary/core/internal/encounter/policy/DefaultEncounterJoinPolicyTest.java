package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.identity.PlayerDirectory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class DefaultEncounterJoinPolicyTest {

    private final DefaultEncounterJoinPolicy policy = new DefaultEncounterJoinPolicy();

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("cases")
    void evaluate_isDeterministicAndFailClosed(Case c) {
        JoinResult actual = policy.evaluate(c.playerId, c.definition, c.context, c.role, c.players, c.parties);
        assertEquals(c.expected, actual);
    }

    static Stream<Case> cases() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");

        PlayerDirectory knownPlayer = new PlayerDirectory() {
            @Override
            public boolean isKnown(UUID playerId) {
                return true;
            }

            @Override
            public Optional<String> displayName(UUID playerId) {
                return Optional.empty();
            }
        };
        PlayerDirectory unknownPlayer = new PlayerDirectory() {
            @Override
            public boolean isKnown(UUID playerId) {
                return false;
            }

            @Override
            public Optional<String> displayName(UUID playerId) {
                return Optional.empty();
            }
        };

        PartyDirectory memberParty = new PartyDirectory() {
            @Override
            public boolean isKnown(UUID partyId) {
                return partyId.equals(party);
            }

            @Override
            public Optional<java.util.Set<UUID>> members(UUID partyId) {
                return partyId.equals(party) ? Optional.of(java.util.Set.of(player)) : Optional.empty();
            }
        };

        PartyDirectory nonMemberParty = new PartyDirectory() {
            @Override
            public boolean isKnown(UUID partyId) {
                return true;
            }

            @Override
            public Optional<java.util.Set<UUID>> members(UUID partyId) {
                return Optional.of(java.util.Set.of());
            }
        };

        EncounterContext noPartyCtx = context(Optional.empty());
        EncounterContext partyCtx = context(Optional.of(party));

        EncounterDefinition publicAllowSpectate = def(EncounterAccessPolicy.PUBLIC, SpectatorPolicy.ALLOW_VIEW_ONLY);
        EncounterDefinition publicNoSpectate = def(EncounterAccessPolicy.PUBLIC, SpectatorPolicy.DISALLOW);
        EncounterDefinition partyOnlyAllowSpectate =
                def(EncounterAccessPolicy.PARTY_ONLY, SpectatorPolicy.ALLOW_VIEW_ONLY);
        EncounterDefinition inviteOnlyAllowSpectate =
                def(EncounterAccessPolicy.INVITE_ONLY, SpectatorPolicy.ALLOW_VIEW_ONLY);

        return Stream.of(
                Case.of(
                        "unknown player (players present) denies even for PUBLIC participant",
                        player,
                        publicAllowSpectate,
                        noPartyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(unknownPlayer),
                        Optional.empty(),
                        JoinResult.DENIED_POLICY),
                Case.of(
                        "PUBLIC participant allows",
                        player,
                        publicAllowSpectate,
                        noPartyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(knownPlayer),
                        Optional.empty(),
                        JoinResult.SUCCESS),
                Case.of(
                        "INVITE_ONLY participant denies (no invite mechanism v0.1)",
                        player,
                        inviteOnlyAllowSpectate,
                        noPartyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(knownPlayer),
                        Optional.empty(),
                        JoinResult.DENIED_POLICY),
                Case.of(
                        "PARTY_ONLY participant denies when context.partyId missing",
                        player,
                        partyOnlyAllowSpectate,
                        noPartyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(knownPlayer),
                        Optional.of(memberParty),
                        JoinResult.DENIED_POLICY),
                Case.of(
                        "PARTY_ONLY participant denies when PartyDirectory missing",
                        player,
                        partyOnlyAllowSpectate,
                        partyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(knownPlayer),
                        Optional.empty(),
                        JoinResult.DENIED_POLICY),
                Case.of(
                        "PARTY_ONLY participant denies when not a member",
                        player,
                        partyOnlyAllowSpectate,
                        partyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(knownPlayer),
                        Optional.of(nonMemberParty),
                        JoinResult.DENIED_POLICY),
                Case.of(
                        "PARTY_ONLY participant allows when member",
                        player,
                        partyOnlyAllowSpectate,
                        partyCtx,
                        ParticipationRole.PARTICIPANT,
                        Optional.of(knownPlayer),
                        Optional.of(memberParty),
                        JoinResult.SUCCESS),
                Case.of(
                        "SPECTATOR denied when spectator policy DISALLOW (even if PUBLIC)",
                        player,
                        publicNoSpectate,
                        noPartyCtx,
                        ParticipationRole.SPECTATOR,
                        Optional.of(knownPlayer),
                        Optional.empty(),
                        JoinResult.DENIED_POLICY),
                Case.of(
                        "SPECTATOR allowed when spectator policy allows and access is PUBLIC",
                        player,
                        publicAllowSpectate,
                        noPartyCtx,
                        ParticipationRole.SPECTATOR,
                        Optional.of(knownPlayer),
                        Optional.empty(),
                        JoinResult.SUCCESS),
                Case.of(
                        "SPECTATOR under PARTY_ONLY follows same access rules (member allows)",
                        player,
                        partyOnlyAllowSpectate,
                        partyCtx,
                        ParticipationRole.SPECTATOR,
                        Optional.of(knownPlayer),
                        Optional.of(memberParty),
                        JoinResult.SUCCESS),
                Case.of(
                        "SPECTATOR under PARTY_ONLY allowed when spectator policy allows (partyId not required)",
                        player,
                        partyOnlyAllowSpectate,
                        noPartyCtx,
                        ParticipationRole.SPECTATOR,
                        Optional.of(knownPlayer),
                        Optional.of(memberParty),
                        JoinResult.SUCCESS));
    }

    private static EncounterDefinition def(EncounterAccessPolicy access, SpectatorPolicy spectator) {
        return new EncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:encounter");
            }

            @Override
            public String displayName() {
                return "test";
            }

            @Override
            public EncounterAccessPolicy accessPolicy() {
                return access;
            }

            @Override
            public SpectatorPolicy spectatorPolicy() {
                return spectator;
            }

            @Override
            public int maxParticipants() {
                return 0;
            }

            @Override
            public int maxSpectators() {
                return 0;
            }
        };
    }

    private static EncounterContext context(Optional<UUID> partyId) {
        return new EncounterContext() {
            @Override
            public EncounterAnchor anchor() {
                return new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), partyId);
            }

            @Override
            public Map<String, Object> metadata() {
                return Map.of();
            }
        };
    }

    private record Case(
            String name,
            UUID playerId,
            EncounterDefinition definition,
            EncounterContext context,
            ParticipationRole role,
            Optional<PlayerDirectory> players,
            Optional<PartyDirectory> parties,
            JoinResult expected) {
        static Case of(
                String name,
                UUID playerId,
                EncounterDefinition definition,
                EncounterContext context,
                ParticipationRole role,
                Optional<PlayerDirectory> players,
                Optional<PartyDirectory> parties,
                JoinResult expected) {
            return new Case(name, playerId, definition, context, role, players, parties, expected);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

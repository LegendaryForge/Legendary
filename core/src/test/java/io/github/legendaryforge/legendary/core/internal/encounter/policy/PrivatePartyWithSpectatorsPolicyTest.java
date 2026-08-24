package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterState;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PrivatePartyWithSpectatorsPolicyTest {

    private static final EncounterDefinition DEF = def();

    @Test
    void sameParty_canJoin() {
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EncounterContext ctx = new TestContext(party);
        TestEncounter enc = new TestEncounter(EncounterState.RUNNING, 4, 0); // 4 participants pre-filled

        JoinDecision d = new PrivatePartyWithSpectatorsPolicy().decide(enc, ctx, UUID.randomUUID(), Optional.of(party));
        assertEquals(JoinDecision.ALLOW_JOIN, d);
    }

    @Test
    void differentParty_canSpectateWhileRunning() {
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EncounterContext ctx = new TestContext(party);
        TestEncounter enc = new TestEncounter(EncounterState.RUNNING, 5, 0); // full participants

        JoinDecision d = new PrivatePartyWithSpectatorsPolicy()
                .decide(enc, ctx, UUID.randomUUID(), Optional.of(UUID.randomUUID()));
        assertEquals(JoinDecision.ALLOW_SPECTATE, d);
    }

    @Test
    void noPartyRequester_canSpectateWhileRunning() {
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EncounterContext ctx = new TestContext(party);
        TestEncounter enc = new TestEncounter(EncounterState.RUNNING, 5, 0); // full participants

        JoinDecision d = new PrivatePartyWithSpectatorsPolicy().decide(enc, ctx, UUID.randomUUID(), Optional.empty());
        assertEquals(JoinDecision.ALLOW_SPECTATE, d);
    }

    @Test
    void ended_encounter_deniesNonParty() {
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");
        EncounterContext ctx = new TestContext(party);
        TestEncounter enc = new TestEncounter(EncounterState.ENDED, 0, 0);

        JoinDecision d = new PrivatePartyWithSpectatorsPolicy()
                .decide(enc, ctx, UUID.randomUUID(), Optional.of(UUID.randomUUID()));
        assertEquals(JoinDecision.DENY, d);
    }

    private static final class TestContext implements EncounterContext {

        private final EncounterAnchor anchor;

        TestContext(UUID partyId) {
            this.anchor = new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.of(partyId));
        }

        @Override
        public EncounterAnchor anchor() {
            return anchor;
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }

    private static final class TestEncounter implements EncounterInstance {

        private final EncounterState state;
        private final Set<UUID> participantSet = new HashSet<>();
        private final Set<UUID> spectatorSet = new HashSet<>();

        TestEncounter(EncounterState state, int initialParticipants, int initialSpectators) {
            this.state = state;
            for (int i = 0; i < initialParticipants; i++) {
                participantSet.add(UUID.randomUUID());
            }
            for (int i = 0; i < initialSpectators; i++) {
                spectatorSet.add(UUID.randomUUID());
            }
        }

        @Override
        public UUID instanceId() {
            return UUID.fromString("00000000-0000-0000-0000-000000000099");
        }

        @Override
        public EncounterDefinition definition() {
            return DEF;
        }

        @Override
        public EncounterState state() {
            return state;
        }

        @Override
        public Set<UUID> participants() {
            return participantSet;
        }

        @Override
        public Set<UUID> spectators() {
            return spectatorSet;
        }
    }

    private static EncounterDefinition def() {
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
                return EncounterAccessPolicy.PUBLIC;
            }

            @Override
            public SpectatorPolicy spectatorPolicy() {
                return SpectatorPolicy.ALLOW_VIEW_ONLY;
            }

            @Override
            public int maxParticipants() {
                return 5;
            }

            @Override
            public int maxSpectators() {
                return 5;
            }
        };
    }
}

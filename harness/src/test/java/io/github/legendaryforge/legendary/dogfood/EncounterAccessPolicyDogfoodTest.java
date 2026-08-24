package io.github.legendaryforge.legendary.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class EncounterAccessPolicyDogfoodTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    private static final class PolicyDefinition implements EncounterDefinition {

        private final ResourceId id;
        private final EncounterAccessPolicy policy;

        private PolicyDefinition(ResourceId id, EncounterAccessPolicy policy) {
            this.id = id;
            this.policy = policy;
        }

        @Override
        public ResourceId id() {
            return id;
        }

        @Override
        public String displayName() {
            return "Dogfood Access Policy Encounter";
        }

        @Override
        public EncounterAccessPolicy accessPolicy() {
            return policy;
        }

        @Override
        public SpectatorPolicy spectatorPolicy() {
            return SpectatorPolicy.ALLOW_VIEW_ONLY;
        }

        @Override
        public int maxParticipants() {
            return 3;
        }

        @Override
        public int maxSpectators() {
            return 3;
        }
    }

    @Test
    void publicAllowsJoin_partyOnlyAndInviteOnlyFailClosedByDefault() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "arena_access"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        UUID player = UUID.randomUUID();

        EncounterInstance pub = encounters.create(
                new PolicyDefinition(ResourceId.of("legendarydogfood", "pub"), EncounterAccessPolicy.PUBLIC), ctx);
        assertEquals(JoinResult.SUCCESS, encounters.join(player, pub, ParticipationRole.PARTICIPANT));

        EncounterInstance partyOnly = encounters.create(
                new PolicyDefinition(ResourceId.of("legendarydogfood", "party_only"), EncounterAccessPolicy.PARTY_ONLY),
                ctx);
        assertEquals(
                JoinResult.DENIED_POLICY, encounters.join(UUID.randomUUID(), partyOnly, ParticipationRole.PARTICIPANT));

        EncounterInstance inviteOnly = encounters.create(
                new PolicyDefinition(
                        ResourceId.of("legendarydogfood", "invite_only"), EncounterAccessPolicy.INVITE_ONLY),
                ctx);
        assertEquals(
                JoinResult.DENIED_POLICY,
                encounters.join(UUID.randomUUID(), inviteOnly, ParticipationRole.PARTICIPANT));
    }
}

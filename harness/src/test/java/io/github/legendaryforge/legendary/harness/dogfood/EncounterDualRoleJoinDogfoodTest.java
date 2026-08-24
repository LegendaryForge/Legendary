package io.github.legendaryforge.legendary.harness.dogfood;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

public final class EncounterDualRoleJoinDogfoodTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    private static final class TestDefinition implements EncounterDefinition {

        private final ResourceId id;

        private TestDefinition(ResourceId id) {
            this.id = id;
        }

        @Override
        public ResourceId id() {
            return id;
        }

        @Override
        public String displayName() {
            return "Dogfood Dual Role Join";
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
            return 2;
        }

        @Override
        public int maxSpectators() {
            return 2;
        }
    }

    @Test
    void playerCannotBeBothParticipantAndSpectator() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterDefinition def = new TestDefinition(ResourceId.of("dogfood", "dual_role_join"));
        EncounterAnchor anchor =
                EncounterAnchor.of(ResourceId.of("dogfood", "world"), ResourceId.of("dogfood", "arena_dual_role_join"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);
        UUID player = UUID.randomUUID();

        // First join succeeds.
        JoinResult first = encounters.join(player, instance, ParticipationRole.PARTICIPANT);
        assertTrue(first == JoinResult.SUCCESS);

        // Second join as spectator must not result in dual-membership.
        JoinResult second = encounters.join(player, instance, ParticipationRole.SPECTATOR);
        assertTrue(second == JoinResult.SUCCESS
                || second == JoinResult.DENIED_POLICY
                || second == JoinResult.DENIED_STATE);

        // Hard invariant: never in both sets.
        assertFalse(instance.participants().contains(player)
                && instance.spectators().contains(player));

        // Additionally, if second succeeded, enforce exclusivity (exactly one set contains player).
        if (second == JoinResult.SUCCESS) {
            boolean inParticipants = instance.participants().contains(player);
            boolean inSpectators = instance.spectators().contains(player);
            assertTrue(inParticipants ^ inSpectators);
        }
    }
}

package io.github.legendaryforge.legendary.dogfood;

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

public final class EncounterRoleSwitchDogfoodTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata)
            implements EncounterContext {}

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
            return "Dogfood Role Switch";
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
    void spectatorToParticipantNeverCreatesDualMembershipAndMaintainsExclusiveRoleSets() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterDefinition def = new TestDefinition(ResourceId.of("dogfood", "role_switch"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("dogfood", "world"),
                ResourceId.of("dogfood", "arena_role_switch")
        );
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);
        UUID player = UUID.randomUUID();

        // Spectator join succeeds and must not grant participant membership.
        assertTrue(encounters.join(player, instance, ParticipationRole.SPECTATOR) == JoinResult.SUCCESS);
        assertTrue(instance.spectators().contains(player));
        assertFalse(instance.participants().contains(player));
        assertFalse(instance.participants().contains(player) && instance.spectators().contains(player));

        // Attempt to switch role to participant.
        JoinResult second = encounters.join(player, instance, ParticipationRole.PARTICIPANT);
        assertTrue(second == JoinResult.SUCCESS || second == JoinResult.DENIED_POLICY || second == JoinResult.DENIED_STATE || second == JoinResult.DENIED_FULL);

        // Hard invariant: never in both sets.
        assertFalse(instance.participants().contains(player) && instance.spectators().contains(player));

        // If switch is permitted, enforce exclusivity (exactly one set contains player).
        if (second == JoinResult.SUCCESS) {
            boolean inParticipants = instance.participants().contains(player);
            boolean inSpectators = instance.spectators().contains(player);
            assertTrue(inParticipants ^ inSpectators);
        }
    }
}

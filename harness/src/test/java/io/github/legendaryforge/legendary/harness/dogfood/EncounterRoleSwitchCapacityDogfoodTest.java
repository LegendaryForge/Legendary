package io.github.legendaryforge.legendary.harness.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

public final class EncounterRoleSwitchCapacityDogfoodTest {

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
            return "Dogfood Role Switch Capacity";
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
            return 1;
        }

        @Override
        public int maxSpectators() {
            return 1;
        }
    }

    @Test
    void roleSwitchCannotBypassCapacityAndNeverCreatesDualMembership() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterDefinition def = new TestDefinition(ResourceId.of("dogfood", "role_switch_capacity"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("dogfood", "world"), ResourceId.of("dogfood", "arena_role_switch_capacity"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        UUID p1 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, encounters.join(p1, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, encounters.join(s1, instance, ParticipationRole.SPECTATOR));

        assertEquals(1, instance.participants().size());
        assertEquals(1, instance.spectators().size());

        // Attempt to switch spectator into participants when participant cap is already full.
        JoinResult switchResult = encounters.join(s1, instance, ParticipationRole.PARTICIPANT);
        assertTrue(switchResult == JoinResult.DENIED_FULL
                || switchResult == JoinResult.DENIED_POLICY
                || switchResult == JoinResult.DENIED_STATE);

        // Counts must remain within caps.
        assertTrue(instance.participants().size() <= def.maxParticipants());
        assertTrue(instance.spectators().size() <= def.maxSpectators());

        // Hard invariant: never in both sets.
        assertFalse(
                instance.participants().contains(s1) && instance.spectators().contains(s1));

        // If implementation allows switching by moving, it must still respect caps.
        if (switchResult == JoinResult.SUCCESS) {
            assertEquals(1, instance.participants().size());
            assertEquals(0, instance.spectators().size());
            assertTrue(instance.participants().contains(s1));
        }
    }
}

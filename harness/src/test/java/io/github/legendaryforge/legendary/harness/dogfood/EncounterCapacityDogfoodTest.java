package io.github.legendaryforge.legendary.harness.dogfood;

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

public final class EncounterCapacityDogfoodTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    private static final class CapacityDefinition implements EncounterDefinition {

        private final ResourceId id;
        private final int maxParticipants;
        private final int maxSpectators;

        private CapacityDefinition(ResourceId id, int maxParticipants, int maxSpectators) {
            this.id = id;
            this.maxParticipants = maxParticipants;
            this.maxSpectators = maxSpectators;
        }

        @Override
        public ResourceId id() {
            return id;
        }

        @Override
        public String displayName() {
            return "Dogfood Capacity Encounter";
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
            return maxParticipants;
        }

        @Override
        public int maxSpectators() {
            return maxSpectators;
        }
    }

    @Test
    void enforcesParticipantAndSpectatorCaps() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterDefinition def = new CapacityDefinition(ResourceId.of("legendarydogfood", "encounter_capacity"), 2, 1);

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "arena_capacity"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        // Participants: 2 allowed
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(
                JoinResult.DENIED_FULL, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));

        // Spectators: 1 allowed
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(JoinResult.DENIED_FULL, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
    }
}

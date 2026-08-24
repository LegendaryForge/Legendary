package io.github.legendaryforge.legendary.harness.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class EncounterRosterSnapshotDogfoodTest {

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
            return "Dogfood Roster Snapshot Encounter";
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
            return 3;
        }

        @Override
        public int maxSpectators() {
            return 3;
        }
    }

    @Test
    void participantsRosterAtEndMatchesSuccessfulParticipantJoins() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterDefinition def = new TestDefinition(ResourceId.of("legendarydogfood", "encounter_roster_snapshot"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "arena_roster"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, encounters.join(p1, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, encounters.join(p2, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, encounters.join(s1, instance, ParticipationRole.SPECTATOR));

        encounters.end(instance, EndReason.COMPLETED);
        Set<UUID> participantsAtEnd = instance.participants();

        assertEquals(Set.of(p1, p2), participantsAtEnd);

        // Duplicate end should not change roster.
        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(Set.of(p1, p2), instance.participants());
    }
}

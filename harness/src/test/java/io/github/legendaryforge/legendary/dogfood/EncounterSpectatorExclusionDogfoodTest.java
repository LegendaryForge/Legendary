package io.github.legendaryforge.legendary.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class EncounterSpectatorExclusionDogfoodTest {

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
            return "Dogfood Spectator Exclusion";
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
    void spectatorsNeverAppearInParticipantRosterSnapshot() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        EncounterDefinition def = new TestDefinition(ResourceId.of("dogfood", "spectator_exclusion"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("dogfood", "world"), ResourceId.of("dogfood", "arena_spectator_exclusion"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        UUID p1 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, encounters.join(p1, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, encounters.join(s1, instance, ParticipationRole.SPECTATOR));

        encounters.end(instance, EndReason.COMPLETED);

        // Snapshot should only include participant(s), never spectators.
        assertEquals(1, instance.participants().size());
        assertFalse(instance.participants().contains(s1));
    }
}

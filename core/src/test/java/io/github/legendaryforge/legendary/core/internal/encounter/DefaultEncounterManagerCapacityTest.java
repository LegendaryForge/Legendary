package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DefaultEncounterManagerCapacityTest {

    @Test
    void participantCapacityEnforced() {
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.empty());
        EncounterInstance inst = mgr.create(def(1, 10), ctx());

        UUID p1 = UUID.fromString("00000000-0000-0000-0000-0000000000A1");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-0000000000A2");

        assertEquals(JoinResult.SUCCESS, mgr.join(p1, inst, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.DENIED_FULL, mgr.join(p2, inst, ParticipationRole.PARTICIPANT));
    }

    @Test
    void spectatorCapacityEnforced() {
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.empty());
        EncounterInstance inst = mgr.create(def(10, 1), ctx());

        UUID s1 = UUID.fromString("00000000-0000-0000-0000-0000000000B1");
        UUID s2 = UUID.fromString("00000000-0000-0000-0000-0000000000B2");

        assertEquals(JoinResult.SUCCESS, mgr.join(s1, inst, ParticipationRole.SPECTATOR));
        assertEquals(JoinResult.DENIED_FULL, mgr.join(s2, inst, ParticipationRole.SPECTATOR));
    }

    private static EncounterDefinition def(int maxParticipants, int maxSpectators) {
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
                return maxParticipants;
            }

            @Override
            public int maxSpectators() {
                return maxSpectators;
            }
        };
    }

    private static EncounterContext ctx() {
        UUID party = UUID.fromString("00000000-0000-0000-0000-000000000002");
        return new EncounterContext() {
            @Override
            public EncounterAnchor anchor() {
                return new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.of(party));
            }

            @Override
            public Map<String, Object> metadata() {
                return Map.of();
            }
        };
    }
}

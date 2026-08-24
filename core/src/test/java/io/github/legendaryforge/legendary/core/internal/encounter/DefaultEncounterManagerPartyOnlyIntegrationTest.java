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

final class DefaultEncounterManagerPartyOnlyIntegrationTest {

    @Test
    void partyOnly_participantDenies_whenPartyDirectoryMissing_butSpectatorAllowed() {
        DefaultEncounterManager mgr = new DefaultEncounterManager(Optional.empty(), Optional.empty(), Optional.empty());

        EncounterInstance inst = mgr.create(def(), ctxWithParty());

        UUID player = UUID.randomUUID();

        assertEquals(JoinResult.DENIED_POLICY, mgr.join(player, inst, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, mgr.join(player, inst, ParticipationRole.SPECTATOR));
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
                return EncounterAccessPolicy.PARTY_ONLY;
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
        };
    }

    private static EncounterContext ctxWithParty() {
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

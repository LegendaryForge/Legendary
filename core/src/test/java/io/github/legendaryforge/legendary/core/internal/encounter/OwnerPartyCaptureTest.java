package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterId;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OwnerPartyCaptureTest {

    @Test
    void plainDefinitionsDoNotCaptureOwnerParty() {
        assertFalse(plain().capturesOwnerParty());
    }

    @Test
    void legendaryDefinitionsCaptureOwnerParty() {
        assertTrue(legendary().capturesOwnerParty());
    }

    private static EncounterDefinition plain() {
        return new EncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:plain");
            }

            @Override
            public String displayName() {
                return "plain";
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
                return 4;
            }

            @Override
            public int maxSpectators() {
                return 4;
            }
        };
    }

    private static LegendaryEncounterDefinition legendary() {
        return new LegendaryEncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:legendary");
            }

            @Override
            public String displayName() {
                return "legendary";
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
                return 4;
            }

            @Override
            public int maxSpectators() {
                return 4;
            }

            @Override
            public LegendaryEncounterId legendaryId() {
                return LegendaryEncounterId.of("test-legendary");
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> completionCooldown() {
                return Optional.empty();
            }
        };
    }
}

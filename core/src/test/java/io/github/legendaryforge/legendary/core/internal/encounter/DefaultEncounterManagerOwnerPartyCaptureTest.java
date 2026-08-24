package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterId;
import io.github.legendaryforge.legendary.core.internal.legendary.instance.LegendaryEncounterInstanceView;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises the actual {@code capturesOwnerParty()} branch in {@link DefaultEncounterManager#create}
 * -- not just the default-method dispatch covered by {@link OwnerPartyCaptureTest}. Asserts that a
 * {@link LegendaryEncounterDefinition} created against a party context actually captures the owner
 * party id and its membership snapshot, and that a plain {@link EncounterDefinition} in the same
 * context captures nothing.
 */
final class DefaultEncounterManagerOwnerPartyCaptureTest {

    private static final UUID PARTY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Test
    void legendaryDefinition_capturesOwnerPartyIdAndMembersAtStart() {
        DefaultEncounterManager mgr =
                new DefaultEncounterManager(Optional.empty(), Optional.of(partyDirectory()), Optional.empty());

        EncounterInstance inst = mgr.create(legendaryDef(), ctxWithParty());

        assertTrue(inst instanceof LegendaryEncounterInstanceView);
        LegendaryEncounterInstanceView view = (LegendaryEncounterInstanceView) inst;

        assertEquals(Optional.of(PARTY_ID), view.ownerPartyId());
        assertEquals(Set.of(MEMBER_1, MEMBER_2), view.ownerPartyMembersAtStart());
    }

    @Test
    void plainDefinition_capturesNothing_evenWithPartyContextAndDirectory() {
        DefaultEncounterManager mgr =
                new DefaultEncounterManager(Optional.empty(), Optional.of(partyDirectory()), Optional.empty());

        EncounterInstance inst = mgr.create(plainDef(), ctxWithParty());

        assertTrue(inst instanceof LegendaryEncounterInstanceView);
        LegendaryEncounterInstanceView view = (LegendaryEncounterInstanceView) inst;

        assertEquals(Optional.empty(), view.ownerPartyId());
        assertEquals(Set.of(), view.ownerPartyMembersAtStart());
    }

    private static PartyDirectory partyDirectory() {
        return new PartyDirectory() {
            @Override
            public boolean isKnown(UUID partyId) {
                return PARTY_ID.equals(partyId);
            }

            @Override
            public Optional<Set<UUID>> members(UUID partyId) {
                if (PARTY_ID.equals(partyId)) {
                    return Optional.of(Set.of(MEMBER_1, MEMBER_2));
                }
                return Optional.empty();
            }
        };
    }

    private static EncounterContext ctxWithParty() {
        return new EncounterContext() {
            @Override
            public EncounterAnchor anchor() {
                return new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.of(PARTY_ID));
            }

            @Override
            public Map<String, Object> metadata() {
                return Map.of();
            }
        };
    }

    private static EncounterDefinition plainDef() {
        return new EncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:plain-capture");
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

    private static LegendaryEncounterDefinition legendaryDef() {
        return new LegendaryEncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:legendary-capture");
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
                return LegendaryEncounterId.of("test-legendary-capture");
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

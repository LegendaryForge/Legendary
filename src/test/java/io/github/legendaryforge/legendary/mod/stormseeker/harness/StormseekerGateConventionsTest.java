package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import org.junit.jupiter.api.Test;

public final class StormseekerGateConventionsTest {

    /**
     * This is a documentation-as-test file.
     *
     * The intent is to lock naming conventions for gate keys and reason codes so Stormseeker and
     * LegendaryCore can evolve without silent string drift.
     */
    @Test
    void stormseekerGateKeyAndReasonCodeConventionsAreStable() {
        // Gate keys (what we register/evaluate in GateService)
        ResourceId gateActivation = ResourceId.of("stormseeker", "activation");
        ResourceId gateDungeonEntry = ResourceId.of("stormseeker", "dungeon_entry");
        ResourceId gateBossArenaEntry = ResourceId.of("stormseeker", "boss_arena_entry");

        // Reason codes (what gates return when denying)
        ResourceId denyNotOnQuestStep = ResourceId.of("stormseeker", "not_on_required_quest_step");
        ResourceId denyMissingItem = ResourceId.of("stormseeker", "missing_required_item");
        ResourceId denyPartyNotEligible = ResourceId.of("stormseeker", "party_not_eligible");

        // Basic sanity: ensure they are non-null and in the expected namespace.
        assertEquals("stormseeker", gateActivation.namespace());
        assertEquals("stormseeker", gateDungeonEntry.namespace());
        assertEquals("stormseeker", gateBossArenaEntry.namespace());

        assertEquals("stormseeker", denyNotOnQuestStep.namespace());
        assertEquals("stormseeker", denyMissingItem.namespace());
        assertEquals("stormseeker", denyPartyNotEligible.namespace());

        // IDs should be lowercase snake_case for stability and ease of integration with telemetry/logs.
        assertTrue(gateActivation.path().matches("[a-z0-9_]+"), gateActivation.path());
        assertTrue(gateDungeonEntry.path().matches("[a-z0-9_]+"), gateDungeonEntry.path());
        assertTrue(gateBossArenaEntry.path().matches("[a-z0-9_]+"), gateBossArenaEntry.path());

        assertTrue(denyNotOnQuestStep.path().matches("[a-z0-9_]+"), denyNotOnQuestStep.path());
        assertTrue(denyMissingItem.path().matches("[a-z0-9_]+"), denyMissingItem.path());
        assertTrue(denyPartyNotEligible.path().matches("[a-z0-9_]+"), denyPartyNotEligible.path());
    }
}

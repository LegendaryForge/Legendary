package io.github.legendaryforge.legendary.harness.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.harness.dogfood.support.EndRosterCapture;
import io.github.legendaryforge.legendary.harness.dogfood.support.RewardDeliveryService;
import io.github.legendaryforge.legendary.harness.dogfood.support.TestContext;
import io.github.legendaryforge.legendary.harness.dogfood.support.TestDefinition;
import io.github.legendaryforge.legendary.harness.dogfood.support.TestEventCounter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ScenarioTest {

    @Test
    void toyLegendaryScenario_startOnce_rewardOnce_endOnce_rosterAtEnd() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        TestEventCounter events = new TestEventCounter(runtime.events());
        RewardDeliveryService rewards = new RewardDeliveryService(runtime.events(), "scenario-reward-table");
        EndRosterCapture roster = new EndRosterCapture(runtime.events(), encounters);

        ResourceId world = ResourceId.of("legendarydogfood", "world");
        ResourceId anchorId = ResourceId.of("legendarydogfood", "anchor-scenario");
        EncounterAnchor anchor = EncounterAnchor.of(world, anchorId);

        EncounterContext context = new TestContext(anchor, Map.of("scenario", "toy-legendary"));

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "scenario"),
                "Toy Legendary Scenario",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                3,
                3);

        EncounterInstance instance = encounters.create(def, context);
        EncounterKey key = EncounterKey.of(def, context);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        // First participant join triggers start exactly once.
        assertEquals(JoinResult.SUCCESS, encounters.join(p1, instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, events.startedFor(instance.instanceId()));

        // Additional joins do not re-trigger start.
        assertEquals(JoinResult.SUCCESS, encounters.join(p2, instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, events.startedFor(instance.instanceId()));

        assertEquals(JoinResult.SUCCESS, encounters.join(s1, instance, ParticipationRole.SPECTATOR));
        assertEquals(1, events.startedFor(instance.instanceId()));

        // Simulate a leaver before end.
        encounters.leave(p2, instance);

        // End twice (retry) should still produce exactly-once end + reward.
        encounters.end(instance, EndReason.COMPLETED);
        encounters.end(instance, EndReason.COMPLETED);

        assertEquals(1, events.endedFor(instance.instanceId()));
        assertEquals(1, rewards.deliveries());
        assertTrue(rewards.hasDeliveredFor(key));

        // Roster captured at end time should include p1, exclude p2 (left) and s1 (spectator).
        Set<UUID> atEnd = roster.participantsAtEnd(instance.instanceId());
        assertTrue(atEnd.contains(p1));
        assertFalse(atEnd.contains(p2));
        assertFalse(atEnd.contains(s1));
        assertEquals(1, atEnd.size());
    }
}

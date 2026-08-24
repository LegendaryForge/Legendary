package io.github.legendaryforge.legendary.dogfood;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterState;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.dogfood.support.TestContext;
import io.github.legendaryforge.legendary.dogfood.support.TestDefinition;
import io.github.legendaryforge.legendary.dogfood.support.TestEventCounter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class SmokeTest {

    @Test
    void createJoinSpectateLeaveEnd_flow_realCoreRuntime() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        ResourceId world = ResourceId.of("legendarydogfood", "world");
        ResourceId anchorId = ResourceId.of("legendarydogfood", "anchor-alpha");
        EncounterAnchor anchor = EncounterAnchor.of(world, anchorId);

        EncounterContext context = new TestContext(anchor, Map.of("note", "dogfood"));

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy"),
                "Toy Encounter",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                1,
                1
        );

        EncounterInstance instance = manager.create(def, context);
        assertNotNull(instance);
        assertEquals(EncounterState.CREATED, instance.state());

        EncounterKey key = EncounterKey.of(def, context);
        assertEquals(instance.instanceId(), manager.byKey(key).orElseThrow().instanceId());

        UUID p1 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, manager.join(p1, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, manager.join(s1, instance, ParticipationRole.SPECTATOR));

        UUID p2 = UUID.randomUUID();
        assertEquals(JoinResult.DENIED_FULL, manager.join(p2, instance, ParticipationRole.PARTICIPANT));

        manager.leave(s1, instance);
        assertEquals(0, instance.spectators().size());

        manager.end(instance, EndReason.COMPLETED);
        assertEquals(EncounterState.ENDED, instance.state());

        UUID late = UUID.randomUUID();
        assertEquals(JoinResult.DENIED_STATE, manager.join(late, instance, ParticipationRole.SPECTATOR));
    }

    @Test
    void spectatorDisallow_deniesSpectator_allowsParticipant_realCoreRuntime() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy2"),
                "Toy Encounter 2",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.DISALLOW,
                1,
                1
        );

        EncounterInstance instance = manager.create(def, context);

        assertEquals(JoinResult.SUCCESS, manager.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.DENIED_POLICY, manager.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
    }

    @Test
    void partyOnly_failClosed_participantDenied_spectatorAllowed_withoutPartyDirectory() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy3"),
                "Toy Encounter 3",
                EncounterAccessPolicy.PARTY_ONLY,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                1,
                1
        );

        EncounterInstance instance = manager.create(def, context);

        assertEquals(JoinResult.DENIED_POLICY, manager.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, manager.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
    }

    @Test
    void inviteOnly_failClosed_participantDenied_spectatorAllowed_withoutInviteSystem() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy-invite"),
                "Toy Invite Encounter",
                EncounterAccessPolicy.INVITE_ONLY,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                1,
                1
        );

        EncounterInstance instance = manager.create(def, context);

        // Conservative expectation: without any invite directory/seam, INVITE_ONLY should fail-closed for participants.
        assertEquals(JoinResult.DENIED_POLICY, manager.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));

        // Spectator behavior should be governed by spectator policy.
        assertEquals(JoinResult.SUCCESS, manager.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
    }

    @Test
    void leave_isIdempotent_forParticipantAndSpectator() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "anchor-leave"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy4"),
                "Toy Encounter 4",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                2,
                2
        );

        EncounterInstance instance = manager.create(def, context);

        UUID p = UUID.randomUUID();
        UUID s = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, manager.join(p, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, manager.join(s, instance, ParticipationRole.SPECTATOR));
        assertEquals(1, instance.participants().size());
        assertEquals(1, instance.spectators().size());

        assertDoesNotThrow(() -> manager.leave(p, instance));
        assertDoesNotThrow(() -> manager.leave(p, instance));
        assertEquals(0, instance.participants().size());

        assertDoesNotThrow(() -> manager.leave(s, instance));
        assertDoesNotThrow(() -> manager.leave(s, instance));
        assertEquals(0, instance.spectators().size());
    }

    @Test
    void end_isIdempotent_stateStable() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "anchor-end"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy5"),
                "Toy Encounter 5",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                1,
                1
        );

        EncounterInstance instance = manager.create(def, context);
        assertEquals(EncounterState.CREATED, instance.state());

        assertDoesNotThrow(() -> manager.end(instance, EndReason.CANCELLED));
        assertEquals(EncounterState.ENDED, instance.state());

        assertDoesNotThrow(() -> manager.end(instance, EndReason.CANCELLED));
        assertEquals(EncounterState.ENDED, instance.state());

        assertEquals(JoinResult.DENIED_STATE, manager.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
    }

    @Test
    void startedEvent_emittedOnce_onFirstSuccessfulParticipantJoin() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        TestEventCounter counter = new TestEventCounter(runtime.events());
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "anchor-start"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy6"),
                "Toy Encounter 6",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                2,
                2
        );

        EncounterInstance instance = manager.create(def, context);

        assertEquals(JoinResult.SUCCESS, manager.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, counter.startedFor(instance.instanceId()));

        assertEquals(JoinResult.SUCCESS, manager.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, counter.startedFor(instance.instanceId()));

        assertEquals(JoinResult.SUCCESS, manager.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(1, counter.startedFor(instance.instanceId()));
    }

    @Test
    void endedEvent_emittedOnce_evenIfEndCalledTwice() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        TestEventCounter counter = new TestEventCounter(runtime.events());
        EncounterManager manager = runtime.encounters();

        EncounterAnchor anchor = EncounterAnchor.of(ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "anchor-ended"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy7"),
                "Toy Encounter 7",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                1,
                1
        );

        EncounterInstance instance = manager.create(def, context);

        assertDoesNotThrow(() -> manager.end(instance, EndReason.COMPLETED));
        assertEquals(1, counter.endedFor(instance.instanceId()));

        assertDoesNotThrow(() -> manager.end(instance, EndReason.COMPLETED));
        assertEquals(1, counter.endedFor(instance.instanceId()));
    }
}

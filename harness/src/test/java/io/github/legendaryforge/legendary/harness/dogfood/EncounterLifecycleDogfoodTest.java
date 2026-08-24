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
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public final class EncounterLifecycleDogfoodTest {

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
            return "Dogfood Test Encounter";
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
            return 5;
        }
    }

    @Test
    void spectatorDoesNotStart_firstParticipantStartsExactlyOnce_endIsExactlyOnce() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EventBus events = runtime.events();
        EncounterManager encounters = runtime.encounters();

        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        AtomicReference<UUID> startedInstance = new AtomicReference<>();
        AtomicReference<UUID> endedInstance = new AtomicReference<>();

        events.subscribe(EncounterStartedEvent.class, e -> {
            starts.incrementAndGet();
            startedInstance.compareAndSet(null, e.instanceId());
        });

        events.subscribe(EncounterEndedEvent.class, e -> {
            ends.incrementAndGet();
            endedInstance.compareAndSet(null, e.instanceId());
        });

        EncounterDefinition def = new TestDefinition(ResourceId.of("legendarydogfood", "encounter_lifecycle"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "arena"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        UUID spectator = UUID.randomUUID();
        JoinResult sJoin = encounters.join(spectator, instance, ParticipationRole.SPECTATOR);
        assertEquals(JoinResult.SUCCESS, sJoin);
        assertEquals(0, starts.get());

        UUID p1 = UUID.randomUUID();
        JoinResult pJoin = encounters.join(p1, instance, ParticipationRole.PARTICIPANT);
        assertEquals(JoinResult.SUCCESS, pJoin);
        assertEquals(1, starts.get());
        assertEquals(instance.instanceId(), startedInstance.get());

        // Another participant should not retrigger start.
        UUID p2 = UUID.randomUUID();
        JoinResult p2Join = encounters.join(p2, instance, ParticipationRole.PARTICIPANT);
        assertEquals(JoinResult.SUCCESS, p2Join);
        assertEquals(1, starts.get());

        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(1, ends.get());
        assertEquals(instance.instanceId(), endedInstance.get());

        // Double-end should not re-emit.
        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(1, ends.get());
    }
}

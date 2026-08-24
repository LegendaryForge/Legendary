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
import org.junit.jupiter.api.Test;

public final class EncounterEventExactlyOnceDogfoodTest {

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
            return "Dogfood Event Exactly Once";
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
    void startIsParticipantGatedAndStartEndEventsAreExactlyOnce() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();
        EventBus events = runtime.events();

        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();

        events.subscribe(EncounterStartedEvent.class, e -> starts.incrementAndGet());
        events.subscribe(EncounterEndedEvent.class, e -> ends.incrementAndGet());

        EncounterDefinition def = new TestDefinition(ResourceId.of("dogfood", "events_exactly_once"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("dogfood", "world"), ResourceId.of("dogfood", "arena_events_exactly_once"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        // Spectator join must not start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(0, starts.get());

        // First participant join starts exactly once.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, starts.get());

        // Subsequent joins do not retrigger start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, starts.get());

        // End emits exactly once; repeated end does not duplicate.
        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(1, ends.get());

        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(1, ends.get());
    }
}

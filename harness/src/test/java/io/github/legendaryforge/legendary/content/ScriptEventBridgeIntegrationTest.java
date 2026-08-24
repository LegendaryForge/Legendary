package io.github.legendaryforge.legendary.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class ScriptEventBridgeIntegrationTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    private static final class RecordingScript implements EncounterScript {

        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger joins = new AtomicInteger();
        private final AtomicInteger ends = new AtomicInteger();

        @Override
        public void onStart(EncounterInstance instance, UUID triggeringPlayerId) {
            starts.incrementAndGet();
        }

        @Override
        public void onJoin(EncounterInstance instance, UUID playerId, ParticipationRole role) {
            joins.incrementAndGet();
        }

        @Override
        public void onEnd(EncounterInstance instance) {
            ends.incrementAndGet();
        }

        int starts() {
            return starts.get();
        }

        int joins() {
            return joins.get();
        }

        int ends() {
            return ends.get();
        }
    }

    @Test
    void eventBridgeWiresStartEndExactlyOnceAndScriptedManagerWiresJoinOnly() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager core = runtime.encounters();
        EventBus events = runtime.events();

        RecordingScript script = new RecordingScript();

        // Start/End via events
        new ScriptEventBridge(events, core, script);

        // Join via decorator
        EncounterManager encounters = new ScriptedEncounterManager(core, script);

        ToyLightningEncounterDefinition def =
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_event_bridge_integration"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of("note", "event_bridge_integration"));

        EncounterInstance instance = encounters.create(def, ctx);

        // Spectator join should not start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(1, script.joins());
        assertEquals(0, script.starts());
        assertEquals(0, script.ends());

        // First participant join starts exactly once.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(2, script.joins());
        assertEquals(1, script.starts());
        assertEquals(0, script.ends());

        // Additional joins do not retrigger start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(3, script.joins());
        assertEquals(1, script.starts());
        assertEquals(0, script.ends());

        // End should be event-driven exactly once.
        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(1, script.ends());

        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(1, script.ends());
    }
}

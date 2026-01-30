package io.github.legendaryforge.legendary.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LateSpectatorJoinTest {

    private record Ctx(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void spectator_can_join_before_and_after_start_without_retriggering_start() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();

        ToyLightningScript script = new ToyLightningScript();

        // Bridge core lifecycle events into script start/end.
        new ScriptEventBridge(runtime.events(), runtime.encounters(), script);

        // Decorator for join/end hook wiring (content-side).
        EncounterManager encounters = new ScriptedEncounterManager(runtime.encounters(), script);

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"), ResourceId.of("legendarycontent", "arena_late_spectator"));

        EncounterContext ctx = new Ctx(anchor, Map.of("note", "late_spectator"));
        EncounterInstance instance = encounters.create(
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning")), ctx);

        // Spectator joins first: allowed, does NOT start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(0, script.startsFor(instance.instanceId()));

        // First participant join starts exactly once.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, script.startsFor(instance.instanceId()));

        // Late spectator join after start: allowed, must NOT retrigger start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(1, script.startsFor(instance.instanceId()));
    }
}

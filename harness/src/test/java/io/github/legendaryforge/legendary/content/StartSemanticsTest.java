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

final class StartSemanticsTest {

    private record Ctx(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void encounter_starts_only_on_first_participant_join() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();

        ToyLightningScript script = new ToyLightningScript();

        // Wire core lifecycle events into the script (start/end).
        new ScriptEventBridge(runtime.events(), runtime.encounters(), script);

        // Wire join/end calls into the script (content-side integration).
        EncounterManager encounters = new ScriptedEncounterManager(runtime.encounters(), script);

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"), ResourceId.of("legendarycontent", "arena_start_semantics"));

        EncounterContext ctx = new Ctx(anchor, Map.of());
        EncounterInstance instance = encounters.create(
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning")), ctx);

        // Spectator joins first -> should not start.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
        assertEquals(0, script.startsFor(instance.instanceId()));

        // First participant join -> starts exactly once.
        assertEquals(JoinResult.SUCCESS, encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT));
        assertEquals(1, script.startsFor(instance.instanceId()));

        // Further joins must not retrigger start.
        encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR);
        encounters.join(UUID.randomUUID(), instance, ParticipationRole.PARTICIPANT);
        assertEquals(1, script.startsFor(instance.instanceId()));
    }
}

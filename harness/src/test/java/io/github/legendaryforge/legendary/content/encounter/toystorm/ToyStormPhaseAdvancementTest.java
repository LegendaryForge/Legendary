package io.github.legendaryforge.legendary.content.encounter.toystorm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ToyStormPhaseAdvancementTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void participantJoinsAdvanceSpectatorsDoNot() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyStormEncounterDefinition def =
                new ToyStormEncounterDefinition(ResourceId.of("legendarycontent", "toy_storm"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"), ResourceId.of("legendarycontent", "arena_storm_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());
        EncounterInstance instance = encounters.create(def, ctx);

        ToyStormScript script = new ToyStormScript();
        UUID trigger = UUID.randomUUID();
        script.onStart(instance, trigger);
        assertEquals(EncounterPhase.CHARGE, script.phaseFor(instance.instanceId()));

        UUID s1 = UUID.randomUUID();
        encounters.join(s1, instance, ParticipationRole.SPECTATOR);
        script.onJoin(instance, s1, ParticipationRole.SPECTATOR);
        assertEquals(EncounterPhase.CHARGE, script.phaseFor(instance.instanceId()));

        UUID p1 = UUID.randomUUID();
        encounters.join(p1, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p1, ParticipationRole.PARTICIPANT);
        assertEquals(EncounterPhase.DISCHARGE, script.phaseFor(instance.instanceId()));

        UUID p2 = UUID.randomUUID();
        encounters.join(p2, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p2, ParticipationRole.PARTICIPANT);
        assertEquals(EncounterPhase.RECOVERY, script.phaseFor(instance.instanceId()));
    }
}

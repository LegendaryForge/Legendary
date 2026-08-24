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

public final class ToyStormEndSummaryTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void capturesPhaseAndParticipantsAtEndIdempotently() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyStormEncounterDefinition def =
                new ToyStormEncounterDefinition(ResourceId.of("legendarycontent", "toy_storm"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_storm_end_summary_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        ToyStormScript script = new ToyStormScript();
        UUID trigger = UUID.randomUUID();
        script.onStart(instance, trigger);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();

        encounters.join(p1, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p1, ParticipationRole.PARTICIPANT);

        encounters.join(p2, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p2, ParticipationRole.PARTICIPANT);

        script.onEnd(instance);
        ToyStormEndSummary summary = script.endSummaryFor(instance.instanceId());

        assertEquals(instance.instanceId(), summary.instanceId());
        assertEquals(2, summary.participantsAtEnd());
        assertEquals(EncounterPhase.RECOVERY, summary.finalPhase());

        // idempotent end
        script.onEnd(instance);
        ToyStormEndSummary summary2 = script.endSummaryFor(instance.instanceId());
        assertEquals(summary, summary2);
    }
}

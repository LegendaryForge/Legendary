package io.github.legendaryforge.legendary.content;

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

public final class ToyLightningEndSummaryTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void capturesDeterministicEndSummaryIdempotently() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyLightningEncounterDefinition def =
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_lightning_end_summary_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        ToyLightningScript script = new ToyLightningScript();
        UUID trigger = UUID.randomUUID();
        script.onStart(instance, trigger);
        script.onStart(instance, trigger);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        encounters.join(p1, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p1, ParticipationRole.PARTICIPANT);

        encounters.join(p2, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p2, ParticipationRole.PARTICIPANT);

        encounters.join(s1, instance, ParticipationRole.SPECTATOR);
        script.onJoin(instance, s1, ParticipationRole.SPECTATOR);

        script.onEnd(instance);
        ToyLightningEndSummary summary = script.endSummaryFor(instance.instanceId());

        assertEquals(instance.instanceId(), summary.instanceId());
        assertEquals(2, summary.participantsAtEnd());
        assertEquals(4, summary.chargeAtEnd());
        assertEquals(2, summary.starts());
        assertEquals(ToyLightningScript.RewardTier.MINOR, summary.rewardTier());

        // idempotent end
        script.onEnd(instance);
        ToyLightningEndSummary summary2 = script.endSummaryFor(instance.instanceId());
        assertEquals(summary, summary2);
    }
}

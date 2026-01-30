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

public final class ToyStormRewardEvaluatorTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void evaluatesEligibilityDeterministicallyFromEndSummary() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyStormEncounterDefinition def =
                new ToyStormEncounterDefinition(ResourceId.of("legendarycontent", "toy_storm"));
        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_toystorm_reward_eval"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        ToyStormScript script = new ToyStormScript();
        ToyStormRewardEvaluator evaluator = new ToyStormRewardEvaluator();

        EncounterInstance i0 = encounters.create(def, ctx);
        script.onStart(i0, UUID.randomUUID());
        script.onEnd(i0);
        assertEquals(new ToyStormRewardDecision(false), evaluator.evaluate(script.endSummaryFor(i0.instanceId())));

        EncounterInstance i1 = encounters.create(def, ctx);
        script.onStart(i1, UUID.randomUUID());
        UUID p1 = UUID.randomUUID();
        encounters.join(p1, i1, ParticipationRole.PARTICIPANT);
        script.onJoin(i1, p1, ParticipationRole.PARTICIPANT);
        script.onEnd(i1);
        assertEquals(new ToyStormRewardDecision(true), evaluator.evaluate(script.endSummaryFor(i1.instanceId())));
    }
}

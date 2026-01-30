package io.github.legendaryforge.legendary.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.content.ToyLightningScript.RewardTier;
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

public final class ToyLightningScriptTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void computesRewardTierDeterministically() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyLightningEncounterDefinition def =
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"), ResourceId.of("legendarycontent", "arena_script_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        ToyLightningScript script = new ToyLightningScript();
        UUID trigger = UUID.randomUUID();
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

        // charge: p1(+2) + p2(+2) + s1(+0) = 4
        assertEquals(4, script.chargeFor(instance.instanceId()));

        script.onEnd(instance);

        // participants at end = 2, score = 2 + 4 = 6 => MINOR per thresholds.
        assertEquals(RewardTier.MINOR, script.rewardTierFor(instance.instanceId()));

        // idempotent end
        script.onEnd(instance);
        assertEquals(RewardTier.MINOR, script.rewardTierFor(instance.instanceId()));
    }
}

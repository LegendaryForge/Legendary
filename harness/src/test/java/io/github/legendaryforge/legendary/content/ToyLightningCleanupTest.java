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

public final class ToyLightningCleanupTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void cleanupEvictsPerInstanceState() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyLightningEncounterDefinition def =
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_lightning_cleanup_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);
        UUID id = instance.instanceId();

        ToyLightningScript script = new ToyLightningScript();
        script.onStart(instance, UUID.randomUUID());

        UUID p1 = UUID.randomUUID();
        encounters.join(p1, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p1, ParticipationRole.PARTICIPANT);

        script.onEnd(instance);

        // Prove we have non-default state.
        assertEquals(1, script.startsFor(id));
        assertEquals(2, script.chargeFor(id));
        assertEquals(ToyLightningScript.RewardTier.MINOR, script.rewardTierFor(id));

        // Evict.
        script.cleanup(id);

        // State should be gone (default query behavior).
        assertEquals(0, script.startsFor(id));
        assertEquals(0, script.chargeFor(id));
        assertEquals(ToyLightningScript.RewardTier.NONE, script.rewardTierFor(id));
    }
}

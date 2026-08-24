package io.github.legendaryforge.legendary.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.content.ToyLightningScript.RewardTier;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ContentScenarioTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void toyLightningEncounter_flow() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();

        ToyLightningScript script = new ToyLightningScript();
        EncounterManager base = runtime.encounters();

        // Event bridge wires start/end lifecycle events into the script.
        new ScriptEventBridge(runtime.events(), base, script);

        // Decorator wires join/end calls into the script as a content-side integration point.
        EncounterManager encounters = new ScriptedEncounterManager(base, script);

        ToyLightningEncounterDefinition def =
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"), ResourceId.of("legendarycontent", "arena_alpha"));

        EncounterContext ctx = new SimpleContext(anchor, Map.of("note", "content_scenario"));
        EncounterInstance instance = encounters.create(def, ctx);

        UUID p1 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, encounters.join(p1, instance, ParticipationRole.PARTICIPANT));

        // Start should have been emitted exactly once and bridged into the script.
        assertEquals(1, script.startsFor(instance.instanceId()));

        assertEquals(JoinResult.SUCCESS, encounters.join(s1, instance, ParticipationRole.SPECTATOR));

        // Script hook ran on successful joins: p(+2) + s(+0) = 2.
        assertEquals(2, script.chargeFor(instance.instanceId()));

        encounters.end(instance, EndReason.COMPLETED);
        assertEquals(RewardTier.MINOR, script.rewardTierFor(instance.instanceId()));

        // Core denies post-end join.
        assertEquals(
                JoinResult.DENIED_STATE, encounters.join(UUID.randomUUID(), instance, ParticipationRole.SPECTATOR));
    }
}

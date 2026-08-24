package io.github.legendaryforge.legendary.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ScriptCleanupBridgeIntegrationTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void cleanupEvent_isForwardedToScript() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyLightningEncounterDefinition def =
                new ToyLightningEncounterDefinition(ResourceId.of("legendarycontent", "toy_lightning"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_cleanup_bridge_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);

        ToyLightningScript script = new ToyLightningScript();
        new ScriptEventBridge(runtime.events(), encounters, script);
        new ScriptCleanupBridge(runtime.events(), script);

        // Ensure script has some per-instance state.
        UUID trigger = UUID.randomUUID();
        script.onStart(instance, trigger);
        assertEquals(1, script.startsFor(instance.instanceId()));

        // End -> core emits EncounterCleanupEvent exactly-once -> bridge calls script cleanup.
        encounters.end(instance, EndReason.COMPLETED);

        // After cleanup, querying should be reset.
        assertEquals(0, script.startsFor(instance.instanceId()));
        assertEquals(0, script.chargeFor(instance.instanceId()));
    }
}

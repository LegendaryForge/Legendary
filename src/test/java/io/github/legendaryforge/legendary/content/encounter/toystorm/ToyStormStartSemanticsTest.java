package io.github.legendaryforge.legendary.content.encounter.toystorm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ToyStormStartSemanticsTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void startSetsChargeIdempotently() {
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

        // idempotent start call
        script.onStart(instance, trigger);
        assertEquals(EncounterPhase.CHARGE, script.phaseFor(instance.instanceId()));
    }
}

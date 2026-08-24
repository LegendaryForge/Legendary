package io.github.legendaryforge.legendary.content.encounter.toystorm;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

public final class ToyStormCleanupTest {

    private record SimpleContext(EncounterAnchor anchor, Map<String, Object> metadata) implements EncounterContext {}

    @Test
    void cleanupEvictsPerInstanceState() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager encounters = runtime.encounters();

        ToyStormEncounterDefinition def =
                new ToyStormEncounterDefinition(ResourceId.of("legendarycontent", "toy_storm"));

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarycontent", "world"),
                ResourceId.of("legendarycontent", "arena_storm_cleanup_test"));
        EncounterContext ctx = new SimpleContext(anchor, Map.of());

        EncounterInstance instance = encounters.create(def, ctx);
        UUID id = instance.instanceId();

        ToyStormScript script = new ToyStormScript();
        script.onStart(instance, UUID.randomUUID());

        // After start we should have a phase.
        assertNotNull(script.phaseFor(id));

        // Joining participants advances phases deterministically.
        UUID p1 = UUID.randomUUID();
        encounters.join(p1, instance, ParticipationRole.PARTICIPANT);
        script.onJoin(instance, p1, ParticipationRole.PARTICIPANT);

        assertNotNull(script.phaseFor(id));

        // Evict.
        script.cleanup(id);

        // State should be gone.
        assertNull(script.phaseFor(id));
    }
}

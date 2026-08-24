package io.github.legendaryforge.legendary.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.dogfood.support.EndRosterCapture;
import io.github.legendaryforge.legendary.dogfood.support.TestContext;
import io.github.legendaryforge.legendary.dogfood.support.TestDefinition;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class EndRosterCaptureTest {

    @Test
    void endEvent_seesFinalParticipantRoster() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        EndRosterCapture capture = new EndRosterCapture(runtime.events(), manager);

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarydogfood", "world"),
                ResourceId.of("legendarydogfood", "anchor-roster")
        );
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy-roster"),
                "Toy Roster Encounter",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                3,
                3
        );

        EncounterInstance instance = manager.create(def, context);

        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();

        assertEquals(JoinResult.SUCCESS, manager.join(p1, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, manager.join(p2, instance, ParticipationRole.PARTICIPANT));
        assertEquals(JoinResult.SUCCESS, manager.join(s1, instance, ParticipationRole.SPECTATOR));

        // p2 leaves before end; should not be present at end.
        manager.leave(p2, instance);

        manager.end(instance, EndReason.COMPLETED);

        Set<UUID> atEnd = capture.participantsAtEnd(instance.instanceId());
        assertTrue(atEnd.contains(p1));
        assertFalse(atEnd.contains(p2));
        assertFalse(atEnd.contains(s1));

        assertEquals(1, atEnd.size());
    }
}

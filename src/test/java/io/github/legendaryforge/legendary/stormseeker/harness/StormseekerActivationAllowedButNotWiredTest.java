package io.github.legendaryforge.legendary.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.*;
import io.github.legendaryforge.legendary.core.api.encounter.*;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.stormseeker.StormseekerWiring;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class StormseekerActivationAllowedButNotWiredTest {

    @Test
    void activationGateAllowsButActivationRemainsInert() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        ActivationService activations = runtime.services().require(ActivationService.class);

        StormseekerWiring.registerGates(gates);

        EncounterDefinition def = new ActivationSessionServiceTest.TestDefinition();
        EncounterContext ctx = new ActivationSessionServiceTest.TestContext();

        ActivationAttemptResult result =
                activations.attemptActivation(
                        new ActivationService.ActivationAttemptRequest(
                                UUID.randomUUID(),
                                def,
                                ctx,
                                Optional.of(StormseekerWiring.GATE_ACTIVATION),
                                Map.of("requiredQuestStep", "A1", "questStep", "A1"),
                                Optional.empty()));

        assertEquals(ActivationAttemptStatus.FAILED, result.status());
        assertFalse(result.decision().allowed());
        assertEquals(ResourceId.of("legendarycore", "not_wired"), result.decision().reasonCode());
        assertTrue(result.sessionId().isEmpty());
        assertTrue(result.instance().isEmpty());
    }
}

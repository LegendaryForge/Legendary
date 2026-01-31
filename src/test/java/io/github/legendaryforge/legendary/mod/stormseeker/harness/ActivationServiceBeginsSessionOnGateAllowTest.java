package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.*;
import io.github.legendaryforge.legendary.core.api.encounter.*;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ActivationServiceBeginsSessionOnGateAllowTest {

    @Test
    void gateAllows_activationBeginsSession() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        ActivationService activations = runtime.services().require(ActivationService.class);

        ResourceId gateKey = ResourceId.of("stormseeker", "allow_gate");
        gates.register(gateKey, req -> io.github.legendaryforge.legendary.core.api.gate.GateDecision.allow());

        EncounterDefinition def = new ActivationSessionServiceTest.TestDefinition();
        EncounterContext ctx = new ActivationSessionServiceTest.TestContext();

        ActivationAttemptResult result = activations.attemptActivation(new ActivationService.ActivationAttemptRequest(
                UUID.randomUUID(), def, ctx, Optional.of(gateKey), Optional.empty()));

        assertEquals(ActivationAttemptStatus.SUCCESS, result.status());
        assertTrue(result.decision().allowed());
        assertTrue(result.sessionId().isPresent());
        assertTrue(result.instance().isEmpty());
    }
}

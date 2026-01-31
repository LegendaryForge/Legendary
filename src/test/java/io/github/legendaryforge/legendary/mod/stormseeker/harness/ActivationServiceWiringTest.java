package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.*;
import io.github.legendaryforge.legendary.core.api.activation.ActivationAuthority;
import io.github.legendaryforge.legendary.core.api.encounter.*;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ActivationServiceWiringTest {

    @Test
    void activationServiceIsWiredAndCallable() {
        CoreRuntime runtime = new DefaultCoreRuntime();

        assertNotNull(runtime.services().require(ActivationService.class));

        GateService gates = runtime.services().require(GateService.class);
        ActivationService activations = runtime.services().require(ActivationService.class);

        StormseekerWiring.registerGates(gates);

        EncounterDefinition def = new ActivationSessionServiceTest.TestDefinition();
        EncounterContext ctx = new ActivationSessionServiceTest.TestContext();

        ActivationAttemptResult result = activations.attemptActivation(new ActivationService.ActivationAttemptRequest(
                UUID.randomUUID(),
                def,
                ctx,
                Optional.of(StormseekerWiring.GATE_ACTIVATION),
                Optional.<ActivationAuthority>empty(),
                Optional.<String>empty()));

        assertNotNull(result);
        assertNotNull(result.decision());
    }
}

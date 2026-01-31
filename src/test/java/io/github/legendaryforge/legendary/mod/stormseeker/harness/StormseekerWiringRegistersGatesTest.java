package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.gate.*;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class StormseekerWiringRegistersGatesTest {

    @Test
    void stormseekerRegistersActivationGate() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        // Act: register Stormseeker gates.
        StormseekerWiring.registerGates(gates);

        // Assert: the gate is invoked and denies with the expected reason.
        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                StormseekerWiring.GATE_ACTIVATION,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()));

        assertNotNull(decision);
        assertFalse(decision.allowed());
        assertEquals(StormseekerWiring.DENY_NOT_ON_REQUIRED_QUEST_STEP, decision.reasonCode());
    }
}

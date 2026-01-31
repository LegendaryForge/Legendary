package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.gate.*;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public final class GateRegistrationWiringTest {

    @Test
    void registeredGateIsInvokedAndDecisionIsReturned() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        ResourceId gateKey = ResourceId.of("stormseeker", "test_gate");
        AtomicReference<ConditionGate.GateRequest> seen = new AtomicReference<>();

        gates.register(gateKey, request -> {
            seen.set(request);
            return GateDecision.deny(ResourceId.of("stormseeker", "denied_by_test"), Map.of("k", "v"));
        });

        UUID playerId = UUID.randomUUID();
        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                gateKey, playerId, Optional.empty(), Optional.empty(), Optional.empty(), Map.of("a", "b")));

        assertNotNull(decision);
        assertFalse(decision.allowed());
        assertEquals(ResourceId.of("stormseeker", "denied_by_test"), decision.reasonCode());
        assertEquals(Map.of("k", "v"), decision.attributes());

        ConditionGate.GateRequest req = seen.get();
        assertNotNull(req, "gate should have been invoked");
        assertEquals(gateKey, req.gateKey());
        assertEquals(playerId, req.playerId());
        assertEquals(Map.of("a", "b"), req.attributes());
    }
}

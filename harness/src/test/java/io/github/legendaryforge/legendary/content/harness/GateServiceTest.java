package io.github.legendaryforge.legendary.content.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.gate.ConditionGate;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class GateServiceTest {

    @Test
    void evaluateDeniesWhenGateNotRegistered() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                ResourceId.of("legendarycontent", "missing_gate"),
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()));

        assertFalse(decision.allowed());
        assertEquals(ResourceId.of("legendarycore", "gate_not_registered"), decision.reasonCode());
    }

    @Test
    void evaluateUsesRegisteredGate() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        ResourceId key = ResourceId.of("legendarycontent", "always_allow");
        gates.register(key, request -> GateDecision.allow());

        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                key, UUID.randomUUID(), Optional.empty(), Optional.empty(), Optional.empty(), Map.of("k", "v")));

        assertTrue(decision.allowed());
        assertEquals(ResourceId.of("legendarycore", "allowed"), decision.reasonCode());
    }
}

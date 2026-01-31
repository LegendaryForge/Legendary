package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.legendaryforge.legendary.core.api.gate.ConditionGate;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.gate.DefaultGateService;
import io.github.legendaryforge.legendary.mod.LegendaryWiring;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LegendaryWiringRegistersStormseekerGatesTest {

    @Test
    void registerAllGates_registersStormseekerActivationGate() {
        GateService gates = new DefaultGateService();
        LegendaryWiring.registerAllGates(gates);

        // Evaluate the gate; if it wasn’t registered, DefaultGateService returns a well-known denial reason.
        ConditionGate.GateRequest req = new ConditionGate.GateRequest(
                StormseekerWiring.GATE_ACTIVATION,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of());

        GateDecision decision = gates.evaluate(req);

        // Strong signal we hit the Stormseeker gate implementation (not the unregistered-gate fallback).
        assertNotEquals(ResourceId.of("legendarycore", "gate_not_registered"), decision.reasonCode());
        assertEquals("stormseeker", decision.reasonCode().namespace());
    }
}

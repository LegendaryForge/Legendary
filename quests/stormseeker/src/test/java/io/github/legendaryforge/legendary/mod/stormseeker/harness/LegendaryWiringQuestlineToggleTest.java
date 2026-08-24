package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.legendaryforge.legendary.core.api.gate.ConditionGate;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.gate.DefaultGateService;
import io.github.legendaryforge.legendary.mod.LegendaryConfig;
import io.github.legendaryforge.legendary.mod.LegendaryWiring;
import io.github.legendaryforge.legendary.mod.questline.StormseekerQuestline;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LegendaryWiringQuestlineToggleTest {

    @Test
    void whenStormseekerDisabled_gateIsNotRegistered() {
        GateService gates = new DefaultGateService();

        LegendaryConfig config = LegendaryConfig.of(Map.of(StormseekerQuestline.ID, false));
        LegendaryWiring.registerAllGates(gates, config);

        ConditionGate.GateRequest req = new ConditionGate.GateRequest(
                StormseekerWiring.GATE_ACTIVATION,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of());

        GateDecision decision = gates.evaluate(req);
        assertEquals(ResourceId.of("legendarycore", "gate_not_registered"), decision.reasonCode());
    }
}

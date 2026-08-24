package io.github.legendaryforge.legendary.quests.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.legendaryforge.legendary.core.api.gate.ConditionGate;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.LegendaryConfig;
import io.github.legendaryforge.legendary.core.api.questline.LegendaryWiring;
import io.github.legendaryforge.legendary.core.api.questline.QuestlineRegistry;
import io.github.legendaryforge.legendary.core.internal.gate.DefaultGateService;
import io.github.legendaryforge.legendary.quests.stormseeker.StormseekerWiring;
import io.github.legendaryforge.legendary.quests.stormseeker.questline.StormseekerQuestline;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LegendaryWiringRegistersStormseekerGatesTest {

    @Test
    void registerAllGates_registersStormseekerActivationGate() {
        GateService gates = new DefaultGateService();
        QuestlineRegistry questlines = new QuestlineRegistry().register(new StormseekerQuestline());
        LegendaryWiring.registerAllGates(questlines, gates, LegendaryConfig.enablingAll(questlines));

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

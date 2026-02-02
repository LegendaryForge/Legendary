package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.gate.ConditionGate;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestAttributes;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestSteps;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class GateServiceTest {

    @Test
    void deniesWhenGateNotRegistered() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                ResourceId.of("stormseeker", "missing_gate"),
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()));

        assertFalse(decision.allowed());
        assertEquals(ResourceId.of("legendarycore", "gate_not_registered"), decision.reasonCode());
    }

    @Test
    void allowsWhenGateReturnsAllow() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        ResourceId gateKey = ResourceId.of("stormseeker", "quest_step_gate");
        gates.register(gateKey, request -> GateDecision.allow());

        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                gateKey,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of(StormseekerQuestAttributes.QUEST_STEP, StormseekerQuestSteps.PHASE_3_INCOMPLETE_FORM)));

        assertTrue(decision.allowed());
        assertEquals(ResourceId.of("legendarycore", "allowed"), decision.reasonCode());
    }
}

package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.gate.*;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class StormseekerQuestStepGateTest {

    @Test
    void deniesWhenQuestStepMissingOrWrong() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        StormseekerWiring.registerGates(gates);

        GateDecision missing = gates.evaluate(new ConditionGate.GateRequest(
                StormseekerWiring.GATE_ACTIVATION,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("requiredQuestStep", "A1")));

        assertFalse(missing.allowed());
        assertEquals(StormseekerWiring.DENY_NOT_ON_REQUIRED_QUEST_STEP, missing.reasonCode());

        GateDecision wrong = gates.evaluate(new ConditionGate.GateRequest(
                StormseekerWiring.GATE_ACTIVATION,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("requiredQuestStep", "A1", "questStep", "A0")));

        assertFalse(wrong.allowed());
        assertEquals(StormseekerWiring.DENY_NOT_ON_REQUIRED_QUEST_STEP, wrong.reasonCode());
    }

    @Test
    void allowsWhenQuestStepIsA1() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        StormseekerWiring.registerGates(gates);

        GateDecision ok = gates.evaluate(new ConditionGate.GateRequest(
                StormseekerWiring.GATE_ACTIVATION,
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("requiredQuestStep", "A1", "questStep", "A1")));

        assertTrue(ok.allowed());
        assertEquals(ResourceId.of("legendarycore", "allowed"), ok.reasonCode());
    }
}

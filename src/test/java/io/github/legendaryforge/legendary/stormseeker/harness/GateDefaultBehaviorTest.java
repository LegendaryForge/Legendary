package io.github.legendaryforge.legendary.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.gate.*;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class GateDefaultBehaviorTest {

    @Test
    void unregisteredGateDeniesByDefault() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        GateDecision decision = gates.evaluate(new ConditionGate.GateRequest(
                ResourceId.of("stormseeker", "unregistered_gate"),
                UUID.randomUUID(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of()));

        assertNotNull(decision, "decision must not be null");
        assertFalse(decision.allowed(), "unregistered gates must deny by default");
        assertNotNull(decision.reasonCode(), "deny decision must include a reason code");
    }
}

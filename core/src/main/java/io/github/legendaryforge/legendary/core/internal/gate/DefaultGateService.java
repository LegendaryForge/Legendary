package io.github.legendaryforge.legendary.core.internal.gate;

import io.github.legendaryforge.legendary.core.api.gate.ConditionGate;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultGateService implements GateService {

    private final Map<ResourceId, ConditionGate> gates = new ConcurrentHashMap<>();

    @Override
    public void register(ResourceId gateKey, ConditionGate gate) {
        gates.put(gateKey, gate);
    }

    @Override
    public GateDecision evaluate(ConditionGate.GateRequest request) {
        ConditionGate gate = gates.get(request.gateKey());
        if (gate == null) {
            return GateDecision.deny(ResourceId.of("legendarycore", "gate_not_registered"));
        }
        return gate.evaluate(request);
    }
}

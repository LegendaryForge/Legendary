package io.github.legendaryforge.legendary.core.api.gate;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;

public interface GateService {

    void register(ResourceId gateKey, ConditionGate gate);

    GateDecision evaluate(ConditionGate.GateRequest request);
}

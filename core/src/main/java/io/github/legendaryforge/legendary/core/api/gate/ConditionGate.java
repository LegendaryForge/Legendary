package io.github.legendaryforge.legendary.core.api.gate;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@FunctionalInterface
public interface ConditionGate {

    GateDecision evaluate(GateRequest request);

    record GateRequest(
            ResourceId gateKey,
            UUID playerId,
            Optional<EncounterKey> encounterKey,
            Optional<ResourceId> interactionKind,
            Optional<LocationRef> location,
            Map<String, String> attributes) {}

    record LocationRef(String worldId, int x, int y, int z) {}
}

package io.github.legendaryforge.legendary.core.api.gate;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;

public record GateDecision(boolean allowed, ResourceId reasonCode, Map<String, String> attributes) {

    public static GateDecision allow() {
        return new GateDecision(true, ResourceId.of("legendarycore", "allowed"), Map.of());
    }

    public static GateDecision deny(ResourceId reasonCode) {
        return new GateDecision(false, reasonCode, Map.of());
    }

    public static GateDecision deny(ResourceId reasonCode, Map<String, String> attributes) {
        return new GateDecision(false, reasonCode, Map.copyOf(attributes));
    }
}

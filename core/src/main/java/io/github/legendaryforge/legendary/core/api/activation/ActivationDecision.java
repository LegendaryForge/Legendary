package io.github.legendaryforge.legendary.core.api.activation;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;

public record ActivationDecision(boolean allowed, ResourceId reasonCode, Map<String, String> attributes) {

    public static ActivationDecision allow() {
        return new ActivationDecision(true, ResourceId.of("legendarycore", "allowed"), Map.of());
    }

    public static ActivationDecision deny(ResourceId reasonCode) {
        return new ActivationDecision(false, reasonCode, Map.of());
    }

    public static ActivationDecision deny(ResourceId reasonCode, Map<String, String> attributes) {
        return new ActivationDecision(false, reasonCode, Map.copyOf(attributes));
    }
}

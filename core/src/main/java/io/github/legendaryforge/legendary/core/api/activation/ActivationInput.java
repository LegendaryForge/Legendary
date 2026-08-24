package io.github.legendaryforge.legendary.core.api.activation;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side resolved activation input passed to gates.
 *
 * <p>Attributes must be derived from authoritative state (content/mod owned), not client claims.</p>
 */
public final class ActivationInput {

    private final UUID activatorPlayerId;
    private final ResourceId activationGateKey;
    private final Map<String, String> attributes;
    private final Optional<String> targetRef;

    public ActivationInput(
            UUID activatorPlayerId,
            ResourceId activationGateKey,
            Map<String, String> attributes,
            Optional<String> targetRef) {
        this.activatorPlayerId = Objects.requireNonNull(activatorPlayerId, "activatorPlayerId");
        this.activationGateKey = Objects.requireNonNull(activationGateKey, "activationGateKey");
        this.attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        this.targetRef = Objects.requireNonNull(targetRef, "targetRef");
    }

    public UUID activatorPlayerId() {
        return activatorPlayerId;
    }

    public ResourceId activationGateKey() {
        return activationGateKey;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public Optional<String> targetRef() {
        return targetRef;
    }
}

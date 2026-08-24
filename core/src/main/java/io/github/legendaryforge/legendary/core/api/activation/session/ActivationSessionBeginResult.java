package io.github.legendaryforge.legendary.core.api.activation.session;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.UUID;

public record ActivationSessionBeginResult(
        ActivationSessionBeginStatus status, ResourceId reasonCode, UUID sessionId) {}

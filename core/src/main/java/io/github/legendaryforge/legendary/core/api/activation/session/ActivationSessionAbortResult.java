package io.github.legendaryforge.legendary.core.api.activation.session;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;

public record ActivationSessionAbortResult(ActivationSessionAbortStatus status, ResourceId reasonCode) {}

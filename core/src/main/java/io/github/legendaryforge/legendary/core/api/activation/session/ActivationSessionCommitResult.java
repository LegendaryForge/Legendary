package io.github.legendaryforge.legendary.core.api.activation.session;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Optional;

public record ActivationSessionCommitResult(
        ActivationSessionCommitStatus status, ResourceId reasonCode, Optional<EncounterInstance> instance) {}

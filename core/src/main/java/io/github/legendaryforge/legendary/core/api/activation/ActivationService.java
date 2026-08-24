package io.github.legendaryforge.legendary.core.api.activation;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Optional;
import java.util.UUID;

public interface ActivationService {

    ActivationAttemptResult attemptActivation(ActivationAttemptRequest request);

    record ActivationAttemptRequest(
            UUID activatorId,
            EncounterDefinition definition,
            EncounterContext context,
            Optional<ResourceId> activationGateKey,
            Optional<ActivationAuthority> authorityOverride,
            Optional<String> targetRef) {}
}

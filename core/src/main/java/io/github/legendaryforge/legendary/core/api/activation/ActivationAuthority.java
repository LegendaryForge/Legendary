package io.github.legendaryforge.legendary.core.api.activation;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import java.util.UUID;

@FunctionalInterface
public interface ActivationAuthority {

    ActivationDecision evaluate(ActivationAuthorityRequest request);

    record ActivationAuthorityRequest(UUID activatorId, EncounterDefinition definition, EncounterContext context) {}
}

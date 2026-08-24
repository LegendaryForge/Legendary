package io.github.legendaryforge.legendary.core.api.activation.session;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ActivationSessionService {

    ActivationSessionBeginResult begin(ActivationSessionBeginRequest request);

    ActivationSessionCommitResult commit(UUID sessionId);

    ActivationSessionAbortResult abort(UUID sessionId, ResourceId reasonCode);

    Optional<ActivationSessionView> get(UUID sessionId);

    record ActivationSessionBeginRequest(
            UUID activatorId,
            EncounterKey encounterKey,
            EncounterDefinition definition,
            EncounterContext context,
            Optional<ResourceId> activationGateKey,
            Map<String, String> attributes) {}
}

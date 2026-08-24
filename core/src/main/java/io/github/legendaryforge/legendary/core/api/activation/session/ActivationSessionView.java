package io.github.legendaryforge.legendary.core.api.activation.session;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import java.util.Map;
import java.util.UUID;

public record ActivationSessionView(
        UUID sessionId,
        UUID activatorId,
        EncounterKey encounterKey,
        ActivationSessionState state,
        Map<String, String> attributes) {}

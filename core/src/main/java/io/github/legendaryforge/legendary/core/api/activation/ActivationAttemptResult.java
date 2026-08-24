package io.github.legendaryforge.legendary.core.api.activation;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import java.util.Optional;
import java.util.UUID;

public record ActivationAttemptResult(
        ActivationAttemptStatus status,
        ActivationDecision decision,
        Optional<UUID> sessionId,
        Optional<EncounterInstance> instance) {

    public ActivationAttemptResult {
        if (status == null) throw new NullPointerException("status");
        if (decision == null) throw new NullPointerException("decision");
        if (sessionId == null) throw new NullPointerException("sessionId");
        if (instance == null) throw new NullPointerException("instance");

        // SUCCESS => allowed==true and sessionId present (session began)
        if (status == ActivationAttemptStatus.SUCCESS) {
            if (!decision.allowed()) {
                throw new IllegalArgumentException("SUCCESS requires decision.allowed == true");
            }
            if (sessionId.isEmpty()) {
                throw new IllegalArgumentException("SUCCESS requires sessionId");
            }
        }

        // FAILED => sessionId absent and allowed==false (denied or fatal mapped to denial)
        if (status == ActivationAttemptStatus.FAILED) {
            if (sessionId.isPresent()) {
                throw new IllegalArgumentException("FAILED must not have sessionId");
            }
            if (decision.allowed()) {
                throw new IllegalArgumentException("FAILED requires decision.allowed == false");
            }
        }
    }
}

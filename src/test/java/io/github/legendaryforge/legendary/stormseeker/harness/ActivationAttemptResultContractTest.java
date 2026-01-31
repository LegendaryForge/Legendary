package io.github.legendaryforge.legendary.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.ActivationAttemptResult;
import io.github.legendaryforge.legendary.core.api.activation.ActivationService;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ActivationAttemptResultContractTest {

    @Test
    void activationAttemptResultAlwaysHasStatusAndDecision() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        ActivationService activations = runtime.services().require(ActivationService.class);

        EncounterDefinition def = new ActivationSessionServiceTest.TestDefinition();
        EncounterContext ctx = new ActivationSessionServiceTest.TestContext();

        ActivationAttemptResult result =
                assertDoesNotThrow(() -> activations.attemptActivation(new ActivationService.ActivationAttemptRequest(
                        UUID.randomUUID(),
                        def,
                        ctx,
                        Optional.of(ResourceId.of("stormseeker", "deny_gate")),
                        java.util.Map.of(),
                        Optional.empty())));

        assertNotNull(result, "result must not be null");
        assertNotNull(result.status(), "status() must not be null");
        assertNotNull(result.decision(), "decision() must not be null");
    }
}

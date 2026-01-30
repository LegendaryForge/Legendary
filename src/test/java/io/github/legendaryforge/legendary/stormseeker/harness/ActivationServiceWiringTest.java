package io.github.legendaryforge.legendary.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.*;
import io.github.legendaryforge.legendary.core.api.encounter.*;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ActivationServiceWiringTest {

    @Test
    void activationServiceIsPresentButNotWiredYet() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        ActivationService activations = runtime.services().require(ActivationService.class);

        EncounterDefinition def = new ActivationSessionServiceTest.TestDefinition();
        EncounterContext ctx = new ActivationSessionServiceTest.TestContext();

        ActivationAttemptResult result = activations.attemptActivation(new ActivationService.ActivationAttemptRequest(
                UUID.randomUUID(), def, ctx, Optional.of(ResourceId.of("stormseeker", "deny_gate")), Optional.empty()));

        assertEquals(ActivationAttemptStatus.FAILED, result.status());
        assertFalse(result.decision().allowed());
        assertTrue(result.sessionId().isEmpty());
        assertTrue(result.instance().isEmpty());
        assertEquals(
                ResourceId.of("legendarycore", "not_wired"), result.decision().reasonCode());
    }
}

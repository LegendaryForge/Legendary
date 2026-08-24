package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.ActivationAttemptResult;
import io.github.legendaryforge.legendary.core.api.activation.ActivationAttemptStatus;
import io.github.legendaryforge.legendary.core.api.activation.ActivationService;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestSteps;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class StormseekerActivationAllowsWhenQuestStepMatchesTest {

    @Test
    void allowBeginsSessionWhenQuestStepMatches() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);

        StormseekerWiring.registerGates(gates);

        ActivationService activations = StormseekerTestActivations.withQuestStep(
                runtime, StormseekerQuestSteps.PHASE_3_INCOMPLETE_FORM, StormseekerQuestSteps.PHASE_3_INCOMPLETE_FORM);

        EncounterDefinition def = new ActivationSessionServiceTest.TestDefinition();
        EncounterContext ctx = new ActivationSessionServiceTest.TestContext();

        ActivationAttemptResult result = activations.attemptActivation(new ActivationService.ActivationAttemptRequest(
                UUID.randomUUID(),
                def,
                ctx,
                Optional.of(StormseekerWiring.GATE_ACTIVATION),
                Optional.empty(),
                Optional.empty()));

        assertEquals(ActivationAttemptStatus.SUCCESS, result.status());
        assertTrue(result.decision().allowed());
        assertTrue(result.sessionId().isPresent());
    }
}

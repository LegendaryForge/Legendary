package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import io.github.legendaryforge.legendary.core.api.activation.ActivationInput;
import io.github.legendaryforge.legendary.core.api.activation.ActivationInputResolver;
import io.github.legendaryforge.legendary.core.api.activation.ActivationService;
import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionService;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.activation.DefaultActivationService;
import java.util.Map;

final class StormseekerTestActivations {

    private StormseekerTestActivations() {}

    static ActivationService withQuestStep(CoreRuntime runtime, String questStep) {
        GateService gates = runtime.services().require(GateService.class);
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        ActivationInputResolver resolver = request -> new ActivationInput(
                request.activatorId(),
                request.activationGateKey().orElseThrow(),
                Map.of("legendary.quest.step", questStep),
                request.targetRef());

        return new DefaultActivationService(gates, sessions, resolver);
    }
}

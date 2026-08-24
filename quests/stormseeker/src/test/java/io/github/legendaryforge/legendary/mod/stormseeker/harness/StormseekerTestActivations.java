package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import io.github.legendaryforge.legendary.core.api.activation.ActivationInput;
import io.github.legendaryforge.legendary.core.api.activation.ActivationInputResolver;
import io.github.legendaryforge.legendary.core.api.activation.ActivationService;
import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionService;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.activation.DefaultActivationService;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestAttributes;
import java.util.Map;

final class StormseekerTestActivations {

    private StormseekerTestActivations() {}

    /**
     * Backward-compatible convenience: assumes requiredQuestStep == questStep.
     */
    static ActivationService withQuestStep(CoreRuntime runtime, String questStep) {
        return withQuestStep(runtime, questStep, questStep);
    }

    /**
     * Explicit helper for Phase 3 input resolution tests.
     *
     * <p>requiredQuestStep represents the gate's required step (e.g., bound to a landmark/target),
     * while questStep represents the activator's current quest state.</p>
     */
    static ActivationService withQuestStep(CoreRuntime runtime, String requiredQuestStep, String questStep) {
        GateService gates = runtime.services().require(GateService.class);
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        ActivationInputResolver resolver = request -> new ActivationInput(
                request.activatorId(),
                request.activationGateKey().orElseThrow(),
                Map.of(
                        StormseekerQuestAttributes.QUEST_STEP, questStep,
                        StormseekerQuestAttributes.LEGACY_QUEST_STEP, questStep,
                        StormseekerQuestAttributes.REQUIRED_QUEST_STEP, requiredQuestStep),
                request.targetRef());

        return new DefaultActivationService(gates, sessions, resolver);
    }
}

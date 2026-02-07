package io.github.legendaryforge.legendary.mod.stormseeker;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.runtime.LegendarySystemRegistrar;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestAttributes;

public final class StormseekerWiring {

    public static final ResourceId GATE_ACTIVATION = ResourceId.of("stormseeker", "activation");

    public static final ResourceId DENY_NOT_ON_REQUIRED_QUEST_STEP =
            ResourceId.of("stormseeker", "not_on_required_quest_step");

    private StormseekerWiring() {}

    public static void registerGates(GateService gates) {
        // Denies unless the activator is on the required quest step.
        //
        // Canonical attribute key (Phase 3+): "legendary.quest.step"
        // Back-compat: also accept legacy "questStep" until all harness tests are migrated.
        gates.register(GATE_ACTIVATION, request -> {
            String required = request.attributes().get(StormseekerQuestAttributes.REQUIRED_QUEST_STEP);
            String step = request.attributes().get(StormseekerQuestAttributes.QUEST_STEP);
            if (step == null) {
                step = request.attributes().get(StormseekerQuestAttributes.LEGACY_QUEST_STEP);
            }

            if (required != null && required.equals(step)) {
                return io.github.legendaryforge.legendary.core.api.gate.GateDecision.allow();
            }

            return io.github.legendaryforge.legendary.core.api.gate.GateDecision.deny(
                    DENY_NOT_ON_REQUIRED_QUEST_STEP, request.attributes());
        });
    }

    /**
     * Register Stormseeker runtime systems.
     *
     * <p>Phase C scope: Flowing Sigil Trial only. No Forge logic. No Anchored Sigil mechanics.
     *
     * <p>Phase C planning seam: Flowing Sigil Trial is driven per-player via FlowingTrialSession.
     * Do not register placeholder systems until a real engine tick/scheduler integration exists.
     * See: io.github.legendaryforge.legendary.mod.stormseeker.integration.StormseekerEngineIntegrationNotes
     */
    public static void registerSystems(LegendarySystemRegistrar registrar) {
        // Intentionally no-op in Phase C scaffold.
    }
}

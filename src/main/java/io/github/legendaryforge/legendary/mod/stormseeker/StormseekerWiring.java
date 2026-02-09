package io.github.legendaryforge.legendary.mod.stormseeker;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.runtime.LegendarySystemRegistrar;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerAnchoredTrialService;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1Loop;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestAttributes;
import java.util.Objects;

public final class StormseekerWiring {

    public static final ResourceId GATE_ACTIVATION = ResourceId.of("stormseeker", "activation");

    public static final ResourceId DENY_NOT_ON_REQUIRED_QUEST_STEP =
            ResourceId.of("stormseeker", "not_on_required_quest_step");

    private static final StormseekerPhase1Loop PHASE_1 = new StormseekerPhase1Loop();
    private static StormseekerAnchoredTrialService PHASE_2 = new StormseekerAnchoredTrialService();

    private StormseekerWiring() {}

    /** Test seam: reset singleton wiring state so JVM-shared tests stay isolated. */
    public static void resetForTesting() {
        PHASE_2 = new io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerAnchoredTrialService();
    }

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
     * <p>Phase C planning seam: Flowing Sigil Trial is driven per-player via FlowingTrialSession. Do not
     * register placeholder systems until a real engine tick/scheduler integration exists. See:
     * io.github.legendaryforge.legendary.mod.stormseeker.integration.StormseekerEngineIntegrationNotes
     */
    public static void registerSystems(LegendarySystemRegistrar registrar) {
        // Intentionally no-op in Phase C scaffold.
    }

    /**
     * Canonical engine/ECS entrypoint.
     *
     * <p>The host integration must call this exactly once per engine tick.
     *
     * <p>Keep this minimal: coordinate existing host tick seams only.
     */
    public static boolean enterAnchoredTrial(String playerId, StormseekerProgress progress) {
        return PHASE_2.enterAnchoredTrial(playerId, progress);
    }

    public static void tick(StormseekerHostRuntime host) {
        Objects.requireNonNull(host, "host");
        PHASE_1.tick(host);
        PHASE_2.tick(host);
    }
}

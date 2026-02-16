package io.github.legendaryforge.legendary.mod.stormseeker;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.runtime.LegendarySystemRegistrar;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.client.PerceptionToggleHandler;
import io.github.legendaryforge.legendary.mod.stormseeker.integration.StormseekerLifecycleBridge;
import io.github.legendaryforge.legendary.mod.stormseeker.logic.StormseekerTrekSystem;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerAnchoredTrialService;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerAttunementService;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1Loop;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestAttributes;
import java.util.Objects;

/**
 * Authority: v2.0 Canonical Document - Phase structure 1 -> 1.5 -> 2
 * * Central wiring hub for the Stormseeker questline.
 * Coordinates the hand-off between Trekking, Attunement, and Trials.
 */
public final class StormseekerWiring {

    public static final ResourceId GATE_ACTIVATION = ResourceId.of("stormseeker", "activation");

    public static final ResourceId DENY_NOT_ON_REQUIRED_QUEST_STEP =
            ResourceId.of("stormseeker", "not_on_required_quest_step");

    public static final ResourceId SIGIL_FLOWING = ResourceId.of("stormseeker", "sigil_flowing");
    public static final ResourceId SIGIL_ANCHORED = ResourceId.of("stormseeker", "sigil_anchored");

    // Phase 1: The Trek
    private static StormseekerTrekSystem PHASE_1_STORM_TREK;

    // Phase 1.5: Attunement (The 30s Ritual)
    private static StormseekerAttunementService ATTUNEMENT_SERVICE;

    // Phase 2: Dual Sigil Trials
    private static final StormseekerPhase1Loop PHASE_2_FLOWING_TRIAL = new StormseekerPhase1Loop();
    private static StormseekerAnchoredTrialService PHASE_2_ANCHORED_TRIAL = new StormseekerAnchoredTrialService();

    private StormseekerWiring() {}

    /** Test seam: reset singleton wiring state so JVM-shared tests stay isolated. */
    public static void resetForTesting() {
        PHASE_2_ANCHORED_TRIAL = new StormseekerAnchoredTrialService();
    }

    public static void registerGates(GateService gates) {
        gates.register(GATE_ACTIVATION, request -> {
            String required = request.attributes().get(StormseekerQuestAttributes.REQUIRED_QUEST_STEP);
            String step = request.attributes().get(StormseekerQuestAttributes.QUEST_STEP);
            if (step == null) {
                step = request.attributes().get(StormseekerQuestAttributes.LEGACY_QUEST_STEP);
            }

            if (required != null && required.equals(step)) {
                return GateDecision.allow();
            }

            return GateDecision.deny(DENY_NOT_ON_REQUIRED_QUEST_STEP, request.attributes());
        });
    }

    public static void registerSystems(LegendarySystemRegistrar registrar) {
        // Intentionally no-op in Phase C scaffold.
    }

    public static void registerListeners(EventBus bus) {
        bus.subscribe(EncounterStartedEvent.class, new StormseekerLifecycleBridge());

        // Initialize Phase 1.5 Attunement Service
        // Note: World implementation is provided by the host environment
        ATTUNEMENT_SERVICE = new StormseekerAttunementService(bus, null);

        bus.subscribe(PerceptionToggleHandler.AttunementCompleteEvent.class, event -> {
            // Handshake logic: Transition player from Phase 1.5 to Phase 2
            // This is triggered after the 30-second spool-down finishes.
        });

        // Initialize Phase 1 Trek System
        PHASE_1_STORM_TREK = new StormseekerTrekSystem(bus, null);
    }

    public static boolean enterAnchoredTrial(String playerId, StormseekerProgress progress) {
        return PHASE_2_ANCHORED_TRIAL.enterAnchoredTrial(playerId, progress);
    }

    /**
     * Canonical engine/ECS entrypoint.
     * The host integration must call this exactly once per engine tick.
     */
    public static void tick(StormseekerHostRuntime host) {
        Objects.requireNonNull(host, "host");

        // Phase 1: The Trek
        if (PHASE_1_STORM_TREK != null) {
            PHASE_1_STORM_TREK.tick(host);
        }

        // Phase 1.5: Attunement Ritual State Machine
        // This processes the 30-second timers for the spooling/locking/spool-down.
        if (ATTUNEMENT_SERVICE != null) {
            ATTUNEMENT_SERVICE.tick();
        }

        // Phase 2: Dual Sigil Trials
        PHASE_2_FLOWING_TRIAL.tick(host);
        PHASE_2_ANCHORED_TRIAL.tick(host);
    }
}

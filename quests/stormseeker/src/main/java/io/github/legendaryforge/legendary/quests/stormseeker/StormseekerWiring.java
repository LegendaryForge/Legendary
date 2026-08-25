package io.github.legendaryforge.legendary.quests.stormseeker;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.LegendarySystemRegistrar;
import io.github.legendaryforge.legendary.quests.stormseeker.client.PerceptionToggleHandler;
import io.github.legendaryforge.legendary.quests.stormseeker.integration.StormseekerLifecycleBridge;
import io.github.legendaryforge.legendary.quests.stormseeker.logic.StormseekerTrekSystem;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerAnchoredTrialService;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerAttunementService;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerFlowingTrialLoop;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerQuestAttributes;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.StormseekerHostRuntime;
import java.util.Objects;

/**
 * Authority: v3.0 Canonical Document - Phase structure 0 -> 1 -> 1.5 -> 2
 * * Central wiring hub for the Stormseeker questline.
 * Coordinates the hand-off between Trekking, Attunement, and Trials.
 */
public final class StormseekerWiring {

    public static final ResourceId GATE_ACTIVATION = ResourceId.of("stormseeker", "activation");

    public static final ResourceId DENY_NOT_ON_REQUIRED_QUEST_STEP =
            ResourceId.of("stormseeker", "not_on_required_quest_step");

    public static final ResourceId SIGIL_FLOWING = ResourceId.of("stormseeker", "sigil_flowing");
    public static final ResourceId SIGIL_ANCHORED = ResourceId.of("stormseeker", "sigil_anchored");

    // Phase 2: The Trek
    private static StormseekerTrekSystem PHASE_2_THE_TREK;

    // Phase 3: The Waking (the 30s rite)
    private static StormseekerAttunementService ATTUNEMENT_SERVICE;

    // Phase 4: The Trials
    private static final StormseekerFlowingTrialLoop PHASE_4_FLOWING_TRIAL = new StormseekerFlowingTrialLoop();
    private static StormseekerAnchoredTrialService PHASE_4_ANCHORED_TRIAL = new StormseekerAnchoredTrialService();

    private StormseekerWiring() {}

    /** Test seam: reset singleton wiring state so JVM-shared tests stay isolated. */
    public static void resetForTesting() {
        PHASE_4_ANCHORED_TRIAL = new StormseekerAnchoredTrialService();
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

        // Initialize the Phase 3 Waking service
        // Note: World implementation is provided by the host environment
        ATTUNEMENT_SERVICE = new StormseekerAttunementService(bus, null);

        bus.subscribe(PerceptionToggleHandler.AttunementCompleteEvent.class, event -> {
            // Handshake logic: transition the player from The Waking to The Trials
            // This is triggered after the 30-second spool-down finishes.
        });

        // Initialize the Phase 2 Trek system
        PHASE_2_THE_TREK = new StormseekerTrekSystem(bus, null);
    }

    public static boolean enterAnchoredTrial(String playerId, StormseekerProgress progress) {
        return PHASE_4_ANCHORED_TRIAL.enterAnchoredTrial(playerId, progress);
    }

    /**
     * Canonical engine/ECS entrypoint.
     * The host integration must call this exactly once per engine tick.
     */
    public static void tick(StormseekerHostRuntime host) {
        Objects.requireNonNull(host, "host");

        // Phase 2: The Trek
        if (PHASE_2_THE_TREK != null) {
            PHASE_2_THE_TREK.tick(host);
        }

        // Phase 3: The Waking rite state machine
        // This processes the 30-second timers for the spooling/locking/spool-down.
        if (ATTUNEMENT_SERVICE != null) {
            ATTUNEMENT_SERVICE.tick();
        }

        // Phase 4: The Trials
        PHASE_4_FLOWING_TRIAL.tick(host);
        PHASE_4_ANCHORED_TRIAL.tick(host);
    }
}

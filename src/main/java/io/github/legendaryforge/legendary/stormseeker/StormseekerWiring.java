package io.github.legendaryforge.legendary.stormseeker;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

/**
 * Stormseeker-side wiring entrypoint.
 *
 * <p>This module is currently a plain java-library, so Stormseeker provides a small explicit wiring
 * API that callers can invoke during runtime bootstrap.
 */
public final class StormseekerWiring {

    private StormseekerWiring() {}

    public static final ResourceId GATE_ACTIVATION = ResourceId.of("stormseeker", "activation");

    public static final ResourceId DENY_NOT_ON_REQUIRED_QUEST_STEP =
            ResourceId.of("stormseeker", "not_on_required_quest_step");

    /** Register Stormseeker gates into the provided {@link GateService}. */
    public static void registerGates(GateService gates) {
        Objects.requireNonNull(gates, "gates");

        // Quest-step gate (temporary contract):
        // - Requires request.attributes().get("questStep") == "A1"
        // - Otherwise denies with stormseeker:not_on_required_quest_step
        gates.register(GATE_ACTIVATION, request -> {
            String step = request.attributes().get("questStep");
            if (!"A1".equals(step)) {
                return io.github.legendaryforge.legendary.core.api.gate.GateDecision.deny(
                        DENY_NOT_ON_REQUIRED_QUEST_STEP);
            }
            return io.github.legendaryforge.legendary.core.api.gate.GateDecision.allow();
        });
    }
}

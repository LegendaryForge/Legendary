package io.github.legendaryforge.legendary.mod;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;

/**
 * Mod-level wiring entrypoint for Legendary.
 *
 * <p>Questlines (e.g., Stormseeker) expose their own wiring classes. This class aggregates them
 * so servers can enable/disable questlines from one place.
 */
public final class LegendaryWiring {

    private LegendaryWiring() {
        // static utility
    }

    public static void registerAllGates(GateService gates) {
        registerAllGates(gates, LegendaryConfig.defaults());
    }

    public static void registerAllGates(GateService gates, LegendaryConfig config) {
        if (config.stormseekerEnabled()) {
            StormseekerWiring.registerGates(gates);
        }
    }
}

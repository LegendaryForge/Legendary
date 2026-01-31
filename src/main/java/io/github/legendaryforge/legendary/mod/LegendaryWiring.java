package io.github.legendaryforge.legendary.mod;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.mod.questline.Questlines;

/**
 * Mod-level wiring entrypoint for Legendary.
 *
 * <p>Questlines are registered here so servers can enable/disable them from one place.
 */
public final class LegendaryWiring {

    private LegendaryWiring() {
        // static utility
    }

    public static void registerAllGates(GateService gates) {
        registerAllGates(gates, LegendaryConfig.defaults());
    }

    public static void registerAllGates(GateService gates, LegendaryConfig config) {
        for (var questline : Questlines.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerGates(gates);
            }
        }
    }
}

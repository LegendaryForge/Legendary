package io.github.legendaryforge.legendary.core.api.questline;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.questline.runtime.LegendarySystemRegistrar;

/**
 * Mod-level wiring entrypoint for Legendary.
 *
 * <p>Questlines come from the {@link QuestlineRegistry} the caller supplies, so core never names
 * one. The former no-config overloads are gone: they called a hard-coded {@code defaults()}, which
 * is what tied this class to Stormseeker.
 */
public final class LegendaryWiring {

    private LegendaryWiring() {
        // static utility
    }

    public static void registerAllGates(QuestlineRegistry registry, GateService gates, LegendaryConfig config) {
        for (QuestlineModule questline : registry.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerGates(gates);
            }
        }
    }

    public static void registerAllSystems(
            QuestlineRegistry registry, LegendarySystemRegistrar registrar, LegendaryConfig config) {
        for (QuestlineModule questline : registry.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerSystems(registrar);
            }
        }
    }

    public static void registerAllListeners(QuestlineRegistry registry, EventBus bus, LegendaryConfig config) {
        for (QuestlineModule questline : registry.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerListeners(bus);
            }
        }
    }
}

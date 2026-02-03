package io.github.legendaryforge.legendary.mod.questline;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.mod.runtime.LegendarySystemRegistrar;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;

/** Stormseeker questline module wrapper. */
public final class StormseekerQuestline implements QuestlineModule {

    public static final String ID = "stormseeker";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Stormseeker";
    }

    @Override
    public void registerGates(GateService gates) {
        StormseekerWiring.registerGates(gates);
    }

    @Override
    public void registerSystems(LegendarySystemRegistrar registrar) {
        StormseekerWiring.registerSystems(registrar);
    }
}

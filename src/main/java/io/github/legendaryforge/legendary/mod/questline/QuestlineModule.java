package io.github.legendaryforge.legendary.mod.questline;

import io.github.legendaryforge.legendary.core.api.gate.GateService;

/**
 * A questline module hosted inside the Legendary mod.
 *
 * <p>Questlines are independently owned (content, gates, logic) but registered through the mod
 * aggregator so servers can enable/disable them from one place.
 */
public interface QuestlineModule {

    /** Stable questline id used for config toggles (e.g., "stormseeker"). */
    String id();

    /** Human-friendly name for logs and future config UIs. */
    String displayName();

    /** Register this questline's gates into the provided GateService. */
    void registerGates(GateService gates);
}

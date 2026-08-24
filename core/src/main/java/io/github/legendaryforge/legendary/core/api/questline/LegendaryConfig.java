package io.github.legendaryforge.legendary.core.api.questline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal configuration surface for the Legendary mod.
 *
 * <p>This is intentionally in-memory only for now (no file parsing yet).
 */
public record LegendaryConfig(Map<String, Boolean> questlinesEnabled) {

    public LegendaryConfig {
        questlinesEnabled = Map.copyOf(questlinesEnabled);
    }

    /** Enables every questline in {@code registry}. Replaces the former hard-coded {@code defaults()}. */
    public static LegendaryConfig enablingAll(QuestlineRegistry registry) {
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (QuestlineModule module : registry.all()) {
            enabled.put(module.id(), true);
        }
        return new LegendaryConfig(enabled);
    }

    public static LegendaryConfig allDisabled() {
        return new LegendaryConfig(Map.of());
    }

    public static LegendaryConfig of(Map<String, Boolean> questlinesEnabled) {
        return new LegendaryConfig(questlinesEnabled);
    }

    public boolean isEnabled(String questlineId) {
        return questlinesEnabled.getOrDefault(questlineId, false);
    }
}

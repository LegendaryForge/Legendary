package io.github.legendaryforge.legendary.mod;

import io.github.legendaryforge.legendary.mod.questline.StormseekerQuestline;
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

    public static LegendaryConfig defaults() {
        return new LegendaryConfig(Map.of(StormseekerQuestline.ID, true));
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

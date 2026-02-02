package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerQuestAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test-only helpers for constructing Stormseeker gate request attributes. */
final class StormseekerGateTestKit {

    private StormseekerGateTestKit() {}

    static Map<String, String> requiredStep(String requiredQuestStep) {
        return Map.of(StormseekerQuestAttributes.REQUIRED_QUEST_STEP, requiredQuestStep);
    }

    static Map<String, String> requiredAndCanonicalStep(String requiredQuestStep, String questStep) {
        return Map.of(
                StormseekerQuestAttributes.REQUIRED_QUEST_STEP, requiredQuestStep,
                StormseekerQuestAttributes.QUEST_STEP, questStep);
    }

    /**
     * Back-compat attribute set: provides both canonical and legacy quest step keys.
     *
     * <p>Use this where older harness paths still depend on {@code questStep}.
     */
    static Map<String, String> requiredAndStepsBackCompat(String requiredQuestStep, String questStep) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(StormseekerQuestAttributes.REQUIRED_QUEST_STEP, requiredQuestStep);
        m.put(StormseekerQuestAttributes.QUEST_STEP, questStep);
        m.put(StormseekerQuestAttributes.LEGACY_QUEST_STEP, questStep);
        return Map.copyOf(m);
    }
}

package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.objective.QuestObjective;
import java.util.List;

/**
 * Canonical Stormseeker objectives (scaffold).
 *
 * <p>Phase 2 (Dual Lodestones) begins by proving elemental alignment.
 * In code, that starts with the Flowing Trial -> the Coursing Lodestone.
 */
public final class StormseekerObjectives {

    public static final ResourceId OBJECTIVE_FLOWING_TRIAL = ResourceId.of("stormseeker", "objective_flowing_trial");
    public static final ResourceId OBJECTIVE_ANCHORED_TRIAL = ResourceId.of("stormseeker", "objective_anchored_trial");

    private StormseekerObjectives() {}

    public static QuestObjective<StormseekerProgress> flowingTrial() {
        return new QuestObjective<>() {
            @Override
            public ResourceId id() {
                return OBJECTIVE_FLOWING_TRIAL;
            }

            @Override
            public String summary() {
                return "Complete the Flowing Trial (earn the Coursing Lodestone).";
            }

            @Override
            public boolean isSatisfied(StormseekerProgress progress) {
                return progress.hasCoursingLodestone();
            }
        };
    }

    /** Placeholder only (no mechanics in Phase 1 scaffold). */
    public static QuestObjective<StormseekerProgress> anchoredTrialPlaceholder() {
        return new QuestObjective<>() {
            @Override
            public ResourceId id() {
                return OBJECTIVE_ANCHORED_TRIAL;
            }

            @Override
            public String summary() {
                return "Complete the Anchored Trial (earn the Standing Lodestone)."; // future Phase 2
            }

            @Override
            public boolean isSatisfied(StormseekerProgress progress) {
                return progress.hasStandingLodestone();
            }
        };
    }

    /** Phase 2 Flowing Trial objective set (scaffold). */
    public static List<QuestObjective<StormseekerProgress>> phase2FlowingTrial() {
        return List.of(flowingTrial());
    }

    /** Phase 2 Dual Lodestones objective set (scaffold). */
    public static List<QuestObjective<StormseekerProgress>> phase2DualLodestones() {
        return List.of(flowingTrial(), anchoredTrialPlaceholder());
    }
}

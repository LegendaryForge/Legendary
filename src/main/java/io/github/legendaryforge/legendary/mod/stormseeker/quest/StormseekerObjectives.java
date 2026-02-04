package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.questline.objective.QuestObjective;
import java.util.List;

/**
 * Canonical Stormseeker objectives (scaffold).
 *
 * <p>Phase 1 (Attunement) begins by proving elemental alignment.
 * In code, that starts with the Flowing Trial -> Sigil A.
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
                return "Complete the Flowing Trial (earn Sigil A).";
            }

            @Override
            public boolean isSatisfied(StormseekerProgress progress) {
                return progress.hasSigilA();
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
                return "Complete the Anchored Trial (earn Sigil B)."; // future Phase 2
            }

            @Override
            public boolean isSatisfied(StormseekerProgress progress) {
                return progress.hasSigilB();
            }
        };
    }

    /** Phase 1 Attunement objective set (scaffold). */
    public static List<QuestObjective<StormseekerProgress>> phase1Attunement() {
        return List.of(flowingTrial());
    }

    /** Phase 2 Dual Sigils objective set (scaffold). */
    public static List<QuestObjective<StormseekerProgress>> phase2DualSigils() {
        return List.of(flowingTrial(), anchoredTrialPlaceholder());
    }
}

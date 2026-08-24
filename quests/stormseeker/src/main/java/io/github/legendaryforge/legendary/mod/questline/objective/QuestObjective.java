package io.github.legendaryforge.legendary.mod.questline.objective;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;

/**
 * Content-side objective contract.
 *
 * <p>Objectives are engine-agnostic: they evaluate against authoritative quest progress
 * and return whether the objective is satisfied.
 *
 * @param <P> progress type for a questline
 */
public interface QuestObjective<P> {

    /** Stable identifier for UI, persistence, and analytics. */
    ResourceId id();

    /** Human-readable summary (scaffold; host can localize later). */
    String summary();

    /** Whether this objective is currently satisfied. */
    boolean isSatisfied(P progress);
}

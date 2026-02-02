package io.github.legendaryforge.legendary.mod.stormseeker.quest;

/**
 * Canonical attribute keys for Stormseeker gate checks.
 *
 * <p>These attributes are consumed by content-side gates (Legendary) but are produced by whatever
 * authoritative player/session/progress system exists in the runtime environment.
 *
 * <p>Do not introduce new keys casually; treat these as part of the public contract between
 * Stormseeker progress tracking and encounter activation gating.
 */
public final class StormseekerQuestAttributes {

    /**
     * Gate attribute: which quest step is required to allow activation.
     *
     * <p>Provided by the activator/encounter definition as an attribute on the gate request.
     */
    public static final String REQUIRED_QUEST_STEP = "requiredQuestStep";

    /**
     * Canonical quest step attribute key (Phase 3+).
     *
     * <p>Produced by the authoritative progress system and injected into gate request attributes.
     */
    public static final String QUEST_STEP = "legendary.quest.step";

    /**
     * Legacy quest step attribute key (temporary back-compat).
     *
     * <p>Accepted until harness/tests and any older integrations migrate to {@link #QUEST_STEP}.
     */
    public static final String LEGACY_QUEST_STEP = "questStep";

    private StormseekerQuestAttributes() {}
}

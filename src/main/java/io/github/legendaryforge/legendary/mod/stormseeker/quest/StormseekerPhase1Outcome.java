package io.github.legendaryforge.legendary.mod.stormseeker.quest;

/**
 * Durable outcome of Phase 1 (Attunement).
 *
 * This is not a live runtime object.
 * It represents what the world/host should now "know".
 */
public record StormseekerPhase1Outcome(String playerId, boolean attuned) {}

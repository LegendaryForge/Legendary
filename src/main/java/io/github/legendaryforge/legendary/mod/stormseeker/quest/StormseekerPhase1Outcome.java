package io.github.legendaryforge.legendary.mod.stormseeker.quest;

/**
 * Durable Phase 1 milestone emitted to the host.
 *
 * <p>Current semantics: emitted exactly once when Sigil A is granted during the Flowing Trial.
 * This intentionally does not claim full Phase 1 completion beyond what the code proves today.
 */
public record StormseekerPhase1Outcome(String playerId, StormseekerPhaseMilestone milestone) {
    public static StormseekerPhase1Outcome sigilAGranted(String playerId) {
        return new StormseekerPhase1Outcome(playerId, StormseekerPhaseMilestone.SIGIL_A_GRANTED);
    }
}

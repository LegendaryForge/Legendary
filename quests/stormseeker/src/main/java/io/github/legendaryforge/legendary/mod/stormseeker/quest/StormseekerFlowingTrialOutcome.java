package io.github.legendaryforge.legendary.mod.stormseeker.quest;

/**
 * Durable Flowing Trial milestone emitted to the host.
 *
 * <p>Current semantics: emitted exactly once when Sigil A is granted during the Flowing Trial.
 * This intentionally does not claim full Flowing Trial completion beyond what the code proves today.
 */
public record StormseekerFlowingTrialOutcome(String playerId, StormseekerPhaseMilestone milestone) {
    public static StormseekerFlowingTrialOutcome sigilAGranted(String playerId) {
        return new StormseekerFlowingTrialOutcome(playerId, StormseekerPhaseMilestone.SIGIL_A_GRANTED);
    }
}

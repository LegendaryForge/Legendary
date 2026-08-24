package io.github.legendaryforge.legendary.quests.stormseeker.quest;

/**
 * Capability queries derived purely from the Stormseeker quest phase.
 *
 * <p>The world (systems, wiring, gates) should ask these questions rather than embedding quest logic.
 * No content logic belongs here.
 */
public final class StormseekerCapabilities {

    public boolean canSenseStorms(StormseekerProgress progress) {
        return switch (progress.phase()) {
            case PHASE_0_WATCHING_ELEMENTAL -> false;
            default -> true;
        };
    }

    public boolean canTrackSigils(StormseekerProgress progress) {
        return switch (progress.phase()) {
            case PHASE_0_WATCHING_ELEMENTAL, PHASE_1_STORM_TREK, PHASE_1_5_ATTUNEMENT -> false;
            default -> true;
        };
    }

    public boolean canForgeAssembleIncompleteForm(StormseekerProgress progress) {
        return progress.phase() == StormseekerPhase.PHASE_3_INCOMPLETE_FORM;
    }

    public boolean canForgeFinalizeStormseeker(StormseekerProgress progress) {
        return progress.phase() == StormseekerPhase.PHASE_4_STORMS_ANSWER;
    }

    /**
     * Storms amplify but do not gate.
     *
     * <p>This is a presentation/feedback hint only. Do not hard-block progression on storms.
     */
    public boolean stormsShouldAmplifySignals(StormseekerProgress progress) {
        return canSenseStorms(progress);
    }
}

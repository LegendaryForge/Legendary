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
            case UNTOUCHED, PHASE_1_THE_MARK -> false;
            default -> true;
        };
    }

    public boolean canTrackSigils(StormseekerProgress progress) {
        return switch (progress.phase()) {
            case UNTOUCHED, PHASE_1_THE_MARK, PHASE_2_THE_TREK, PHASE_3_THE_WAKING -> false;
            default -> true;
        };
    }

    public boolean canForgeAssembleIncompleteForm(StormseekerProgress progress) {
        return progress.phase() == StormseekerPhase.PHASE_5_THE_FRAME;
    }

    public boolean canForgeFinalizeStormseeker(StormseekerProgress progress) {
        return progress.phase() == StormseekerPhase.PHASE_6_THE_FORGING;
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

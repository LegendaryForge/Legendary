package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import java.util.Objects;

/**
 * Authoritative Stormseeker questline progress (scaffold-only).
 *
 * <p>Intentionally content-agnostic:
 * <ul>
 *   <li>Sigils are binary proofs only.</li>
 *   <li>No trial mechanics, rituals, or encounter logic live here.</li>
 * </ul>
 */
public final class StormseekerProgress {

    private StormseekerPhase phase;
    private boolean hasSigilA;
    private boolean hasSigilB;

    public StormseekerProgress() {
        this.phase = StormseekerPhase.UNTOUCHED;
        this.hasSigilA = false;
        this.hasSigilB = false;
    }

    public StormseekerPhase phase() {
        return phase;
    }

    public boolean hasSigilA() {
        return hasSigilA;
    }

    public boolean hasSigilB() {
        return hasSigilB;
    }

    public void grantSigilA() {
        this.hasSigilA = true;
    }

    public boolean grantSigilB() {
        if (hasSigilB()) {
            return false;
        }
        this.hasSigilB = true;
        return true;
    }

    /**
     * Advances phase if (and only if) the current phase's exit conditions are satisfied.
     *
     * <p>This method is the scaffold gatekeeper: later content plugs into exit conditions,
     * but sequencing remains authoritative here.
     */
    public void advanceIfEligible() {
        switch (phase) {
            case UNTOUCHED -> phase = StormseekerPhase.PHASE_1_THE_MARK;
            case PHASE_1_THE_MARK -> phase = StormseekerPhase.PHASE_2_THE_TREK;
            case PHASE_2_THE_TREK -> phase = StormseekerPhase.PHASE_3_THE_WAKING;
            case PHASE_3_THE_WAKING -> phase = StormseekerPhase.PHASE_4_THE_TRIALS;

            case PHASE_4_THE_TRIALS -> {
                if (hasSigilA && hasSigilB) {
                    phase = StormseekerPhase.PHASE_5_THE_FRAME;
                }
            }

            case PHASE_5_THE_FRAME -> {
                // Scaffold stub: later integrates "frame assembled" proof.
                // No automatic advancement in scaffold mode.
            }

            case PHASE_6_THE_FORGING -> {
                // Scaffold stub: later integrates "storm correction resolved" proof.
                // No automatic advancement in scaffold mode.
            }

            case COMPLETE -> {
                // Final; no-op.
            }
        }
    }

    /**
     * Strict phase advance used by harness/testing and future integration points.
     * Only allows advancing to the immediate next phase.
     */
    public void advanceToNextOrThrow(StormseekerPhase target) {
        Objects.requireNonNull(target, "target");
        if (phase.isFinal()) {
            throw new IllegalStateException("Already final; cannot advance.");
        }

        StormseekerPhase expected = phase.next();
        if (target != expected) {
            throw new IllegalArgumentException(
                    "Invalid phase transition: current=" + phase + ", target=" + target + ", expected=" + expected);
        }
        this.phase = target;
    }
}

package io.github.legendaryforge.legendary.mod.stormseeker.quest;

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
        this.phase = StormseekerPhase.PHASE_0_UNEASE;
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

    public void grantSigilB() {
        this.hasSigilB = true;
    }

    /**
     * Advances phase if (and only if) the current phase's exit conditions are satisfied.
     *
     * <p>This method is the scaffold gatekeeper: later content plugs into exit conditions,
     * but sequencing remains authoritative here.
     */
    public void advanceIfEligible() {
        switch (phase) {
            case PHASE_0_UNEASE -> phase = StormseekerPhase.PHASE_1_ATTUNEMENT;
            case PHASE_1_ATTUNEMENT -> phase = StormseekerPhase.PHASE_1_5_AFTERSHOCK;
            case PHASE_1_5_AFTERSHOCK -> phase = StormseekerPhase.PHASE_2_DUAL_SIGILS;

            case PHASE_2_DUAL_SIGILS -> {
                if (hasSigilA && hasSigilB) {
                    phase = StormseekerPhase.PHASE_3_INCOMPLETE_FORM;
                }
            }

            case PHASE_3_INCOMPLETE_FORM -> {
                // Scaffold stub: later integrates "frame assembled" proof.
                // No automatic advancement in scaffold mode.
            }

            case PHASE_4_STORMS_ANSWER -> {
                // Scaffold stub: later integrates "storm correction resolved" proof.
                // No automatic advancement in scaffold mode.
            }

            case PHASE_5_FINAL_TEMPERING -> {
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

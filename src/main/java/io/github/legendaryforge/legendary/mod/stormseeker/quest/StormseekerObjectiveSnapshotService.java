package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.mod.questline.objective.ObjectiveStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * Content-side objective snapshot for Stormseeker.
 *
 * <p>Purpose: give hosts/UI a deterministic list of "what matters now" without engine coupling.
 * This is not an execution service; it is a read model.
 */
public final class StormseekerObjectiveSnapshotService {

    // Stable objective ids (string ids to keep this API portable).
    public static final String OBJ_REACH_ATTUNEMENT = "stormseeker.phase1.reach_attunement";
    public static final String OBJ_EARN_SIGIL_A = "stormseeker.phase1.sigil_a_flowing";

    /**
     * Snapshot objectives for the current phase (minimal scaffold).
     */
    public List<ObjectiveStatus> snapshot(StormseekerProgress progress) {
        List<ObjectiveStatus> out = new ArrayList<>();
        StormseekerPhase phase = progress.phase();

        // Phase 0: player is not yet in Attunement.
        if (phase == StormseekerPhase.PHASE_0_UNEASE) {
            out.add(ObjectiveStatus.incomplete(
                    OBJ_REACH_ATTUNEMENT,
                    "Advance to Phase 1 (Attunement) to begin elemental alignment.")); // scaffold hint
            return out;
        }

        // Phase 1 (Attunement): Flowing Trial -> Sigil A.
        if (phase == StormseekerPhase.PHASE_1_ATTUNEMENT) {
            if (progress.hasSigilA()) {
                out.add(ObjectiveStatus.complete(OBJ_EARN_SIGIL_A));
            } else {
                out.add(ObjectiveStatus.incomplete(
                        OBJ_EARN_SIGIL_A,
                        "Enter the Flowing Trial and maintain coherent movement to earn Sigil A.")); // real path
            }
            return out;
        }

        // Later phases: keep a stable contract for hosts while content expands.
        // We intentionally do not speculate mechanics here.
        return out;
    }
}

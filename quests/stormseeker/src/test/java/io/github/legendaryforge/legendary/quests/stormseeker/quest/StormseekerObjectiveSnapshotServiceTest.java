package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.questline.objective.ObjectiveStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StormseekerObjectiveSnapshotServiceTest {

    @Test
    void theMarkShowsReachAttunementObjective() {
        StormseekerObjectiveSnapshotService s = new StormseekerObjectiveSnapshotService();
        StormseekerProgress p = new StormseekerProgress();
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_THE_MARK);

        List<ObjectiveStatus> snap = s.snapshot(p);
        assertEquals(1, snap.size());
        assertEquals(
                StormseekerObjectiveSnapshotService.OBJ_REACH_ATTUNEMENT,
                snap.get(0).id());
        assertFalse(snap.get(0).completed());
        assertNotNull(snap.get(0).hint());
    }

    @Test
    void theTrialsShowSigilAObjectiveIncompleteUntilGranted() {
        StormseekerObjectiveSnapshotService s = new StormseekerObjectiveSnapshotService();
        StormseekerProgress p = new StormseekerProgress();
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_THE_MARK);
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_2_THE_TREK);
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_3_THE_WAKING);
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_4_THE_TRIALS);

        List<ObjectiveStatus> snap = s.snapshot(p);
        assertEquals(1, snap.size());
        assertEquals(
                StormseekerObjectiveSnapshotService.OBJ_EARN_SIGIL_A,
                snap.get(0).id());
        assertFalse(snap.get(0).completed());
        assertNotNull(snap.get(0).hint());

        p.grantSigilA();

        snap = s.snapshot(p);
        assertEquals(1, snap.size());
        assertEquals(
                StormseekerObjectiveSnapshotService.OBJ_EARN_SIGIL_A,
                snap.get(0).id());
        assertTrue(snap.get(0).completed());
        assertNull(snap.get(0).hint());
    }
}

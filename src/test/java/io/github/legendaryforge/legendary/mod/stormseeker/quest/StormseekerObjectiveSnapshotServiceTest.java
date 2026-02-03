package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.mod.questline.objective.ObjectiveStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

final class StormseekerObjectiveSnapshotServiceTest {

    @Test
    void phase0ShowsReachAttunementObjective() {
        StormseekerObjectiveSnapshotService s = new StormseekerObjectiveSnapshotService();
        StormseekerProgress p = new StormseekerProgress(); // PHASE_0_UNEASE

        List<ObjectiveStatus> snap = s.snapshot(p);
        assertEquals(1, snap.size());
        assertEquals(
                StormseekerObjectiveSnapshotService.OBJ_REACH_ATTUNEMENT,
                snap.get(0).id());
        assertFalse(snap.get(0).completed());
        assertNotNull(snap.get(0).hint());
    }

    @Test
    void phase1ShowsSigilAObjectiveIncompleteUntilGranted() {
        StormseekerObjectiveSnapshotService s = new StormseekerObjectiveSnapshotService();
        StormseekerProgress p = new StormseekerProgress();
        p.advanceToNextOrThrow(StormseekerPhase.PHASE_1_ATTUNEMENT);

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

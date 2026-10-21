package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The lattice's job is to guarantee that no region is empty, which is the single defect that
 * retired the convergence-star model: measured density fell to 0.00 nexuses/km² beyond 3 km. These
 * tests pin that guarantee and the determinism it rests on.
 */
class CandidateLatticeTest {

    private static final LatticeParameters P = LatticeParameters.defaults();

    @Test
    void sameSeedAndCell_givesTheSameCandidate() {
        assertEquals(CandidateLattice.candidateInCell(42L, P, 3, -7), CandidateLattice.candidateInCell(42L, P, 3, -7));
    }

    @Test
    void differentSeed_movesTheCandidate() {
        assertNotEquals(
                CandidateLattice.candidateInCell(42L, P, 3, -7), CandidateLattice.candidateInCell(43L, P, 3, -7));
    }

    @Test
    void everyCandidateStaysInsideItsJitterReach() {
        double reach = P.jitterFraction() * P.cellSize();
        for (long cx = -20; cx <= 20; cx++) {
            for (long cz = -20; cz <= 20; cz++) {
                WorldPoint2d c = CandidateLattice.candidateInCell(9001L, P, cx, cz);
                double centreX = cx * (double) P.cellSize() + P.cellSize() / 2.0;
                double centreZ = cz * (double) P.cellSize() + P.cellSize() / 2.0;
                assertTrue(
                        Math.abs(c.x() - centreX) <= reach,
                        "cell " + cx + "," + cz + " x drifted " + (c.x() - centreX) + " > " + reach);
                assertTrue(Math.abs(c.z() - centreZ) <= reach, "cell " + cx + "," + cz + " z drifted too far");
            }
        }
    }

    @Test
    void jitterAxesAreNotCorrelated() {
        // CandidateLattice's own comment claims the two axes use different hash arguments "so they
        // cannot correlate -- the same mix with the same arguments would place every candidate on a
        // diagonal". That is a testable claim, so it is tested: if dx and dz moved together, their
        // correlation would sit near +1 and this is the assertion that catches it.
        int n = 0;
        double sumX = 0;
        double sumZ = 0;
        double sumXZ = 0;
        double sumXX = 0;
        double sumZZ = 0;
        for (long cx = -40; cx <= 40; cx++) {
            for (long cz = -40; cz <= 40; cz++) {
                WorldPoint2d c = CandidateLattice.candidateInCell(7L, P, cx, cz);
                double dx = c.x() - (cx * (double) P.cellSize() + P.cellSize() / 2.0);
                double dz = c.z() - (cz * (double) P.cellSize() + P.cellSize() / 2.0);
                n++;
                sumX += dx;
                sumZ += dz;
                sumXZ += dx * dz;
                sumXX += dx * dx;
                sumZZ += dz * dz;
            }
        }
        double cov = sumXZ / n - (sumX / n) * (sumZ / n);
        double sdX = Math.sqrt(sumXX / n - (sumX / n) * (sumX / n));
        double sdZ = Math.sqrt(sumZZ / n - (sumZ / n) * (sumZ / n));
        double r = cov / (sdX * sdZ);
        assertTrue(Math.abs(r) < 0.1, "jitter axes correlate at r=" + r + "; they must be independent");
    }

    @Test
    void noDoubleCellRegionIsEmpty_anywhere() {
        // The extent guarantee, stated as a test -- and stated at the size that is actually
        // guaranteed. A window of one cellSize can be empty when it is not cell-aligned, because
        // each candidate sits in the middle 1 - 2*jitterFraction of its own cell. A window of
        // 2*cellSize always fully contains one whole cell, so it can never be empty.
        //
        // The first version of this test asserted the one-cell form, failed, and the failure was the
        // useful output: the guarantee had been stated one factor too strong in the production
        // comment as well.
        //
        // Walked far past the 3 km where the retired convergence-star model produced 0.00
        // nexuses/km2, because that is the defect this mechanism exists to remove.
        int cell = P.cellSize();
        for (int k = 0; k < 200; k++) {
            double x0 = 40_000 + k * (double) cell * 1.37;
            double z0 = -75_000 + k * (double) cell * 0.91;
            List<WorldPoint2d> found =
                    CandidateLattice.candidatesWithin(5L, P, x0, z0, x0 + 2.0 * cell, z0 + 2.0 * cell);
            assertFalse(found.isEmpty(), "no candidate in the 2-cell window at " + x0 + "," + z0);
        }
    }

    @Test
    void worstCaseNeighbourSpacing_staysWithinTheDocumentedBound() {
        // (1 + 2*jitterFraction) * cellSize is what CandidateLattice now documents as the honest
        // answer to "how far might a player walk between nexuses". Pin it, because it is the one
        // parameter in this design with a play-feel referent.
        double bound = (1 + 2 * P.jitterFraction()) * P.cellSize();
        double worst = 0;
        for (long cx = -30; cx <= 30; cx++) {
            for (long cz = -30; cz <= 30; cz++) {
                WorldPoint2d a = CandidateLattice.candidateInCell(77L, P, cx, cz);
                WorldPoint2d b = CandidateLattice.candidateInCell(77L, P, cx + 1, cz);
                worst = Math.max(worst, Math.abs(b.x() - a.x()));
            }
        }
        assertTrue(worst <= bound, "neighbour x-spacing " + worst + " exceeded the documented " + bound);
    }

    @Test
    void candidatesWithin_returnsOnlyCandidatesInsideTheBounds() {
        List<WorldPoint2d> found = CandidateLattice.candidatesWithin(11L, P, -500, -500, 500, 500);
        assertFalse(found.isEmpty());
        for (WorldPoint2d c : found) {
            assertTrue(c.x() >= -500 && c.x() <= 500 && c.z() >= -500 && c.z() <= 500, "out of bounds: " + c);
        }
    }

    @Test
    void aCandidateNeverLeavesItsOwnCell() {
        // This is the actual invariant the jitterFraction <= 0.5 cap buys, and it is what makes the
        // cell-enumeration margin defensive rather than necessary. Asserted directly, because the
        // production comment previously justified the margin with a different and incorrect claim.
        int cell = P.cellSize();
        for (long cx = -25; cx <= 25; cx++) {
            for (long cz = -25; cz <= 25; cz++) {
                WorldPoint2d c = CandidateLattice.candidateInCell(13L, P, cx, cz);
                assertTrue(
                        c.x() >= cx * (double) cell && c.x() <= (cx + 1) * (double) cell,
                        "candidate escaped cell " + cx + " on x: " + c.x());
                assertTrue(
                        c.z() >= cz * (double) cell && c.z() <= (cz + 1) * (double) cell,
                        "candidate escaped cell " + cz + " on z: " + c.z());
            }
        }
    }

    @Test
    void emptyBounds_rejected() {
        assertThrows(IllegalArgumentException.class, () -> CandidateLattice.candidatesWithin(1L, P, 10, 0, 5, 0));
    }
}

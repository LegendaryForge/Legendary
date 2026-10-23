package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.ArrayList;
import java.util.List;

/**
 * Proposes candidate positions. The lattice proposes; terrain disposes.
 *
 * <p>One seed-hashed, jittered candidate per cell. This is the salvaged half of a direction that was
 * otherwise discarded: before terrain derivation, the proposal on the table was a <em>lattice of
 * convergences</em> — tile the single-convergence star so it covers an unbounded world. That was
 * rejected as the wrong layer, because it fixed the mechanism's reach while leaving residue an
 * invisible mathematical field that would happily site a Fire nexus in a snowfield. The lattice was
 * never wrong as a <em>mechanism</em>, only as an <em>answer</em>.
 *
 * <p><strong>So note what this class does not do.</strong> It has no geometric role. Nothing grows
 * from a candidate, no arms radiate, it is not a convergence. It only proposes locations, and
 * terrain decides which of them are anything. That distinction is the whole difference between this
 * and the rejected version.
 *
 * <p>What it contributes, which terrain alone cannot: <strong>no region is empty by
 * construction</strong> — the extent problem that killed the single-convergence model, where
 * measured density fell to 0.00 nexuses/km² beyond 3 km — and a bounded cost, because scoring
 * examines N candidates rather than scanning a world.
 *
 * <p>Determinism is total: candidates are a pure function of {@code (worldSeed, cellX, cellZ)}
 * through {@link ResidueRandom}'s SplitMix64 finaliser, which is stable across runs, machines and
 * JVM versions. That is what lets the same derivation run on demand near a player today and, if
 * generation-time precomputation ever becomes available, be cached without becoming a second source
 * of truth.
 */
public final class CandidateLattice {

    private CandidateLattice() {}

    /**
     * Every candidate whose position falls inside the bounds.
     *
     * <p>Cells are enumerated with one cell of margin. That margin is <strong>defensive, not
     * load-bearing</strong>, and the distinction was established by a test rather than assumed: because
     * {@link LatticeParameters} caps {@code jitterFraction} at 0.5, a candidate can never leave its own
     * cell, so enumerating the overlapping cells alone would already be correct. The margin is what
     * keeps that true if the cap is ever raised. An earlier version of this comment claimed the margin
     * was required for determinism; it is not, and the claim was wrong.
     *
     * <p><strong>The extent guarantee this provides has a precise form, and it is not "one per
     * cell-sized window".</strong> An axis-aligned window of {@code cellSize} that is not cell-aligned
     * can contain no candidate at all, because each cell's candidate sits in the middle
     * {@code 1 - 2 * jitterFraction} of it. What is guaranteed is that <strong>every window of
     * {@code 2 * cellSize} contains at least one candidate</strong>, since such a window always fully
     * contains one whole cell. Worst-case spacing between neighbouring candidates is
     * {@code (1 + 2 * jitterFraction) * cellSize} — about 1.7 cells at the default jitter — which is the
     * honest answer to "how far might a player walk between nexuses".
     */
    public static List<WorldPoint2d> candidatesWithin(
            long worldSeed, LatticeParameters parameters, double minX, double minZ, double maxX, double maxZ) {
        return candidatesWithin(worldSeed, parameters.cellSize(), parameters.jitterFraction(), minX, minZ, maxX, maxZ);
    }

    /**
     * Every candidate inside the bounds, taking the two dials the lattice actually uses.
     *
     * <p>Exists because the lattice is shared by two parameter records — {@link LatticeParameters}
     * for the superseded weighted scorer and {@code AnchorParameters} for the volume model — and
     * neither should have to depend on the other merely to propose a position. The extent guarantee
     * and jitter reasoning documented on the overload above apply unchanged.
     */
    public static List<WorldPoint2d> candidatesWithin(
            long worldSeed, int cellSize, double jitterFraction, double minX, double minZ, double maxX, double maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("empty bounds: x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ);
        }
        long firstCellX = Math.floorDiv((long) Math.floor(minX), cellSize) - 1;
        long lastCellX = Math.floorDiv((long) Math.floor(maxX), cellSize) + 1;
        long firstCellZ = Math.floorDiv((long) Math.floor(minZ), cellSize) - 1;
        long lastCellZ = Math.floorDiv((long) Math.floor(maxZ), cellSize) + 1;

        List<WorldPoint2d> out = new ArrayList<>();
        for (long cx = firstCellX; cx <= lastCellX; cx++) {
            for (long cz = firstCellZ; cz <= lastCellZ; cz++) {
                WorldPoint2d candidate = candidateInCell(worldSeed, cellSize, jitterFraction, cx, cz);
                if (candidate.x() >= minX && candidate.x() <= maxX && candidate.z() >= minZ && candidate.z() <= maxZ) {
                    out.add(candidate);
                }
            }
        }
        return out;
    }

    /**
     * The one candidate belonging to a cell.
     *
     * <p>Exposed because a caller that already knows which cell it cares about should not have to
     * build a bounding box to ask. The two jitter axes use different hash arguments so they cannot
     * correlate — the same mix with the same arguments would place every candidate on a diagonal.
     */
    public static WorldPoint2d candidateInCell(long worldSeed, LatticeParameters parameters, long cellX, long cellZ) {
        return candidateInCell(worldSeed, parameters.cellSize(), parameters.jitterFraction(), cellX, cellZ);
    }

    /** The one candidate belonging to a cell, taking values rather than a parameter record. */
    public static WorldPoint2d candidateInCell(
            long worldSeed, int cellSize, double jitterFraction, long cellX, long cellZ) {
        double centreX = cellX * (double) cellSize + cellSize / 2.0;
        double centreZ = cellZ * (double) cellSize + cellSize / 2.0;
        double reach = jitterFraction * cellSize;
        double jitterX = ResidueRandom.signed(worldSeed, cellX, cellZ) * reach;
        double jitterZ = ResidueRandom.signed(worldSeed ^ 0x5DEECE66DL, cellZ, cellX) * reach;
        return new WorldPoint2d(centreX + jitterX, centreZ + jitterZ);
    }
}

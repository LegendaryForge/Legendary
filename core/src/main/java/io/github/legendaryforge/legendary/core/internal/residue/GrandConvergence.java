package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;

/**
 * The single point where every element's current meets.
 *
 * <p>Takes the world seed and nothing else. Every elemental questline must compute the *same*
 * point independently, so a dependency on any per-element parameter would be a correctness bug,
 * not a flexibility feature. Do not add an overload taking {@code CurrentParameters}: it would let
 * two questlines disagree about where the shared anchor is, and nothing would fail loudly. This is
 * enforced by the signature, because no test can assert that a second overload does not exist.
 */
final class GrandConvergence {

    /** Far enough from spawn that reaching it is a journey. */
    static final double MIN_RADIUS = 512.0;

    /** Near enough that it is reachable on foot within one play session. */
    static final double MAX_RADIUS = 2048.0;

    private static final long DOMAIN = 0x436F6E76L; // "Conv" — separates this from arm hashing

    private GrandConvergence() {}

    static WorldPoint2d locate(long worldSeed) {
        double angle = ResidueRandom.unit(worldSeed, DOMAIN, 0L) * 2.0 * Math.PI;
        double radius = MIN_RADIUS + ResidueRandom.unit(worldSeed, DOMAIN, 1L) * (MAX_RADIUS - MIN_RADIUS);
        return new WorldPoint2d(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }
}

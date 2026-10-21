package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * The injected terrain read that site derivation scores against.
 *
 * <p>This is the port that keeps derivation in {@code :core}. {@code ResidueNetwork}'s contract is
 * that nothing is "placed, persisted, or read from the world", and
 * {@code :core:checkNoPlatformImports} enforces it mechanically — so terrain cannot be read here
 * directly. It arrives through this interface, implemented in {@code mod/hytale} against the
 * engine's {@code SectionReader}. The shipped weather port is the same pattern.
 *
 * <p><strong>Deliberately semantic, not raw.</strong> The engine returns {@code int} ids for fluids
 * and environments, resolvable only through its own asset maps. Those ids are Hytale's, and a
 * module that cannot import Hytale cannot interpret them — so resolution happens in the adapter and
 * this port speaks already-resolved names. That also keeps the scoring rules readable as data
 * ({@code Fluid_Lava}, {@code Env_Zone4_Volcanoes}) rather than as magic numbers.
 *
 * <p><strong>Everything is bounded to loaded chunks.</strong> §9 questions 1 and 2 of the terrain
 * derivation design resolved to "positionally readable, for loaded chunks only" — and a headless
 * server loads nothing at all, while a server with one player loads a region local to that player.
 * {@link #isLoaded} is therefore not a convenience: it is the discriminator that tells a caller
 * whether a zero means "nothing there" or "nothing known".
 */
public interface TerrainSample {

    /**
     * Whether this position's chunk section is in memory.
     *
     * <p>Check before trusting any other method here. Every read below returns an empty or default
     * result for an unloaded position, and that is indistinguishable from a real negative without
     * this.
     */
    boolean isLoaded(int x, int y, int z);

    /** Resolved fluid id at this position, e.g. {@code Fluid_Lava}; empty when none or unloaded. */
    Optional<String> fluidAt(int x, int y, int z);

    /** Resolved environment id, e.g. {@code Env_Zone4_Volcanoes}; empty when unresolved or unloaded. */
    Optional<String> environmentAt(int x, int y, int z);

    /**
     * Sky light in {@code [0,15]}, or {@code -1} when unloaded.
     *
     * <p>Measured 2026-10-21: this is sky <em>access</em>, not brightness — 15 at the surface at
     * both midday and night, and 0 beside a lit torch underground. That is what makes it usable as
     * a derivation signal: deterministic, identical for every player, and immune to a player
     * lighting a cave.
     */
    int skyLightAt(int x, int y, int z);

    /** Terrain surface height for this column, or empty when unloaded. */
    OptionalInt surfaceHeightAt(int x, int z);
}

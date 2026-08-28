/**
 * Residue network: deterministic, engine-agnostic geometry shared by every elemental questline.
 *
 * <p>Coordinates are two-dimensional (world X/Z) and {@code double}. Currents are horizontal and Y
 * is not modelled — residue is read on the surface. This is a deliberate simplification, recorded
 * here so it is not re-derived.
 *
 * <p>Nothing in this package persists, mutates the world, or performs I/O. Every value is a pure
 * function of a {@code long} world seed and a position.
 */
package io.github.legendaryforge.legendary.core.api.residue;

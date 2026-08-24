/**
 * Hytale-specific adapters for LegendaryCore.
 *
 * <p>This package is reserved for bindings between the Hytale server/runtime and LegendaryCore's
 * platform-agnostic public APIs (registries, lifecycle, events, encounters).</p>
 *
 * <p>Important:
 * <ul>
 *   <li>This package is internal and may change without notice.</li>
 *   <li>It must not be required for compilation in CI environments that do not have Hytale available.</li>
 *   <li>Hytale classes must not leak into {@code core.api.*}.</li>
 * </ul>
 * </p>
 */
package io.github.legendaryforge.legendary.core.internal.platform.hytale;

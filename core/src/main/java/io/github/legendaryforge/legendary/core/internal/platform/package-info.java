/**
 * Platform integration layer for LegendaryCore.
 *
 * <p>This package (and its subpackages) contains adapters that bind a specific platform runtime
 * (e.g. Hytale) to LegendaryCore's public APIs. It is intentionally internal and provides no
 * compatibility guarantees to external consumers.</p>
 *
 * <p>Rules:
 * <ul>
 *   <li>Public APIs in {@code core.api.*} must never reference platform-specific classes.</li>
 *   <li>Platform code must adapt the platform to the core, not the reverse.</li>
 *   <li>CI must not require a platform runtime to be present.</li>
 * </ul>
 * </p>
 */
package io.github.legendaryforge.legendary.core.internal.platform;

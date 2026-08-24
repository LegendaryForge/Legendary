package io.github.legendaryforge.legendary.mod.stormseeker.integration;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;

/**
 * Engine integration notes (host adapter contract).
 *
 * <p>Canonical tick seam:
 * <ul>
 *   <li>Call {@link StormseekerWiring#tick(StormseekerHostRuntime)} exactly once per engine/simulation tick.</li>
 *   <li>Host integrations must not call Phase loops/services directly. {@code StormseekerWiring.tick(...)} is the
 *       only supported per-tick entrypoint.</li>
 * </ul>
 *
 * <p>Host-owned participation policy:
 * <ul>
 *   <li>Anchored Trial participation is a host decision (zone, interaction, UI, etc.).</li>
 *   <li>Enter: {@link StormseekerWiring#enterAnchoredTrial(String, io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress)}.</li>
 *   <li>Leave: {@link StormseekerWiring#leaveAnchoredTrial(String)}.</li>
 *   <li>If your policy requires clearing host-side session state on leave, call
 *       {@link StormseekerWiring#leaveAndCleanup(StormseekerHostRuntime, String)} instead.</li>
 * </ul>
 *
 * <p>Testing only:
 * <ul>
 *   <li>{@link StormseekerWiring#resetForTesting()} exists to prevent singleton state leakage across JVM-shared tests.</li>
 *   <li>Do not call {@code resetForTesting()} from real engine code.</li>
 * </ul>
 */
public final class StormseekerEngineIntegrationNotes {
    private StormseekerEngineIntegrationNotes() {}
}

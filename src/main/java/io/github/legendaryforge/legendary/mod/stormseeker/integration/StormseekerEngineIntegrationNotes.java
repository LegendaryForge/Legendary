package io.github.legendaryforge.legendary.mod.stormseeker.integration;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerAnchoredTrialService;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerPhase1Loop;

/**
 * Engine/ECS integration notes for Stormseeker.
 *
 * <h2>What exists today</h2>
 * Stormseeker currently exposes host-driven, tick-based seams. There is intentionally no engine scheduler
 * wiring in this repository yet.
 *
 * <h2>Canonical tick entrypoints</h2>
 * Integrations should drive Stormseeker by calling these entrypoints exactly once per host tick:
 * <ul>
 *   <li>{@link StormseekerPhase1Loop#tick(StormseekerHostRuntime)} for Phase 1 (Flowing Trial).</li>
 *   <li>{@link StormseekerAnchoredTrialService#tick(StormseekerHostRuntime)} for Phase 2 (Anchored Trial).</li>
 * </ul>
 *
 * <h2>Host responsibilities</h2>
 * The {@link StormseekerHostRuntime} is the boundary for:
 * <ul>
 *   <li>Supplying the set of player ids to evaluate this tick.</li>
 *   <li>Supplying per-player motion samples for evaluation.</li>
 *   <li>Persisting and returning per-player {@code StormseekerProgress}.</li>
 *   <li>Receiving emissions (step callbacks, hints, durable milestone notifications).</li>
 * </ul>
 *
 * <h2>Policy invariants (do not break)</h2>
 * <ul>
 *   <li>Participation membership gates ticking.</li>
 *   <li>Leaving participation does not reset host tick session state; cleanup is explicit via opt-in seams.</li>
 *   <li>Milestones are durable edge signals and must be order-independent where relevant.</li>
 * </ul>
 *
 * <p>This class is documentation-only: it exists to make future engine wiring obvious and to prevent
 * accidental double-ticking or unintended cleanup semantics.</p>
 */
public final class StormseekerEngineIntegrationNotes {

    private StormseekerEngineIntegrationNotes() {}
}

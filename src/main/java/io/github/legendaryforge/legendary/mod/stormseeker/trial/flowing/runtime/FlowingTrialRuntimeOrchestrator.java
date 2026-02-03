package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.runtime;

import io.github.legendaryforge.legendary.mod.runtime.ActivePlayerProvider;
import io.github.legendaryforge.legendary.mod.runtime.FlowHintSink;
import io.github.legendaryforge.legendary.mod.runtime.LegendaryTickContext;
import io.github.legendaryforge.legendary.mod.runtime.MotionSampleSource;
import io.github.legendaryforge.legendary.mod.runtime.PlayerRef;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerProgressStore;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSession;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase C runtime adapter for the Flowing Sigil Trial.
 *
 * <p>Host responsibilities:
 * <ul>
 *   <li>Call {@link #tick(LegendaryTickContext)} once per simulation tick.</li>
 *   <li>Provide player set, motion samples, and presentation sink.</li>
 *   <li>Persist StormseekerProgress when it changes.</li>
 * </ul>
 *
 * <p>Scope guardrails (canonical):
 * <ul>
 *   <li>Flowing Trial only.</li>
 *   <li>No Forge logic.</li>
 *   <li>No Anchored mechanics.</li>
 * </ul>
 */
public final class FlowingTrialRuntimeOrchestrator {

    private final ActivePlayerProvider players;
    private final MotionSampleSource motion;
    private final StormseekerProgressStore progressStore;
    private final FlowHintSink hints;

    private final Map<PlayerRef, FlowingTrialSession> sessions = new HashMap<>();

    public FlowingTrialRuntimeOrchestrator(
            ActivePlayerProvider players,
            MotionSampleSource motion,
            StormseekerProgressStore progressStore,
            FlowHintSink hints) {
        this.players = Objects.requireNonNull(players, "players");
        this.motion = Objects.requireNonNull(motion, "motion");
        this.progressStore = Objects.requireNonNull(progressStore, "progressStore");
        this.hints = Objects.requireNonNull(hints, "hints");
    }

    /** Run one tick of Phase C evaluation for all active players. */
    public void tick(LegendaryTickContext ctx) {
        Objects.requireNonNull(ctx, "ctx");

        for (var player : players.activePlayers()) {
            Optional<StormseekerProgress> maybe = progressStore.load(player);
            if (maybe.isEmpty()) {
                // No Stormseeker state for this player yet; ignore.
                continue;
            }

            FlowingTrialSession session = sessions.computeIfAbsent(player, p -> new FlowingTrialSession(maybe.get()));

            FlowingTrialSessionStep step = session.step(motion.sampleFor(player));

            // Emit readability intent every tick (intensity may be 0).
            hints.emit(player, step.hint(), step.status());

            // Persist progress only if we granted this tick (authoritative change).
            if (step.sigilGrantedThisTick()) {
                progressStore.save(player, session.progress());
            }

            // Hygiene: if completed and already granted, host may later choose to evict session.
            // We keep it for now to preserve determinism and avoid edgecases.
        }
    }
}

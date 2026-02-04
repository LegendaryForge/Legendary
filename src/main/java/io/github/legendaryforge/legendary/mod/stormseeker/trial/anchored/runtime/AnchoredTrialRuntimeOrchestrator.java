package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.runtime;

import io.github.legendaryforge.legendary.mod.runtime.MotionSampleSource;
import io.github.legendaryforge.legendary.mod.runtime.PlayerRef;
import io.github.legendaryforge.legendary.mod.runtime.StormseekerProgressStore;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;
import java.util.Objects;

/**
 * Phase 2D: Host/runtime orchestrator seam for Anchored Trial.
 *
 * <p>Rules (locked):
 * <ul>
 *   <li>Host ticks do not grant progress.</li>
 *   <li>Gameplay systems mutate progress elsewhere.</li>
 *   <li>This orchestrator only bridges runtime seams (progress storage + motion sampling).</li>
 * </ul>
 */
public final class AnchoredTrialRuntimeOrchestrator {

    private final StormseekerProgressStore progressStore;
    private final MotionSampleSource motionSampleSource;

    public AnchoredTrialRuntimeOrchestrator(
            StormseekerProgressStore progressStore, MotionSampleSource motionSampleSource) {
        this.progressStore = Objects.requireNonNull(progressStore);
        this.motionSampleSource = Objects.requireNonNull(motionSampleSource);
    }

    /**
     * Minimal tick entrypoint for Phase 2D scaffolding.
     *
     * <p>Loads (or creates) StormseekerProgress for the player and samples motion.
     * No progress mutation occurs here.
     */
    public void tick(PlayerRef player) {
        Objects.requireNonNull(player);

        StormseekerProgress progress = progressStore.load(player).orElseGet(() -> {
            var created = new StormseekerProgress();
            progressStore.save(player, created);
            return created;
        });

        // Current tick motion sample (used by gameplay evaluation systems elsewhere).
        MotionSample sample = motionSampleSource.sampleFor(player);

        // Avoid unused warnings (palantir-java-format doesn’t like dead locals).
        if (sample.moving() && progress.hasSigilB()) {
            // No-op: scaffold only.
        }
    }
}

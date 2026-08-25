package io.github.legendaryforge.legendary.quests.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.quests.stormseeker.client.PerceptionToggleHandler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3 (The Waking) authoritative manager.
 *
 * <p>Manages the 30-second Attunement Ritual at the Ancient Air Leyline Calibration Station.
 * Handles state transitions, diegetic feedback, and player interaction for up to 6 plates independently.
 */
public final class StormseekerAttunementService {

    public interface World {
        boolean isPlayerOnPlate(String playerId, UUID plateId);

        void applyRootEffect(String playerId);

        void removeRootEffect(String playerId);

        void updatePlateVisuals(UUID plateId, float intensity, RitualState state);

        void updatePlateAudio(UUID plateId, float pitch, float volume);

        void setPlateToIdle(UUID plateId);
    }

    public enum RitualState {
        IDLE,
        SPOOL_UP,
        ACTIVE_LOCK,
        SPOOL_DOWN
    }

    private static class RitualInstance {
        private final String playerId;
        private RitualState state;
        private long stateStartTime;

        RitualInstance(String playerId) {
            this.playerId = playerId;
            this.state = RitualState.SPOOL_UP;
            this.stateStartTime = System.currentTimeMillis();
        }
    }

    private static final long SPOOL_UP_DURATION = 5000; // 5 seconds
    private static final long ACTIVE_LOCK_DURATION = 15000; // 15 seconds
    private static final long SPOOL_DOWN_DURATION = 5000; // 5 seconds

    private final EventBus eventBus;
    private final World world;
    private final Map<UUID, RitualInstance> activeRituals = new ConcurrentHashMap<>();

    public StormseekerAttunementService(EventBus eventBus, World world) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.world = world; // nullable until host provides a real World implementation
    }

    /**
     * Starts the attunement ritual for a player on a specific plate.
     * Does nothing if a ritual is already active on that plate.
     */
    public void startRitual(UUID plateId, String playerId) {
        if (world == null) {
            return;
        }
        activeRituals.computeIfAbsent(plateId, k -> new RitualInstance(playerId));
    }

    /**
     * Main tick loop to be called by a system every game tick.
     */
    public void tick() {
        if (world == null) {
            return;
        }
        for (Map.Entry<UUID, RitualInstance> entry : activeRituals.entrySet()) {
            UUID plateId = entry.getKey();
            RitualInstance ritual = entry.getValue();

            // Interrupt if the player has moved off the plate, unless in ACTIVE_LOCK (uninterruptible)
            if (ritual.state != RitualState.ACTIVE_LOCK && !world.isPlayerOnPlate(ritual.playerId, plateId)) {
                interruptRitual(plateId, ritual);
                continue;
            }

            long elapsed = System.currentTimeMillis() - ritual.stateStartTime;

            switch (ritual.state) {
                case SPOOL_UP:
                    tickSpoolUp(plateId, ritual, elapsed);
                    break;
                case ACTIVE_LOCK:
                    tickActiveLock(plateId, ritual, elapsed);
                    break;
                case SPOOL_DOWN:
                    tickSpoolDown(plateId, ritual, elapsed);
                    break;
            }
        }
    }

    private void tickSpoolUp(UUID plateId, RitualInstance ritual, long elapsed) {
        if (elapsed >= SPOOL_UP_DURATION) {
            // Transition to ACTIVE_LOCK
            ritual.state = RitualState.ACTIVE_LOCK;
            ritual.stateStartTime = System.currentTimeMillis();
            world.applyRootEffect(ritual.playerId);
        } else {
            // Update diegetic feedback
            float progress = (float) elapsed / SPOOL_UP_DURATION;
            world.updatePlateVisuals(plateId, progress, RitualState.SPOOL_UP);
            world.updatePlateAudio(plateId, 0.5f + progress * 0.5f, progress * 0.8f);
        }
    }

    private void tickActiveLock(UUID plateId, RitualInstance ritual, long elapsed) {
        if (elapsed >= ACTIVE_LOCK_DURATION) {
            // Transition to SPOOL_DOWN
            ritual.state = RitualState.SPOOL_DOWN;
            ritual.stateStartTime = System.currentTimeMillis();
            world.removeRootEffect(ritual.playerId);
        } else {
            // Update diegetic feedback
            float progress = (float) elapsed / ACTIVE_LOCK_DURATION;
            world.updatePlateVisuals(plateId, 1.0f, RitualState.ACTIVE_LOCK);
            world.updatePlateAudio(plateId, 1.0f, 0.8f + (float) Math.sin(progress * Math.PI * 4) * 0.2f);
        }
    }

    private void tickSpoolDown(UUID plateId, RitualInstance ritual, long elapsed) {
        if (elapsed >= SPOOL_DOWN_DURATION) {
            // Handshake: Emit completion event
            eventBus.post(new PerceptionToggleHandler.AttunementCompleteEvent(ritual.playerId));
            // Cleanup
            activeRituals.remove(plateId);
            world.setPlateToIdle(plateId);
        } else {
            // Update diegetic feedback
            float progress = 1.0f - ((float) elapsed / SPOOL_DOWN_DURATION);
            world.updatePlateVisuals(plateId, progress, RitualState.SPOOL_DOWN);
            world.updatePlateAudio(plateId, 0.5f + progress * 0.5f, progress * 0.8f);
        }
    }

    private void interruptRitual(UUID plateId, RitualInstance ritual) {
        if (ritual.state == RitualState.ACTIVE_LOCK) {
            world.removeRootEffect(ritual.playerId);
        }
        activeRituals.remove(plateId);
        world.setPlateToIdle(plateId);
    }
}

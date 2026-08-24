package io.github.legendaryforge.legendary.mod.hytale.stormseeker;

import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.legendaryforge.legendary.quests.stormseeker.persistence.PropertiesProgressStore;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerFlowingTrialOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerFlowingTrialTickView;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.quests.stormseeker.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.anchored.AnchoredTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.MotionSample;
import io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing.PositionMotionTracker;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;

public final class HytaleStormseekerHost implements StormseekerHostRuntime {

    private static final MotionSample ZERO_MOTION = new MotionSample(0, 0, 0, false);
    private static final double MOVING_THRESHOLD = 0.01;

    // The delta/threshold arithmetic lives in PositionMotionTracker over in
    // :quests:stormseeker. It touches no platform type, and keeping it here meant it could
    // not be compiled or tested anywhere without the game jar. This class now only reads
    // coordinates off the engine and hands them over as plain doubles.

    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private final PropertiesProgressStore store;

    public HytaleStormseekerHost(PropertiesProgressStore store) {
        this.store = store;
    }

    public void addPlayer(String playerId, PlayerRef playerRef) {
        StormseekerProgress progress = store.load(playerId);
        players.putIfAbsent(playerId, new PlayerState(playerRef, progress));
        System.out.println("[LegendaryHytale] Loaded progress for " + playerId + ": phase=" + progress.phase()
                + " sigilA=" + progress.hasSigilA() + " sigilB=" + progress.hasSigilB());
    }

    public void removePlayer(String playerId) {
        PlayerState state = players.remove(playerId);
        if (state != null) {
            store.save(playerId, state.progress);
            System.out.println(
                    "[LegendaryHytale] Saved progress for " + playerId + ": phase=" + state.progress.phase());
        }
    }

    /** Saves all current players' progress (call on shutdown). */
    public void saveAll() {
        for (Map.Entry<String, PlayerState> entry : players.entrySet()) {
            store.save(entry.getKey(), entry.getValue().progress);
        }
        System.out.println("[LegendaryHytale] Saved progress for " + players.size() + " player(s).");
    }

    public void updateAllPositions() {
        for (PlayerState state : players.values()) {
            try {
                PlayerRef ref = state.playerRef;
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                TransformComponent transform = ref.getComponent(TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }
                Vector3d pos = transform.getPosition();
                state.updatePosition(pos.x(), pos.y(), pos.z());
            } catch (Exception e) {
                // Defensive - don't let one player break the tick loop
            }
        }
    }

    /** Returns the last Flowing Trial step for a player, or null if none yet. */
    public FlowingTrialSessionStep lastFlowingStep(String playerId) {
        PlayerState state = players.get(playerId);
        if (state == null) {
            return null;
        }
        return state.lastFlowingStep;
    }

    @Override
    public Iterable<String> playerIds() {
        return players.keySet();
    }

    @Override
    public MotionSample motionSample(String playerId) {
        PlayerState state = players.get(playerId);
        if (state == null) {
            return ZERO_MOTION;
        }
        return state.motion.latest();
    }

    @Override
    public StormseekerProgress progress(String playerId) {
        PlayerState state = players.get(playerId);
        if (state == null) {
            return new StormseekerProgress();
        }
        return state.progress;
    }

    @Override
    public void emitFlowHint(String playerId, FlowHintIntent hint) {}

    @Override
    public void emitStormseekerMilestone(StormseekerMilestoneOutcome outcome) {
        System.out.println("[LegendaryHytale] Milestone: " + outcome);
        // Save immediately on milestone (sigil grants, phase transitions)
        String playerId = outcome.playerId();
        PlayerState state = players.get(playerId);
        if (state != null) {
            store.save(playerId, state.progress);
        }
    }

    @Override
    public void emitFlowingTrialTickView(StormseekerFlowingTrialTickView view) {}

    @Override
    public void emitFlowingTrialOutcome(StormseekerFlowingTrialOutcome outcome) {
        StormseekerHostRuntime.super.emitFlowingTrialOutcome(outcome);
    }

    @Override
    public void onFlowingTrialStep(String playerId, FlowingTrialSessionStep step) {
        PlayerState state = players.get(playerId);
        if (state != null) {
            state.lastFlowingStep = step;
        }
    }

    @Override
    public void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {}

    private static final class PlayerState {
        final PlayerRef playerRef;
        final StormseekerProgress progress;
        final PositionMotionTracker motion = new PositionMotionTracker(MOVING_THRESHOLD);
        FlowingTrialSessionStep lastFlowingStep = null;

        PlayerState(PlayerRef playerRef, StormseekerProgress progress) {
            this.playerRef = playerRef;
            this.progress = progress;
        }

        void updatePosition(double x, double y, double z) {
            motion.accept(x, y, z);
        }
    }
}

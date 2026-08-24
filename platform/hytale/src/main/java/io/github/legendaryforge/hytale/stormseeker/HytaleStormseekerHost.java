package io.github.legendaryforge.hytale.stormseeker;

import io.github.legendaryforge.legendary.mod.runtime.StormseekerHostRuntime;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerMilestoneOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerFlowingTrialOutcome;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerFlowingTrialTickView;
import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored.AnchoredTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialSessionStep;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HytaleStormseekerHost implements StormseekerHostRuntime {

    private static final MotionSample ZERO_MOTION = new MotionSample(0, 0, 0, false);
    private static final double MOVING_THRESHOLD = 0.01;

    private final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    private final StormseekerProgressStore store;

    public HytaleStormseekerHost(StormseekerProgressStore store) {
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
            System.out.println("[LegendaryHytale] Saved progress for " + playerId + ": phase=" + state.progress.phase());
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
                state.updatePosition(pos.getX(), pos.getY(), pos.getZ());
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
        return state.lastMotion;
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
    public void emitFlowHint(String playerId, FlowHintIntent hint) {
    }

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
    public void emitFlowingTrialTickView(StormseekerFlowingTrialTickView view) {
    }

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
    public void onAnchoredTrialStep(String playerId, AnchoredTrialSessionStep step) {
    }

    private static final class PlayerState {
        final PlayerRef playerRef;
        final StormseekerProgress progress;
        MotionSample lastMotion = ZERO_MOTION;
        FlowingTrialSessionStep lastFlowingStep = null;

        double prevX = Double.NaN;
        double prevY = Double.NaN;
        double prevZ = Double.NaN;

        PlayerState(PlayerRef playerRef, StormseekerProgress progress) {
            this.playerRef = playerRef;
            this.progress = progress;
        }

        void updatePosition(double x, double y, double z) {
            if (Double.isNaN(prevX)) {
                prevX = x;
                prevY = y;
                prevZ = z;
                lastMotion = ZERO_MOTION;
                return;
            }

            double dx = x - prevX;
            double dy = y - prevY;
            double dz = z - prevZ;
            boolean moving = Math.sqrt(dx * dx + dy * dy + dz * dz) > MOVING_THRESHOLD;

            lastMotion = new MotionSample(dx, dy, dz, moving);
            prevX = x;
            prevY = y;
            prevZ = z;
        }
    }
}

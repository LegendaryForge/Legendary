package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Phase F: engine-agnostic participation model for the Flowing Trial loop.
 *
 * <p>The host decides when players enter/leave (zone, interaction, quest step, etc.).
 * This type is purely authoritative state for "who is currently participating".
 */
public final class FlowingTrialParticipation {

    private final Set<String> playerIds = new LinkedHashSet<>();

    public boolean enter(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerIds.add(playerId);
    }

    public boolean leave(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerIds.remove(playerId);
    }

    public boolean contains(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerIds.contains(playerId);
    }

    public Set<String> playerIdsView() {
        return Collections.unmodifiableSet(playerIds);
    }

    public void clear() {
        playerIds.clear();
    }
}

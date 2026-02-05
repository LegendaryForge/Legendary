package io.github.legendaryforge.legendary.mod.stormseeker.trial.anchored;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative participation set for Anchored Trial.
 *
 * <p>Engine integration decides *when* players enter/leave (zone, interaction, UI, etc.).
 * This class is engine-agnostic and just maintains membership.
 */
public final class AnchoredTrialParticipation {

    private final Set<String> participating = new HashSet<>();

    public boolean enter(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return participating.add(playerId);
    }

    public boolean leave(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return participating.remove(playerId);
    }

    public boolean contains(String playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return participating.contains(playerId);
    }

    public Set<String> playerIds() {
        return Collections.unmodifiableSet(participating);
    }
}

package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal tracker of players whose participation has been revoked per encounter instance.
 */
public final class ArenaRevocationTracker {

    private final ConcurrentHashMap<UUID, Set<UUID>> revokedByInstance = new ConcurrentHashMap<>();

    public void revoke(UUID instanceId, UUID playerId) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        revokedByInstance
                .computeIfAbsent(instanceId, ignored -> ConcurrentHashMap.newKeySet())
                .add(playerId);
    }

    public boolean isRevoked(UUID instanceId, UUID playerId) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(playerId, "playerId");
        Set<UUID> set = revokedByInstance.get(instanceId);
        return set != null && set.contains(playerId);
    }

    public void clearInstance(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        revokedByInstance.remove(instanceId);
    }
}

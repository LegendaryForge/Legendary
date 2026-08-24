package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal invariant that tracks encounter phase deterministically per instance.
 *
 * <p>This is a foundation for future arena enforcement (phase gates, lockouts, pressure rules)
 * without adding new public Core APIs.
 */
public final class PhaseGateInvariant implements ArenaInvariant {

    private final Map<UUID, ArenaPhase> phases = new ConcurrentHashMap<>();

    @Override
    public void onStart(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        phases.put(instanceId, ArenaPhase.ACTIVE);
    }

    @Override
    public void onEnd(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        phases.put(instanceId, ArenaPhase.ENDED);
    }

    @Override
    public void onCleanup(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        phases.remove(instanceId);
    }

    public Optional<ArenaPhase> phaseOf(UUID instanceId) {
        Objects.requireNonNull(instanceId, "instanceId");
        return Optional.ofNullable(phases.get(instanceId));
    }
}

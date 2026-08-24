package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import java.util.UUID;

/**
 * Internal hook for legendary arena enforcement.
 *
 * <p>Intentionally internal-only: no public Core API surface until proven necessary.
 */
public interface ArenaInvariant {

    void onStart(UUID instanceId);

    void onEnd(UUID instanceId);

    void onCleanup(UUID instanceId);
}

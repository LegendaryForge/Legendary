package io.github.legendaryforge.legendary.core.api.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform-provided access to player identity information.
 *
 * <p>This interface is intentionally minimal and UUID-based to remain platform-agnostic.</p>
 */
public interface PlayerDirectory {

    /**
     * Returns true if the platform recognizes the given player id.
     *
     * <p>Implementations may treat this as "known to the platform" rather than strictly "online".</p>
     */
    boolean isKnown(UUID playerId);

    /**
     * Best-effort display name for UI/logging.
     */
    Optional<String> displayName(UUID playerId);
}

package io.github.legendaryforge.legendary.core.api.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Tracks which quests are active for which players.
 */
public interface QuestDirectory {

    /**
     * Returns true if the given player currently has the given quest active.
     */
    boolean hasQuest(UUID playerId, String questId);

    /**
     * Returns the set of active quest IDs for a player (optional).
     */
    default Set<String> getActiveQuests(UUID playerId) {
        throw new UnsupportedOperationException();
    }
}

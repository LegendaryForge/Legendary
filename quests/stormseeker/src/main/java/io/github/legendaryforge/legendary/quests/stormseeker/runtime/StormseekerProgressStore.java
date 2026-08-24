package io.github.legendaryforge.legendary.quests.stormseeker.runtime;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.quests.stormseeker.quest.StormseekerProgress;
import java.util.Optional;

/**
 * Persists Stormseeker progress (authoritative).
 *
 * <p>The host integration owns storage and durability semantics.
 */
public interface StormseekerProgressStore {

    Optional<StormseekerProgress> load(PlayerRef player);

    void save(PlayerRef player, StormseekerProgress progress);
}

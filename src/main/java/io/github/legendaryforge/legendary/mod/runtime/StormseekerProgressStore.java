package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.quest.StormseekerProgress;
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

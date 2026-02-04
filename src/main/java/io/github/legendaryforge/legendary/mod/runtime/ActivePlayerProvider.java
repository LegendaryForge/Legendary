package io.github.legendaryforge.legendary.mod.runtime;

import java.util.Collection;

/**
 * Supplies the set of players that should be evaluated this tick.
 *
 * <p>The host decides what "active" means (nearby, online, in-region, etc).
 */
public interface ActivePlayerProvider {

    Collection<PlayerRef> activePlayers();
}

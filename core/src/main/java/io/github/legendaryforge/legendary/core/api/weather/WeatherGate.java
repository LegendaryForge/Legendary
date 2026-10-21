package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import java.util.Optional;

/**
 * Read side of per-player weather.
 *
 * <p>{@link #effective} is what the player is actually experiencing. Precedence is <b>per-player
 * override &gt; world-forced &gt; environment</b>, measured 2026-10-20, and is the engine's — this
 * port does not change it.
 */
public interface WeatherGate {

    /** Weather the subject is experiencing now, or empty if they have no tracker. */
    Optional<WeatherId> effective(PlayerRef subject);

    /** Weather the subject would experience with no override applied. */
    Optional<WeatherId> unoverridden(PlayerRef subject);
}

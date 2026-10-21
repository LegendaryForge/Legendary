package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import java.util.Optional;

/**
 * Write side of per-player weather: claim an override for a subject, release it, see who holds it.
 *
 * <p>One owner per subject, because the engine supports one override per player.
 *
 * <p><b>Arbitration is ours, not the engine's.</b> The engine compares owners by reference identity
 * on an arbitrary claimant {@code Ref}, so our own subsystems are indistinguishable to it and a
 * same-claimant re-claim is a silent overwrite. Refusal and the foreign-release check therefore
 * happen here, before the engine sees the call.
 *
 * <p>Deliberately absent: any equivalent of the engine's unchecked
 * {@code setOverrideWeatherIndex}/{@code clearOverrideWeatherIndex}. Those exist only behind
 * {@link WeatherBackend}.
 */
public interface PlayerWeatherController {

    WeatherClaimResult claim(PlayerRef subject, WeatherId weather, OverrideOwner owner);

    WeatherReleaseStatus release(PlayerRef subject, OverrideOwner owner);

    Optional<OverrideOwner> holder(PlayerRef subject);

    /**
     * Drop any recorded owner for this subject without touching the engine.
     *
     * <p>Called when a player leaves. The engine self-heals a stranded override once the claimant
     * {@code Ref} goes invalid, but this registry is a plain map and would otherwise leak one owner
     * per departed player — after which the next claim against a recycled id is wrongly refused.
     */
    void forget(PlayerRef subject);
}

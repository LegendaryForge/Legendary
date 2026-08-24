package io.github.legendaryforge.legendary.core.api.legendary.definition;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import java.time.Duration;
import java.util.Optional;

/**
 * Public contract for defining a Legendary Encounter.
 *
 * <p>This is a stable, platform-agnostic API intended for downstream mods.
 * Implementations should be pure definition/data and must not depend on platform types.
 */
public interface LegendaryEncounterDefinition extends EncounterDefinition {

    /** Stable ID used for persistence, rewards, telemetry, and cross-mod references. */
    LegendaryEncounterId legendaryId();

    /** Human-readable name (may be localized by the platform layer). */
    @Override
    String displayName();
    /** Optional description (may be localized by the platform layer). */
    Optional<String> description();

    /**
     * Optional cooldown between successful completions for a given player/party.
     * Empty means no explicit cooldown at the core layer.
     */
    Optional<Duration> completionCooldown();
}

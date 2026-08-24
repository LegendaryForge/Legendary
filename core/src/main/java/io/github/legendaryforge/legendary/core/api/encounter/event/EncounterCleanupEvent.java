package io.github.legendaryforge.legendary.core.api.encounter.event;

import io.github.legendaryforge.legendary.core.api.event.Event;
import java.util.UUID;

/**
 * Fired when an encounter instance is eligible for content-side state cleanup.
 *
 * <p>Contract: emitted exactly once per instanceId.
 */
public record EncounterCleanupEvent(UUID instanceId) implements Event {}

package io.github.legendaryforge.legendary.core.internal.platform.hytale;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Objects;

/**
 * Platform-side mapping from core reason IDs to player-facing messages.
 *
 * <p>Core emits stable reason IDs. Platforms decide how to present them.
 */
public final class LegendaryReasonMessageResolver {

    private static final Map<ResourceId, String> MESSAGES =
            Map.of(new ResourceId("legendary", "out_of_bounds"), "Participation revoked: left arena bounds.");

    private LegendaryReasonMessageResolver() {}

    public static String messageFor(ResourceId reasonId) {
        Objects.requireNonNull(reasonId, "reasonId");
        return MESSAGES.getOrDefault(reasonId, "Participation revoked.");
    }
}

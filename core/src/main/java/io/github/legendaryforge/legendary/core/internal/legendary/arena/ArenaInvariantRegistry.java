package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.List;
import java.util.Objects;

/**
 * Internal registry of arena invariants keyed by encounter definition id.
 *
 * <p>This stays internal until the model is proven in real legendary content.
 */
@FunctionalInterface
public interface ArenaInvariantRegistry {

    List<ArenaInvariant> invariantsFor(ResourceId definitionId);

    static ArenaInvariantRegistry empty() {
        return definitionId -> {
            Objects.requireNonNull(definitionId, "definitionId");
            return List.of();
        };
    }
}

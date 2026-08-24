package io.github.legendaryforge.legendary.core.api.encounter;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

/**
 * Deterministic identity for an encounter, derived from the encounter definition and its anchor.
 *
 * <p>This is stable across runtime instances and is suitable for lookup, uniqueness constraints,
 * and persistence keys.
 */
public record EncounterKey(ResourceId definitionId, EncounterAnchor anchor) {

    public EncounterKey {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(anchor, "anchor");
    }

    public static EncounterKey of(EncounterDefinition definition, EncounterContext context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");
        return new EncounterKey(definition.id(), context.anchor());
    }
}

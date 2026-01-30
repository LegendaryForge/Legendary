package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

public final class ToyLightningEncounterDefinition implements EncounterDefinition {

    private final ResourceId id;

    public ToyLightningEncounterDefinition(ResourceId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public String displayName() {
        return "Toy Lightning Encounter";
    }

    @Override
    public EncounterAccessPolicy accessPolicy() {
        return EncounterAccessPolicy.PUBLIC;
    }

    @Override
    public SpectatorPolicy spectatorPolicy() {
        return SpectatorPolicy.ALLOW_VIEW_ONLY;
    }

    @Override
    public int maxParticipants() {
        return 3;
    }

    @Override
    public int maxSpectators() {
        return 5;
    }
}

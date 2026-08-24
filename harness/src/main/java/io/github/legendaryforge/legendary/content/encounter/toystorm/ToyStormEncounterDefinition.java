package io.github.legendaryforge.legendary.content.encounter.toystorm;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

public final class ToyStormEncounterDefinition implements EncounterDefinition {

    private final ResourceId id;

    public ToyStormEncounterDefinition(ResourceId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public String displayName() {
        return "Toy Storm Encounter";
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

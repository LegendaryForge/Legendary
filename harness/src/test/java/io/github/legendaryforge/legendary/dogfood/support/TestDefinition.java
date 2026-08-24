package io.github.legendaryforge.legendary.dogfood.support;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

public final class TestDefinition implements EncounterDefinition {

    private final ResourceId id;
    private final String displayName;
    private final EncounterAccessPolicy accessPolicy;
    private final SpectatorPolicy spectatorPolicy;
    private final int maxParticipants;
    private final int maxSpectators;

    public TestDefinition(
            ResourceId id,
            String displayName,
            EncounterAccessPolicy accessPolicy,
            SpectatorPolicy spectatorPolicy,
            int maxParticipants,
            int maxSpectators) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.spectatorPolicy = Objects.requireNonNull(spectatorPolicy, "spectatorPolicy");
        this.maxParticipants = maxParticipants;
        this.maxSpectators = maxSpectators;
    }

    @Override
    public ResourceId id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public EncounterAccessPolicy accessPolicy() {
        return accessPolicy;
    }

    @Override
    public SpectatorPolicy spectatorPolicy() {
        return spectatorPolicy;
    }

    @Override
    public int maxParticipants() {
        return maxParticipants;
    }

    @Override
    public int maxSpectators() {
        return maxSpectators;
    }
}

package io.github.legendaryforge.legendary.core.api.encounter;

import java.util.Set;
import java.util.UUID;

public interface EncounterInstance {

    UUID instanceId();

    EncounterDefinition definition();

    EncounterState state();

    Set<UUID> participants();

    Set<UUID> spectators();
}

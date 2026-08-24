package io.github.legendaryforge.legendary.core.api.encounter;

import java.util.Optional;
import java.util.UUID;

public interface EncounterManager {

    EncounterInstance create(EncounterDefinition definition, EncounterContext context);

    JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role);

    void leave(UUID playerId, EncounterInstance instance);

    void end(EncounterInstance instance, EndReason reason);

    Optional<EncounterInstance> byInstanceId(UUID instanceId);

    /**
     * Optional lookup by deterministic encounter key.
     *
     * <p>Implementations may return {@link Optional#empty()} if they do not index encounters by key.
     */
    default Optional<EncounterInstance> byKey(EncounterKey key) {
        return Optional.empty();
    }
}

package io.github.legendaryforge.legendary.core.internal.encounter.policy;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.identity.PlayerDirectory;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal policy interface for evaluating whether a player may join an encounter.
 *
 * <p>This interface defines policy only. It performs no gameplay or platform logic.</p>
 *
 * <p>All implementations must be deterministic and fail closed when required information
 * is missing.</p>
 */
public interface EncounterJoinPolicy {

    /**
     * Evaluates whether {@code playerId} may join the encounter described by
     * {@code definition} and {@code context} as {@code role}.
     *
     * <p>Directories are optional seams. If a decision requires directory information and
     * the directory is absent, the evaluation must deny.</p>
     */
    JoinResult evaluate(
            UUID playerId,
            EncounterDefinition definition,
            EncounterContext context,
            ParticipationRole role,
            Optional<PlayerDirectory> players,
            Optional<PartyDirectory> parties);
}

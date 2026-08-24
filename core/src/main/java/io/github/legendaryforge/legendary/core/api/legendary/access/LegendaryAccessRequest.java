package io.github.legendaryforge.legendary.core.api.legendary.access;

import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Request to access an existing Legendary Encounter instance, or to start one if permitted.
 */
public final class LegendaryAccessRequest {

    private final LegendaryEncounterId encounterId;
    private final UUID playerId;
    private final UUID ownerPartyId;
    private final UUID playerPartyId;
    private final boolean lateJoiner;
    private final LegendaryVisibilityMode visibilityMode;

    private LegendaryAccessRequest(
            LegendaryEncounterId encounterId,
            UUID playerId,
            UUID ownerPartyId,
            UUID playerPartyId,
            boolean lateJoiner,
            LegendaryVisibilityMode visibilityMode) {
        this.encounterId = Objects.requireNonNull(encounterId, "encounterId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.ownerPartyId = Objects.requireNonNull(ownerPartyId, "ownerPartyId");
        this.playerPartyId = playerPartyId;
        this.lateJoiner = lateJoiner;
        this.visibilityMode = Objects.requireNonNull(visibilityMode, "visibilityMode");
    }

    public static LegendaryAccessRequest of(
            LegendaryEncounterId encounterId,
            UUID playerId,
            UUID ownerPartyId,
            UUID playerPartyId,
            boolean lateJoiner,
            LegendaryVisibilityMode visibilityMode) {
        return new LegendaryAccessRequest(
                encounterId, playerId, ownerPartyId, playerPartyId, lateJoiner, visibilityMode);
    }

    public LegendaryEncounterId encounterId() {
        return encounterId;
    }

    public UUID playerId() {
        return playerId;
    }

    /** Party that owns the encounter instance. */
    public UUID ownerPartyId() {
        return ownerPartyId;
    }

    /** Party the requesting player is currently in (empty if solo / no party). */
    public Optional<UUID> playerPartyId() {
        return Optional.ofNullable(playerPartyId);
    }

    /** True if the player joined the owner party after the encounter started. */
    public boolean lateJoiner() {
        return lateJoiner;
    }

    public LegendaryVisibilityMode visibilityMode() {
        return visibilityMode;
    }
}

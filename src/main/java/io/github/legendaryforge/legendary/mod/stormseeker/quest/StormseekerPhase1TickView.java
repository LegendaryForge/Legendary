package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.mod.questline.objective.ObjectiveStatus;
import java.util.List;

/**
 * Host-facing Phase 1 tick view for a single player.
 *
 * <p>Designed as a read model for UI/host integration:
 * - eligibility (stable denial reason or null)
 * - objective snapshot ("what matters now")
 * - whether the Phase 1 loop is treating the player as participating this tick
 */
public record StormseekerPhase1TickView(
        String playerId, ResourceId denyEnterReason, List<ObjectiveStatus> objectives, boolean participating) {}

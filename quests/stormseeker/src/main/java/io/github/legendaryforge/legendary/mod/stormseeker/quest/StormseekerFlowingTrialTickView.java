package io.github.legendaryforge.legendary.mod.stormseeker.quest;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.objective.ObjectiveStatus;
import java.util.List;

/**
 * Host-facing Flowing Trial tick view for a single player.
 *
 * <p>Designed as a read model for UI/host integration:
 * - eligibility (stable denial reason or null)
 * - objective snapshot ("what matters now")
 * - whether the Flowing Trial loop is treating the player as participating this tick
 */
public record StormseekerFlowingTrialTickView(
        String playerId, ResourceId denyEnterReason, List<ObjectiveStatus> objectives, boolean participating) {}

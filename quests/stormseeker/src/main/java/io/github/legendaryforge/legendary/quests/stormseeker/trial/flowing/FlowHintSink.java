package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;

/**
 * Receives presentation intent for Flowing Trial readability.
 *
 * <p>This is NOT gameplay logic. It's the "what should the player feel/see" channel.
 */
public interface FlowHintSink {

    void emit(PlayerRef player, FlowHintIntent hint, FlowingTrialStatus status);
}

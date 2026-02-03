package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowHintIntent;
import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.FlowingTrialStatus;

/**
 * Receives presentation intent for Flowing Trial readability.
 *
 * <p>This is NOT gameplay logic. It's the "what should the player feel/see" channel.
 */
public interface FlowHintSink {

    void emit(PlayerRef player, FlowHintIntent hint, FlowingTrialStatus status);
}

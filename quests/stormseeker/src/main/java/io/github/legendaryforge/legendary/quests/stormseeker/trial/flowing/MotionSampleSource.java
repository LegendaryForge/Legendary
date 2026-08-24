package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;

/** Provides a per-player movement sample for the current tick. */
public interface MotionSampleSource {

    MotionSample sampleFor(PlayerRef player);
}

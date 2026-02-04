package io.github.legendaryforge.legendary.mod.runtime;

import io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.MotionSample;

/** Provides a per-player movement sample for the current tick. */
public interface MotionSampleSource {

    MotionSample sampleFor(PlayerRef player);
}

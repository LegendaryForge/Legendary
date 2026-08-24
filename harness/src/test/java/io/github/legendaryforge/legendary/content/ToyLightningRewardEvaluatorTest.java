package io.github.legendaryforge.legendary.content;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ToyLightningRewardEvaluatorTest {

    @Test
    void mapsTierToDeterministicPoints() {
        UUID id = UUID.randomUUID();

        ToyLightningEndSummary none = new ToyLightningEndSummary(id, 0, 0, 0, ToyLightningScript.RewardTier.NONE);
        ToyLightningEndSummary minor = new ToyLightningEndSummary(id, 2, 5, 1, ToyLightningScript.RewardTier.MINOR);
        ToyLightningEndSummary major = new ToyLightningEndSummary(id, 3, 7, 1, ToyLightningScript.RewardTier.MAJOR);

        assertEquals(0, ToyLightningRewardEvaluator.decide(none).rewardPoints());
        assertEquals(10, ToyLightningRewardEvaluator.decide(minor).rewardPoints());
        assertEquals(25, ToyLightningRewardEvaluator.decide(major).rewardPoints());
    }
}

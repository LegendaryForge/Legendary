package io.github.legendaryforge.legendary.harness.dogfood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import io.github.legendaryforge.legendary.harness.dogfood.support.RewardDeliveryService;
import io.github.legendaryforge.legendary.harness.dogfood.support.TestContext;
import io.github.legendaryforge.legendary.harness.dogfood.support.TestDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class RewardDeliveryServiceTest {

    @Test
    void rewardDeliveredExactlyOnce_whenEndCalledTwice() {
        DefaultCoreRuntime runtime = new DefaultCoreRuntime();
        EncounterManager manager = runtime.encounters();

        RewardDeliveryService rewards = new RewardDeliveryService(runtime.events(), "toy-reward-table");

        EncounterAnchor anchor = EncounterAnchor.of(
                ResourceId.of("legendarydogfood", "world"), ResourceId.of("legendarydogfood", "anchor-reward"));
        EncounterContext context = new TestContext(anchor, Map.of());

        EncounterDefinition def = new TestDefinition(
                ResourceId.of("legendarydogfood", "toy-reward"),
                "Toy Reward Encounter",
                EncounterAccessPolicy.PUBLIC,
                SpectatorPolicy.ALLOW_VIEW_ONLY,
                1,
                1);

        EncounterInstance instance = manager.create(def, context);
        EncounterKey key = EncounterKey.of(def, context);

        manager.end(instance, EndReason.COMPLETED);
        manager.end(instance, EndReason.COMPLETED);

        assertEquals(1, rewards.deliveries());
        assertTrue(rewards.hasDeliveredFor(key));
    }
}

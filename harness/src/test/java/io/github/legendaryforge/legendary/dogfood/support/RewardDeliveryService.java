package io.github.legendaryforge.legendary.dogfood.support;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dogfood-only prototype: consumer-side exactly-once reward delivery via idempotency keys.
 */
public final class RewardDeliveryService {

    private final String rewardTableId;
    private final Set<DeliveryKey> delivered = ConcurrentHashMap.newKeySet();
    private final AtomicInteger deliveries = new AtomicInteger();

    public RewardDeliveryService(EventBus bus, String rewardTableId) {
        this.rewardTableId = Objects.requireNonNull(rewardTableId, "rewardTableId");
        bus.subscribe(EncounterEndedEvent.class, this::onEnded);
    }

    private void onEnded(EncounterEndedEvent e) {
        DeliveryKey key = new DeliveryKey(e.key(), rewardTableId);
        if (delivered.add(key)) {
            // In a real mod, this is where you'd grant loot/currency/etc.
            deliveries.incrementAndGet();
        }
    }

    public int deliveries() {
        return deliveries.get();
    }

    public boolean hasDeliveredFor(EncounterKey encounterKey) {
        return delivered.contains(new DeliveryKey(encounterKey, rewardTableId));
    }

    private record DeliveryKey(EncounterKey encounterKey, String rewardTableId) {
        private DeliveryKey {
            Objects.requireNonNull(encounterKey, "encounterKey");
            Objects.requireNonNull(rewardTableId, "rewardTableId");
        }
    }
}

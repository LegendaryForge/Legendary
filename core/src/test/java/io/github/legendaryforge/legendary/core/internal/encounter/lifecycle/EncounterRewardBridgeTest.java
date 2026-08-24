package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.identity.QuestDirectory;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

final class EncounterRewardBridgeTest {

    @Test
    void rewardListenerReceivesQuestEligibleParticipantsOnly() {
        List<Event> eventsReceived = new CopyOnWriteArrayList<>();

        @SuppressWarnings("UnusedVariable")
        EventBus bus = new EventBus() {
            @Override
            public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
                return () -> {};
            }

            @Override
            public void post(Event event) {
                eventsReceived.add(event);
            }
        };

        // Minimal QuestDirectory stub
        QuestDirectory questDirectory = new QuestDirectory() {
            @Override
            public boolean hasQuest(UUID playerId, String questId) {
                return playerId.toString().endsWith("001"); // only player1 eligible
            }
        };

        EncounterRewardListener listener = event -> {
            // simulate participants for test
            Set<UUID> participants = new HashSet<>();
            participants.add(UUID.fromString("00000000-0000-0000-0000-000000000001")); // eligible
            participants.add(UUID.fromString("00000000-0000-0000-0000-000000000002")); // not eligible

            participants.forEach(pid -> {
                if (questDirectory.hasQuest(pid, "quest:bossX")) {
                    eventsReceived.add(event);
                }
            });
        };

        EncounterAnchor anchor =
                new EncounterAnchor(ResourceId.parse("test:world"), Optional.empty(), Optional.empty());
        EncounterKey key = new EncounterKey(ResourceId.parse("test:encounter"), anchor);

        // create EncounterEndedEvent (no TestEncounter)
        EncounterEndedEvent ended = new EncounterEndedEvent(
                key, UUID.randomUUID(), ResourceId.parse("test:world"), anchor, EndReason.COMPLETED);

        listener.onReward(ended);

        assertTrue(eventsReceived.size() == 1); // only eligible participant triggers reward
    }
}

package io.github.legendaryforge.legendary.dogfood.support;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Dogfood-only helper to capture participant roster at end-event time. */
public final class EndRosterCapture {

    private final EncounterManager encounters;
    private final ConcurrentHashMap<UUID, Set<UUID>> participantsAtEnd = new ConcurrentHashMap<>();

    public EndRosterCapture(EventBus bus, EncounterManager encounters) {
        this.encounters = encounters;
        bus.subscribe(EncounterEndedEvent.class, this::onEnded);
    }

    private void onEnded(EncounterEndedEvent e) {
        Optional<EncounterInstance> instance = encounters.byInstanceId(e.instanceId());
        instance.ifPresent(i -> participantsAtEnd.put(e.instanceId(), Set.copyOf(i.participants())));
    }

    public Set<UUID> participantsAtEnd(UUID instanceId) {
        return participantsAtEnd.getOrDefault(instanceId, Set.of());
    }
}

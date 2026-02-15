package io.github.legendaryforge.legendary.mod.stormseeker.integration;

import io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring;

public class StormseekerLifecycleBridge implements EventListener<EncounterStartedEvent> {

    @Override
    public void onEvent(EncounterStartedEvent event) {
        if ("stormseeker".equals(event.definitionId().namespace())) {
            StormseekerWiring.enterAnchoredTrial(event.triggeringPlayerId().toString(), null);
        }
    }
}

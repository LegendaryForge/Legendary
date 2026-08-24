package io.github.legendaryforge.legendary.content.encounter.toystorm;

import java.util.UUID;

public record ToyStormEndSummary(UUID instanceId, int participantsAtEnd, EncounterPhase finalPhase) {}

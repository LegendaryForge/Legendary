package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import java.util.UUID;

/**
 * Content-side script hook for encounter behavior.
 *
 * <p>This lives outside LegendaryCore intentionally: we dogfood mechanics here and only
 * promote seams into core if repeated friction/pain is proven.
 */
public interface EncounterScript {

    void onStart(EncounterInstance instance, UUID triggeringPlayerId);

    void onJoin(EncounterInstance instance, UUID playerId, ParticipationRole role);

    void onEnd(EncounterInstance instance);
}

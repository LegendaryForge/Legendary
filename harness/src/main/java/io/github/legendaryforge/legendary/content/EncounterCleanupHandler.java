package io.github.legendaryforge.legendary.content;

import java.util.UUID;

/**
 * Optional script capability for bounded per-instance state.
 *
 * <p>Lives in LegendaryContent: we dogfood cleanup signals before promoting any API into core.
 */
public interface EncounterCleanupHandler {

    void onCleanup(UUID instanceId);
}

package io.github.legendaryforge.legendary.testmod;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;

/**
 * Minimal consumer of LegendaryCore.
 *
 * <p>This class exists only to verify that LegendaryCore's public API can be
 * consumed cleanly by an external module.</p>
 */
public final class TestMod {

    public static final ResourceId MOD_ID =
            ResourceId.of("legendarytest", "core_validation");

    private TestMod() {
        // no instances
    }
}

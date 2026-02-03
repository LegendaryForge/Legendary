package io.github.legendaryforge.legendary.mod.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LegendaryItemPolicyTest {

    @Test
    void onlyOwnerCanAuthoritativelyUse() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        LegendaryItemIdentity item =
                new LegendaryItemIdentity(owner, "stormseeker", LegendaryItemRole.LEGENDARY_WEAPON);

        assertTrue(LegendaryItemPolicy.canAuthoritativelyUse(owner, item));
        assertFalse(LegendaryItemPolicy.canAuthoritativelyUse(other, item));
    }
}

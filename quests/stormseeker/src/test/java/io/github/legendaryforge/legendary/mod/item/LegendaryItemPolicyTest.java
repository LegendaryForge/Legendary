package io.github.legendaryforge.legendary.mod.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.item.LegendaryItemIdentity;
import io.github.legendaryforge.legendary.core.api.item.LegendaryItemPolicy;
import io.github.legendaryforge.legendary.core.api.item.LegendaryItemRole;
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

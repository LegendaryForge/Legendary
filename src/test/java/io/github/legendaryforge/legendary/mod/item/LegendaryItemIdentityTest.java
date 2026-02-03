package io.github.legendaryforge.legendary.mod.item;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class LegendaryItemIdentityTest {

    @Test
    void rejectsBlankQuestlineId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegendaryItemIdentity(UUID.randomUUID(), " ", LegendaryItemRole.SIGIL));
    }

    @Test
    void acceptsValidIdentity() {
        LegendaryItemIdentity id = new LegendaryItemIdentity(UUID.randomUUID(), "stormseeker", LegendaryItemRole.FRAME);
        assertTrue(id.questlineId().equals("stormseeker"));
    }
}

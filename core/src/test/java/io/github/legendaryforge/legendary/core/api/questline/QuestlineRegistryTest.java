package io.github.legendaryforge.legendary.core.api.questline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestlineRegistryTest {

    private static QuestlineModule module(String id) {
        return new QuestlineModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public void registerGates(GateService gates) {
                // no-op for registry tests
            }
        };
    }

    @Test
    void emptyRegistryHasNoQuestlines() {
        assertEquals(List.of(), new QuestlineRegistry().all());
    }

    @Test
    void registrationOrderIsPreserved() {
        QuestlineRegistry registry =
                new QuestlineRegistry().register(module("alpha")).register(module("beta"));
        assertEquals(
                List.of("alpha", "beta"),
                registry.all().stream().map(QuestlineModule::id).toList());
    }

    @Test
    void duplicateIdIsRejected() {
        QuestlineRegistry registry = new QuestlineRegistry().register(module("alpha"));
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> registry.register(module("alpha")));
        assertTrue(thrown.getMessage().contains("alpha"));
    }

    @Test
    void nullModuleIsRejected() {
        assertThrows(NullPointerException.class, () -> new QuestlineRegistry().register(null));
    }

    @Test
    void allIsUnmodifiable() {
        QuestlineRegistry registry = new QuestlineRegistry().register(module("alpha"));
        assertThrows(UnsupportedOperationException.class, () -> registry.all().add(module("beta")));
    }

    @Test
    void enablingAllTurnsOnEveryRegisteredQuestline() {
        QuestlineRegistry registry =
                new QuestlineRegistry().register(module("alpha")).register(module("beta"));
        LegendaryConfig config = LegendaryConfig.enablingAll(registry);
        assertTrue(config.isEnabled("alpha"));
        assertTrue(config.isEnabled("beta"));
    }

    @Test
    void unregisteredQuestlineIsDisabled() {
        LegendaryConfig config = LegendaryConfig.enablingAll(new QuestlineRegistry().register(module("alpha")));
        assertTrue(!config.isEnabled("gamma"));
    }
}

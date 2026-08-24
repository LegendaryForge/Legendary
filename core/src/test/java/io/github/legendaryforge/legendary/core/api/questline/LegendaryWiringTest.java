package io.github.legendaryforge.legendary.core.api.questline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.questline.runtime.LegendarySystemRegistrar;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import io.github.legendaryforge.legendary.core.internal.gate.DefaultGateService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Coverage for all three {@link LegendaryWiring} {@code registerAll*} methods.
 *
 * <p>{@code registerAllGates} already had indirect coverage via two tests in {@code
 * quests/stormseeker}. {@code registerAllSystems} and {@code registerAllListeners} had zero
 * coverage and zero call sites repo-wide -- exactly the two methods {@code
 * docs/architecture/questline-framework-adoption.md} instructs a future implementer to call.
 * This class lives in {@code core} because {@code LegendaryWiring} is core-owned public API and
 * should not depend on a questline module for its own test coverage.
 */
final class LegendaryWiringTest {

    /** Recording fake -- no mock framework is on this module's classpath. */
    private static final class RecordingQuestline implements QuestlineModule {
        private final String id;
        private boolean gatesRegistered;
        private boolean systemsRegistered;
        private boolean listenersRegistered;

        RecordingQuestline(String id) {
            this.id = id;
        }

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
            gatesRegistered = true;
        }

        @Override
        public void registerSystems(LegendarySystemRegistrar registrar) {
            systemsRegistered = true;
            registrar.register(this);
        }

        @Override
        public void registerListeners(EventBus bus) {
            listenersRegistered = true;
        }
    }

    @Test
    void registerAllGates_invokesRegisterGatesOnlyForEnabledQuestlines() {
        RecordingQuestline enabled = new RecordingQuestline("enabled");
        RecordingQuestline disabled = new RecordingQuestline("disabled");
        QuestlineRegistry registry = new QuestlineRegistry().register(enabled).register(disabled);
        LegendaryConfig config = LegendaryConfig.of(Map.of("enabled", true, "disabled", false));
        GateService gates = new DefaultGateService();

        LegendaryWiring.registerAllGates(registry, gates, config);

        assertTrue(enabled.gatesRegistered);
        assertFalse(disabled.gatesRegistered);
    }

    @Test
    void registerAllGates_disabledConfigRegistersNothing() {
        RecordingQuestline questline = new RecordingQuestline("stormseeker");
        QuestlineRegistry registry = new QuestlineRegistry().register(questline);
        LegendaryConfig config = LegendaryConfig.allDisabled();

        LegendaryWiring.registerAllGates(registry, new DefaultGateService(), config);

        assertFalse(questline.gatesRegistered);
    }

    @Test
    void registerAllSystems_invokesRegisterSystemsOnlyForEnabledQuestlines() {
        RecordingQuestline enabled = new RecordingQuestline("enabled");
        RecordingQuestline disabled = new RecordingQuestline("disabled");
        QuestlineRegistry registry = new QuestlineRegistry().register(enabled).register(disabled);
        LegendaryConfig config = LegendaryConfig.of(Map.of("enabled", true, "disabled", false));
        List<Object> registered = new ArrayList<>();
        LegendarySystemRegistrar registrar = registered::add;

        LegendaryWiring.registerAllSystems(registry, registrar, config);

        assertTrue(enabled.systemsRegistered);
        assertFalse(disabled.systemsRegistered);
        assertTrue(registered.contains(enabled));
        assertFalse(registered.contains(disabled));
    }

    @Test
    void registerAllSystems_disabledConfigRegistersNothing() {
        RecordingQuestline questline = new RecordingQuestline("stormseeker");
        QuestlineRegistry registry = new QuestlineRegistry().register(questline);
        LegendaryConfig config = LegendaryConfig.allDisabled();
        LegendarySystemRegistrar registrar = system -> {
            throw new AssertionError("must not register any system when the questline is disabled: " + system);
        };

        LegendaryWiring.registerAllSystems(registry, registrar, config);

        assertFalse(questline.systemsRegistered);
    }

    @Test
    void registerAllListeners_invokesRegisterListenersOnlyForEnabledQuestlines() {
        RecordingQuestline enabled = new RecordingQuestline("enabled");
        RecordingQuestline disabled = new RecordingQuestline("disabled");
        QuestlineRegistry registry = new QuestlineRegistry().register(enabled).register(disabled);
        LegendaryConfig config = LegendaryConfig.of(Map.of("enabled", true, "disabled", false));
        EventBus bus = new SimpleEventBus();

        LegendaryWiring.registerAllListeners(registry, bus, config);

        assertTrue(enabled.listenersRegistered);
        assertFalse(disabled.listenersRegistered);
    }

    @Test
    void registerAllListeners_disabledConfigRegistersNothing() {
        RecordingQuestline questline = new RecordingQuestline("stormseeker");
        QuestlineRegistry registry = new QuestlineRegistry().register(questline);
        LegendaryConfig config = LegendaryConfig.allDisabled();

        LegendaryWiring.registerAllListeners(registry, new SimpleEventBus(), config);

        assertFalse(questline.listenersRegistered);
    }
}

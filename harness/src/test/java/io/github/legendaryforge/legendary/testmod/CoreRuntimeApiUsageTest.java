package io.github.legendaryforge.legendary.testmod;

import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.api.registry.RegistryAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time validation of the platform-agnostic CoreRuntime adapter surface.
 */
public class CoreRuntimeApiUsageTest {

    @SuppressWarnings({"unused"})
    private static void compileTimeOnly(CoreRuntime runtime) {
        RegistryAccess registries = runtime.registries();

        runtime.lifecycle();
        runtime.services();
        runtime.events();
        runtime.encounters();

        // Optional identity/party seams (may be empty at runtime; compile-time only here).
        runtime.players();
        runtime.parties();

        // Ensure returned types are usable by consumers
        registries.toString();
    }

    @Test
    void coreRuntimeApiIsConsumable() {
        assertTrue(true);
    }
}

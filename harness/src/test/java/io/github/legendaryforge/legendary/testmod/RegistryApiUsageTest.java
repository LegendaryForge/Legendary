package io.github.legendaryforge.legendary.testmod;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.registry.Registry;
import io.github.legendaryforge.legendary.core.api.registry.RegistryAccess;
import io.github.legendaryforge.legendary.core.api.registry.RegistryKey;
import io.github.legendaryforge.legendary.core.api.registry.Registrar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time validation of the LegendaryCore registry API from a consumer module.
 *
 * <p>This test intentionally avoids executing any registry logic. Its purpose is to
 * ensure that external mods can declare registry keys and reference the public
 * registry types without accessing internal implementations.</p>
 */
public class RegistryApiUsageTest {

    private static final RegistryKey<String> TEST_REGISTRY =
            RegistryKey.of(ResourceId.of("legendarytest", "strings"), String.class);

    /**
     * This method is never invoked. It exists solely to typecheck consumer-side usage
     * of the public registry API.
     */
    @SuppressWarnings({"UnusedReturnValue", "unused"})
    private static void compileTimeOnly(RegistryAccess access) {
        Registry<String> registry = access.registry(TEST_REGISTRY);
        Registrar<String> registrar = access.registrar(TEST_REGISTRY);

        ResourceId id = ResourceId.of("legendarytest", "example");

        registrar.register(id, "value");
        registrar.isRegistered(id);

        registry.get(id);
        registry.require(id);
        registry.ids();
        registry.values();
        registry.key();
    }

    @Test
    void registryApiIsConsumable() {
        // If this test module compiles, the public API is consumable.
        assertTrue(true);
    }
}

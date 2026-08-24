package io.github.legendaryforge.legendary.core.internal.registry;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.registry.Registry;
import io.github.legendaryforge.legendary.core.api.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultRegistryAccessTest {

    @Test
    void registry_preserves_deterministic_insertion_order() {
        DefaultRegistryAccess access = new DefaultRegistryAccess();
        RegistryKey<String> key = RegistryKey.of(ResourceId.parse("legendarycore:test_strings"), String.class);

        access.registrar(key).register(ResourceId.parse("legendarycore:a"), "A");
        access.registrar(key).register(ResourceId.parse("legendarycore:b"), "B");
        access.registrar(key).register(ResourceId.parse("legendarycore:c"), "C");

        Registry<String> registry = access.registry(key);

        List<ResourceId> ids = new ArrayList<>(registry.ids());
        assertEquals(
                List.of(
                        ResourceId.parse("legendarycore:a"),
                        ResourceId.parse("legendarycore:b"),
                        ResourceId.parse("legendarycore:c")),
                ids);
    }

    @Test
    void duplicate_registration_is_rejected() {
        DefaultRegistryAccess access = new DefaultRegistryAccess();
        RegistryKey<String> key = RegistryKey.of(ResourceId.parse("legendarycore:test_dupes"), String.class);

        var registrar = access.registrar(key);
        ResourceId id = ResourceId.parse("legendarycore:same");

        registrar.register(id, "first");
        assertThrows(IllegalStateException.class, () -> registrar.register(id, "second"));
    }

    @Test
    void registry_access_requires_registrar_initialized() {
        DefaultRegistryAccess access = new DefaultRegistryAccess();
        RegistryKey<String> key = RegistryKey.of(ResourceId.parse("legendarycore:test_missing"), String.class);

        // No registrar() call yet; registry() should fail fast
        assertThrows(IllegalStateException.class, () -> access.registry(key));
    }
}

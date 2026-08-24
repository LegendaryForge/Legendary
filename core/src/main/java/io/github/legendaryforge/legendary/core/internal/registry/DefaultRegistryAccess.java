package io.github.legendaryforge.legendary.core.internal.registry;

import io.github.legendaryforge.legendary.core.api.registry.*;
import java.util.HashMap;
import java.util.Map;

public final class DefaultRegistryAccess implements RegistryAccess {

    private final Map<RegistryKey<?>, DefaultRegistrar<?>> registrars = new HashMap<>();
    private final Map<RegistryKey<?>, Registry<?>> registries = new HashMap<>();

    @SuppressWarnings("unchecked")
    @Override
    public <T> Registrar<T> registrar(RegistryKey<T> key) {
        return (Registrar<T>) registrars.computeIfAbsent(key, k -> new DefaultRegistrar<>());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Registry<T> registry(RegistryKey<T> key) {
        return (Registry<T>) registries.computeIfAbsent(key, k -> {
            DefaultRegistrar<T> reg = (DefaultRegistrar<T>) registrars.get(k);
            if (reg == null) {
                throw new IllegalStateException("Registry not initialized for key: " + k);
            }
            return new DefaultRegistry<>(key, reg.snapshot());
        });
    }
}

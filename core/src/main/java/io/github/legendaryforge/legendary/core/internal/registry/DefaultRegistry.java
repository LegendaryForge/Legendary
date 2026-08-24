package io.github.legendaryforge.legendary.core.internal.registry;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.registry.Registry;
import io.github.legendaryforge.legendary.core.api.registry.RegistryKey;
import java.util.*;

final class DefaultRegistry<T> implements Registry<T> {

    private final RegistryKey<T> key;
    private final Map<ResourceId, T> entries;

    DefaultRegistry(RegistryKey<T> key, Map<ResourceId, T> entries) {
        this.key = key;
        this.entries = Collections.unmodifiableMap(entries);
    }

    @Override
    public RegistryKey<T> key() {
        return key;
    }

    @Override
    public Optional<T> get(ResourceId id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public Set<ResourceId> ids() {
        return entries.keySet();
    }

    @Override
    public Collection<T> values() {
        return entries.values();
    }
}

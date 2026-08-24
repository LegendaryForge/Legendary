package io.github.legendaryforge.legendary.core.internal.registry;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.registry.Registrar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class DefaultRegistrar<T> implements Registrar<T> {

    private final Map<ResourceId, T> entries;

    DefaultRegistrar() {
        this.entries = new LinkedHashMap<>();
    }

    Map<ResourceId, T> snapshot() {
        return new LinkedHashMap<>(entries);
    }

    @Override
    public void register(ResourceId id, T value) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, "value");
        if (entries.containsKey(id)) {
            throw new IllegalStateException("Duplicate registration for id: " + id);
        }
        entries.put(id, value);
    }

    @Override
    public boolean isRegistered(ResourceId id) {
        return entries.containsKey(id);
    }
}

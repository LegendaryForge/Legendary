package io.github.legendaryforge.legendary.core.api.registry;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

public final class RegistryKey<T> {

    private final ResourceId id;
    private final Class<T> type;

    private RegistryKey(ResourceId id, Class<T> type) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> RegistryKey<T> of(ResourceId id, Class<T> type) {
        return new RegistryKey<>(id, type);
    }

    public ResourceId id() {
        return id;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return "RegistryKey[" + id + ", type=" + type.getSimpleName() + "]";
    }
}

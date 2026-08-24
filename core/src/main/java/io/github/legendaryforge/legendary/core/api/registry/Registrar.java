package io.github.legendaryforge.legendary.core.api.registry;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;

public interface Registrar<T> {

    void register(ResourceId id, T value);

    boolean isRegistered(ResourceId id);
}

package io.github.legendaryforge.legendary.core.api.registry;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface Registry<T> {

    RegistryKey<T> key();

    Optional<T> get(ResourceId id);

    default T require(ResourceId id) {
        return get(id).orElseThrow(() -> new IllegalArgumentException("No entry registered for id: " + id));
    }

    Set<ResourceId> ids();

    Collection<T> values();
}

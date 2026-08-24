package io.github.legendaryforge.legendary.core.api.lifecycle;

import java.util.Optional;

public interface ServiceRegistry {

    <T> void register(Class<T> type, T instance);

    <T> Optional<T> get(Class<T> type);

    default <T> T require(Class<T> type) {
        return get(type).orElseThrow(() -> new IllegalStateException("Service not registered: " + type.getName()));
    }
}

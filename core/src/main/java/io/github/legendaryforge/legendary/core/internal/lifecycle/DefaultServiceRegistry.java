package io.github.legendaryforge.legendary.core.internal.lifecycle;

import io.github.legendaryforge.legendary.core.api.lifecycle.LifecyclePhase;
import io.github.legendaryforge.legendary.core.api.lifecycle.ServiceRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultServiceRegistry implements ServiceRegistry {

    private final DefaultLifecycle lifecycle;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public DefaultServiceRegistry(DefaultLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    @Override
    public <T> void register(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");

        LifecyclePhase phase = lifecycle.phase();
        if (phase == LifecyclePhase.ENABLED || phase == LifecyclePhase.DISABLED) {
            throw new IllegalStateException("Cannot register services during phase: " + phase);
        }

        Object prev = services.putIfAbsent(type, instance);
        if (prev != null) {
            throw new IllegalStateException("Service already registered: " + type.getName());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return Optional.ofNullable((T) services.get(type));
    }
}

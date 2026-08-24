package io.github.legendaryforge.legendary.core.internal.event;

import io.github.legendaryforge.legendary.core.api.event.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SimpleEventBus implements EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<EventListener<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");

        CopyOnWriteArrayList<EventListener<?>> list =
                listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());

        list.add(listener);

        return () -> list.remove(listener);
    }

    @Override
    public void post(Event event) {
        Objects.requireNonNull(event, "event");

        // Dispatch to listeners registered for the exact event class.
        // (We can add "dispatch to supertypes" later if needed; keep v0.1 minimal and predictable.)
        List<EventListener<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }

        for (EventListener<?> raw : list) {
            @SuppressWarnings("unchecked")
            EventListener<Event> typed = (EventListener<Event>) raw;
            typed.onEvent(event);
        }
    }
}

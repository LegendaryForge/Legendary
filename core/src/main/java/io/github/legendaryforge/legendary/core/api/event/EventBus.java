package io.github.legendaryforge.legendary.core.api.event;

public interface EventBus {

    <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener);

    void post(Event event);
}

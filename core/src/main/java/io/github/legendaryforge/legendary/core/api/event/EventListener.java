package io.github.legendaryforge.legendary.core.api.event;

@FunctionalInterface
public interface EventListener<E extends Event> {
    void onEvent(E event);
}

package io.github.legendaryforge.legendary.testmod;

import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.event.EventListener;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time validation of the LegendaryCore EventBus API from a consumer module.
 *
 * <p>This test intentionally avoids executing any event logic. Its purpose is to
 * ensure that external mods can declare events, listeners, and subscriptions using
 * only the public EventBus API.</p>
 */
public class EventBusApiUsageTest {

    /**
     * Simple consumer-defined event type.
     */
    private static final class TestEvent implements Event {
        // marker event
    }

    /**
     * This method is never invoked. It exists solely to typecheck consumer-side
     * usage of the public EventBus API.
     */
    @SuppressWarnings({"unused"})
    private static void compileTimeOnly(EventBus bus) {

        EventListener<TestEvent> listener = event -> {
            // no-op
        };

        Subscription subscription = bus.subscribe(TestEvent.class, listener);

        bus.post(new TestEvent());

        subscription.unsubscribe();
    }

    @Test
    void eventBusApiIsConsumable() {
        // If this test module compiles, the public EventBus API is consumable.
        assertTrue(true);
    }
}

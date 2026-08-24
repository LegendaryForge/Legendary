package io.github.legendaryforge.legendary.core.internal.event;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.event.Event;
import io.github.legendaryforge.legendary.core.api.event.Subscription;
import org.junit.jupiter.api.Test;

class SimpleEventBusTest {

    static final class Ping implements Event {
        final int value;

        Ping(int value) {
            this.value = value;
        }
    }

    @Test
    void listener_receives_posted_event() {
        SimpleEventBus bus = new SimpleEventBus();

        final int[] seen = {0};
        bus.subscribe(Ping.class, e -> seen[0] = e.value);

        bus.post(new Ping(42));
        assertEquals(42, seen[0]);
    }

    @Test
    void unsubscribe_stops_delivery() {
        SimpleEventBus bus = new SimpleEventBus();

        final int[] count = {0};
        Subscription sub = bus.subscribe(Ping.class, e -> count[0]++);

        bus.post(new Ping(1));
        sub.unsubscribe();
        bus.post(new Ping(2));

        assertEquals(1, count[0]);
    }

    @Test
    void unrelated_event_type_not_delivered() {
        SimpleEventBus bus = new SimpleEventBus();

        final boolean[] ran = {false};
        bus.subscribe(Ping.class, e -> ran[0] = true);

        bus.post(new Event() {});
        assertFalse(ran[0]);
    }
}

package io.github.legendaryforge.legendary.core.internal.encounter.lifecycle;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

final class MutableClock extends Clock {

    private long millis;
    private final ZoneId zone;

    MutableClock(long initialMillis, ZoneId zone) {
        this.millis = initialMillis;
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    void advanceMillis(long deltaMillis) {
        this.millis += deltaMillis;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(millis, zone);
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis);
    }
}

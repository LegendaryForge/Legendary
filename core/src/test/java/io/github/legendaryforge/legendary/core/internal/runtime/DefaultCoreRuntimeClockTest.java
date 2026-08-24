package io.github.legendaryforge.legendary.core.internal.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

final class DefaultCoreRuntimeClockTest {

    @Test
    void exposesInjectedClock() {
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        DefaultCoreRuntime runtime = new DefaultCoreRuntime(clock);
        assertSame(clock, runtime.clock());
    }
}

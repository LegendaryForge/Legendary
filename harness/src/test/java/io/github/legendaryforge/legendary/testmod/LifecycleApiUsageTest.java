package io.github.legendaryforge.legendary.testmod;

import io.github.legendaryforge.legendary.core.api.lifecycle.Lifecycle;
import io.github.legendaryforge.legendary.core.api.lifecycle.LifecyclePhase;
import io.github.legendaryforge.legendary.core.api.lifecycle.ServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time validation of Lifecycle and ServiceRegistry APIs from a consumer module.
 *
 * <p>This test intentionally avoids executing lifecycle logic. Its purpose is to ensure
 * external mods can reference lifecycle phases and use service registry types via the
 * public API only.</p>
 */
public class LifecycleApiUsageTest {

    /**
     * Example consumer-defined service type.
     */
    interface ExampleService {
        String name();
    }

    /**
     * This method is never invoked. It exists solely to typecheck consumer-side usage
     * of the public lifecycle/service APIs.
     */
    @SuppressWarnings({"unused"})
    private static void compileTimeOnly(Lifecycle lifecycle, ServiceRegistry services) {

        LifecyclePhase phase = lifecycle.phase();

        if (phase == LifecyclePhase.ENABLED) {
            // no-op
        }

        ExampleService svc = () -> "example";

        services.register(ExampleService.class, svc);
        services.get(ExampleService.class);
        services.require(ExampleService.class);
    }

    @Test
    void lifecycleApiIsConsumable() {
        assertTrue(true);
    }
}

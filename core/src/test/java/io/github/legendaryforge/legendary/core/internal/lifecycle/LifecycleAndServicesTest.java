package io.github.legendaryforge.legendary.core.internal.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.lifecycle.LifecyclePhase;
import org.junit.jupiter.api.Test;

class LifecycleAndServicesTest {

    @Test
    void onPhase_runs_immediately_if_already_passed() {
        DefaultLifecycle lc = new DefaultLifecycle();
        lc.advanceTo(LifecyclePhase.REGISTRATION);

        final boolean[] ran = {false};
        lc.onPhase(LifecyclePhase.BOOTSTRAP, () -> ran[0] = true);

        assertTrue(ran[0]);
    }

    @Test
    void onPhase_runs_when_phase_is_reached() {
        DefaultLifecycle lc = new DefaultLifecycle();

        final int[] count = {0};
        lc.onPhase(LifecyclePhase.ENABLED, () -> count[0]++);

        assertEquals(0, count[0]);
        lc.advanceTo(LifecyclePhase.REGISTRATION);
        assertEquals(0, count[0]);
        lc.advanceTo(LifecyclePhase.ENABLED);
        assertEquals(1, count[0]);
    }

    @Test
    void services_register_allowed_before_enabled_disallowed_after() {
        DefaultLifecycle lc = new DefaultLifecycle();
        DefaultServiceRegistry sr = new DefaultServiceRegistry(lc);

        sr.register(String.class, "ok");
        assertEquals("ok", sr.require(String.class));

        lc.advanceTo(LifecyclePhase.ENABLED);

        assertThrows(IllegalStateException.class, () -> sr.register(Integer.class, 1));
    }

    @Test
    void service_duplicate_registration_is_rejected() {
        DefaultLifecycle lc = new DefaultLifecycle();
        DefaultServiceRegistry sr = new DefaultServiceRegistry(lc);

        sr.register(String.class, "a");
        assertThrows(IllegalStateException.class, () -> sr.register(String.class, "b"));
    }
}

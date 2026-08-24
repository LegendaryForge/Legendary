package io.github.legendaryforge.legendary.core.internal.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.encounter.DefaultEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.LegendaryInstanceTrackingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.LegendaryRevokedRejoinEnforcingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.manager.LegendaryAccessEnforcingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.start.LegendaryStartGatingEncounterManager;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

final class DefaultCoreRuntimeWiringTest {

    @Test
    void defaultConstructorWiresAllCoreComponents() {
        CoreRuntime runtime = new DefaultCoreRuntime();

        assertNotNull(runtime.registries(), "registries");
        assertNotNull(runtime.lifecycle(), "lifecycle");
        assertNotNull(runtime.services(), "services");
        assertNotNull(runtime.events(), "events");
        assertNotNull(runtime.encounters(), "encounters");
    }

    @Test
    void defaultConstructorWiresLegendaryEncounterManagersInCorrectOrder() {
        CoreRuntime runtime = new DefaultCoreRuntime();

        EncounterManager encounters = runtime.encounters();
        assertTrue(
                encounters instanceof LegendaryInstanceTrackingEncounterManager,
                "top-level should track legendary instances");

        EncounterManager revokedRejoinEnforced = readDelegate(encounters);
        assertTrue(
                revokedRejoinEnforced instanceof LegendaryRevokedRejoinEnforcingEncounterManager,
                "second-level should enforce revocation");

        EncounterManager enforcing = readDelegate(revokedRejoinEnforced);
        assertTrue(enforcing instanceof LegendaryAccessEnforcingEncounterManager, "third-level should enforce access");

        EncounterManager startGated = readDelegate(enforcing);
        assertTrue(startGated instanceof LegendaryStartGatingEncounterManager, "fourth-level should gate start");

        EncounterManager base = readDelegate(startGated);
        assertTrue(base instanceof DefaultEncounterManager, "base should be DefaultEncounterManager");
    }

    private static EncounterManager readDelegate(Object wrapper) {
        try {
            Field f = wrapper.getClass().getDeclaredField("delegate");
            f.setAccessible(true);
            return (EncounterManager) f.get(wrapper);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError(
                    "Failed to read delegate from " + wrapper.getClass().getName(), e);
        }
    }
}

package io.github.legendaryforge.legendary.content.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.ActivationService;
import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionService;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import org.junit.jupiter.api.Test;

public final class CoreSeamsWiringTest {

    @Test
    void runtimeRegistersGateActivationAndSessions() {
        CoreRuntime runtime = new DefaultCoreRuntime();

        assertNotNull(runtime.services().require(GateService.class));
        assertNotNull(runtime.services().require(ActivationSessionService.class));
        assertNotNull(runtime.services().require(ActivationService.class));
    }
}

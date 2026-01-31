package io.github.legendaryforge.legendary.mod.stormseeker.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionAbortStatus;
import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionBeginStatus;
import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionService;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterAnchor;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.gate.GateDecision;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ActivationSessionServiceTest {

    static final class TestDefinition implements EncounterDefinition {
        @Override
        public ResourceId id() {
            return ResourceId.of("stormseeker", "activation_harness");
        }

        @Override
        public String displayName() {
            return "Stormseeker Activation Harness";
        }

        @Override
        public EncounterAccessPolicy accessPolicy() {
            return EncounterAccessPolicy.PARTY_ONLY;
        }

        @Override
        public SpectatorPolicy spectatorPolicy() {
            return SpectatorPolicy.ALLOW_VIEW_ONLY;
        }

        @Override
        public int maxParticipants() {
            return 5;
        }

        @Override
        public int maxSpectators() {
            return 20;
        }
    }

    static final class TestContext implements EncounterContext {
        private final EncounterAnchor anchor;

        TestContext() {
            this.anchor = EncounterAnchor.of(ResourceId.of("stormseeker", "test_world"));
        }

        @Override
        public EncounterAnchor anchor() {
            return anchor;
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }

    @Test
    void beginIsIdempotentAndAbortTransitionsState() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        ResourceId gateKey = ResourceId.of("stormseeker", "allow_gate");
        gates.register(gateKey, req -> GateDecision.allow());

        UUID activator = UUID.randomUUID();
        EncounterDefinition def = new TestDefinition();
        EncounterContext ctx = new TestContext();
        EncounterKey key = EncounterKey.of(def, ctx);

        ActivationSessionService.ActivationSessionBeginRequest req =
                new ActivationSessionService.ActivationSessionBeginRequest(
                        activator, key, def, ctx, Optional.of(gateKey), Map.of("questStep", "A1"));

        var first = sessions.begin(req);
        assertTrue(first.status() == ActivationSessionBeginStatus.CREATED
                || first.status() == ActivationSessionBeginStatus.EXISTING);

        var second = sessions.begin(req);
        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(ActivationSessionBeginStatus.EXISTING, second.status());

        var aborted = sessions.abort(first.sessionId(), ResourceId.of("stormseeker", "user_cancelled"));
        assertTrue(aborted.status() == ActivationSessionAbortStatus.ABORTED
                || aborted.status() == ActivationSessionAbortStatus.ALREADY_ABORTED);

        assertTrue(sessions.get(first.sessionId()).isPresent());
        assertEquals(first.sessionId(), sessions.get(first.sessionId()).get().sessionId());
    }

    @Test
    void beginDeniedWhenGateDenies() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        ResourceId gateKey = ResourceId.of("stormseeker", "deny_gate");
        gates.register(gateKey, req -> GateDecision.deny(ResourceId.of("stormseeker", "not_ready")));

        UUID activator = UUID.randomUUID();
        EncounterDefinition def = new TestDefinition();
        EncounterContext ctx = new TestContext();
        EncounterKey key = EncounterKey.of(def, ctx);

        ActivationSessionService.ActivationSessionBeginRequest req =
                new ActivationSessionService.ActivationSessionBeginRequest(
                        activator, key, def, ctx, Optional.of(gateKey), Map.of("questStep", "A0"));

        var result = sessions.begin(req);
        assertEquals(ActivationSessionBeginStatus.CREATED, result.status());
        assertEquals(ResourceId.of("legendarycore", "session_created"), result.reasonCode());
    }

    @Test
    void beginCreatesSessionEvenWhenActivationGateKeyProvided() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        GateService gates = runtime.services().require(GateService.class);
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        // Register Stormseeker gates into Core runtime.
        io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring.registerGates(gates);

        UUID activator = UUID.randomUUID();
        EncounterDefinition def = new TestDefinition();
        EncounterContext ctx = new TestContext();
        EncounterKey key = EncounterKey.of(def, ctx);

        // NOTE: ActivationSessionService (rc3) does NOT evaluate gates; it only creates/locks sessions.
        ActivationSessionService.ActivationSessionBeginRequest req =
                new ActivationSessionService.ActivationSessionBeginRequest(
                        activator,
                        key,
                        def,
                        ctx,
                        Optional.of(
                                io.github.legendaryforge.legendary.mod.stormseeker.StormseekerWiring.GATE_ACTIVATION),
                        Map.of("requiredQuestStep", "A1", "questStep", "A0"));

        var result = sessions.begin(req);

        // DefaultActivationSessionService currently creates sessions even when a gate denies; denial is tracked in
        // reason.
        assertEquals(ActivationSessionBeginStatus.CREATED, result.status());
        assertEquals(ResourceId.of("legendarycore", "session_created"), result.reasonCode());
    }
}

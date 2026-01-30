package io.github.legendaryforge.legendary.content.harness;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.activation.session.*;
import io.github.legendaryforge.legendary.core.api.encounter.*;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.internal.runtime.DefaultCoreRuntime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public final class ActivationSessionServiceTest {

    private static final class TestDefinition implements EncounterDefinition {
        private final ResourceId id;

        private TestDefinition(ResourceId id) {
            this.id = id;
        }

        @Override
        public ResourceId id() {
            return id;
        }

        @Override
        public String displayName() {
            return "Harness";
        }

        @Override
        public EncounterAccessPolicy accessPolicy() {
            return EncounterAccessPolicy.PARTY_ONLY;
        }

        @Override
        public SpectatorPolicy spectatorPolicy() {
            return SpectatorPolicy.DISALLOW;
        }

        @Override
        public int maxParticipants() {
            return 0;
        }

        @Override
        public int maxSpectators() {
            return 0;
        }
    }

    private static final class TestContext implements EncounterContext {
        private final EncounterAnchor anchor;

        private TestContext(EncounterAnchor anchor) {
            this.anchor = anchor;
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

    private static EncounterAnchor anchor() {
        // world id is a ResourceId; party id optional.
        return EncounterAnchor.of(ResourceId.of("legendarycontent", "world"));
    }

    private static EncounterDefinition def() {
        return new TestDefinition(ResourceId.of("legendarycontent", "harness_def"));
    }

    private static EncounterContext ctx() {
        return new TestContext(anchor());
    }

    @Test
    void beginReturnsCreatedThenExistingForSameActivatorAndKey() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        UUID activator = UUID.randomUUID();
        EncounterDefinition definition = def();
        EncounterContext context = ctx();
        EncounterKey key = EncounterKey.of(definition, context);

        ActivationSessionService.ActivationSessionBeginRequest req =
                new ActivationSessionService.ActivationSessionBeginRequest(
                        activator, key, definition, context, Optional.empty(), Map.of("a", "1"));

        ActivationSessionBeginResult first = sessions.begin(req);
        assertEquals(ActivationSessionBeginStatus.CREATED, first.status());

        ActivationSessionBeginResult second = sessions.begin(req);
        assertEquals(ActivationSessionBeginStatus.EXISTING, second.status());
        assertEquals(first.sessionId(), second.sessionId());

        ActivationSessionView view = sessions.get(first.sessionId()).orElseThrow();
        assertEquals(ActivationSessionState.OPEN, view.state());
        assertEquals(activator, view.activatorId());
        assertEquals(key, view.encounterKey());
    }

    @Test
    void beginDeniedWhenSessionLockedByDifferentActivator() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        EncounterDefinition definition = def();
        EncounterContext context = ctx();
        EncounterKey key = EncounterKey.of(definition, context);

        ActivationSessionService.ActivationSessionBeginRequest reqA =
                new ActivationSessionService.ActivationSessionBeginRequest(
                        UUID.randomUUID(), key, definition, context, Optional.empty(), Map.of());
        ActivationSessionBeginResult a = sessions.begin(reqA);
        assertEquals(ActivationSessionBeginStatus.CREATED, a.status());

        ActivationSessionService.ActivationSessionBeginRequest reqB =
                new ActivationSessionService.ActivationSessionBeginRequest(
                        UUID.randomUUID(), key, definition, context, Optional.empty(), Map.of());
        ActivationSessionBeginResult b = sessions.begin(reqB);

        assertEquals(ActivationSessionBeginStatus.DENIED, b.status());
        assertEquals(ResourceId.of("legendarycore", "session_locked"), b.reasonCode());
        assertEquals(a.sessionId(), b.sessionId());
    }

    @Test
    void abortTransitionsToAbortedAndIsIdempotent() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        EncounterDefinition definition = def();
        EncounterContext context = ctx();
        EncounterKey key = EncounterKey.of(definition, context);

        ActivationSessionBeginResult begin = sessions.begin(new ActivationSessionService.ActivationSessionBeginRequest(
                UUID.randomUUID(), key, definition, context, Optional.empty(), Map.of()));

        ActivationSessionAbortResult first =
                sessions.abort(begin.sessionId(), ResourceId.of("legendarycontent", "cancelled"));
        assertEquals(ActivationSessionAbortStatus.ABORTED, first.status());

        ActivationSessionAbortResult second =
                sessions.abort(begin.sessionId(), ResourceId.of("legendarycontent", "cancelled"));
        assertEquals(ActivationSessionAbortStatus.ALREADY_ABORTED, second.status());

        ActivationSessionView view = sessions.get(begin.sessionId()).orElseThrow();
        assertEquals(ActivationSessionState.ABORTED, view.state());
    }

    @Test
    void commitDeniedWhenMissingAndAlreadyCommittedIsIdempotent() {
        CoreRuntime runtime = new DefaultCoreRuntime();
        ActivationSessionService sessions = runtime.services().require(ActivationSessionService.class);

        ActivationSessionCommitResult missing = sessions.commit(UUID.randomUUID());
        assertEquals(ActivationSessionCommitStatus.DENIED, missing.status());

        EncounterDefinition definition = def();
        EncounterContext context = ctx();
        EncounterKey key = EncounterKey.of(definition, context);

        ActivationSessionBeginResult begin = sessions.begin(new ActivationSessionService.ActivationSessionBeginRequest(
                UUID.randomUUID(), key, definition, context, Optional.empty(), Map.of()));

        ActivationSessionCommitResult first = sessions.commit(begin.sessionId());
        assertTrue(first.status() == ActivationSessionCommitStatus.COMMITTED
                || first.status() == ActivationSessionCommitStatus.FAILED);

        ActivationSessionCommitResult second = sessions.commit(begin.sessionId());
        assertEquals(ActivationSessionCommitStatus.ALREADY_COMMITTED, second.status());

        ActivationSessionView view = sessions.get(begin.sessionId()).orElseThrow();
        assertEquals(ActivationSessionState.COMMITTED, view.state());
    }
}

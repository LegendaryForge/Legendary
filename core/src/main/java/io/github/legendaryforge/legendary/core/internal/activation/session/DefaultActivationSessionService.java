package io.github.legendaryforge.legendary.core.internal.activation.session;

import io.github.legendaryforge.legendary.core.api.activation.session.*;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultActivationSessionService implements ActivationSessionService {

    private static final class SessionRecord {
        final UUID sessionId;
        final UUID activatorId;
        final EncounterKey encounterKey;
        volatile ActivationSessionState state;
        volatile EncounterInstance instance;

        SessionRecord(UUID sessionId, UUID activatorId, EncounterKey encounterKey) {
            this.sessionId = sessionId;
            this.activatorId = activatorId;
            this.encounterKey = encounterKey;
            this.state = ActivationSessionState.OPEN;
        }
    }

    private final Map<UUID, SessionRecord> sessions = new ConcurrentHashMap<>();
    private final Map<EncounterKey, UUID> openSessionsByKey = new ConcurrentHashMap<>();

    @Override
    public ActivationSessionBeginResult begin(ActivationSessionBeginRequest request) {
        UUID existing = openSessionsByKey.get(request.encounterKey());
        if (existing != null) {
            SessionRecord record = sessions.get(existing);
            if (record != null && record.activatorId.equals(request.activatorId())) {
                return new ActivationSessionBeginResult(
                        ActivationSessionBeginStatus.EXISTING,
                        ResourceId.of("legendarycore", "session_exists"),
                        existing);
            }
            return new ActivationSessionBeginResult(
                    ActivationSessionBeginStatus.DENIED, ResourceId.of("legendarycore", "session_locked"), existing);
        }

        UUID sessionId = UUID.randomUUID();
        SessionRecord record = new SessionRecord(sessionId, request.activatorId(), request.encounterKey());
        sessions.put(sessionId, record);
        openSessionsByKey.put(request.encounterKey(), sessionId);

        return new ActivationSessionBeginResult(
                ActivationSessionBeginStatus.CREATED, ResourceId.of("legendarycore", "session_created"), sessionId);
    }

    @Override
    public ActivationSessionCommitResult commit(UUID sessionId) {
        SessionRecord record = sessions.get(sessionId);
        if (record == null) {
            return new ActivationSessionCommitResult(
                    ActivationSessionCommitStatus.DENIED,
                    ResourceId.of("legendarycore", "session_not_found"),
                    Optional.empty());
        }

        if (record.state == ActivationSessionState.COMMITTED) {
            return new ActivationSessionCommitResult(
                    ActivationSessionCommitStatus.ALREADY_COMMITTED,
                    ResourceId.of("legendarycore", "already_committed"),
                    Optional.ofNullable(record.instance));
        }

        if (record.state == ActivationSessionState.ABORTED) {
            return new ActivationSessionCommitResult(
                    ActivationSessionCommitStatus.DENIED,
                    ResourceId.of("legendarycore", "session_aborted"),
                    Optional.empty());
        }

        record.state = ActivationSessionState.COMMITTED;
        openSessionsByKey.remove(record.encounterKey);

        return new ActivationSessionCommitResult(
                ActivationSessionCommitStatus.COMMITTED, ResourceId.of("legendarycore", "committed"), Optional.empty());
    }

    @Override
    public ActivationSessionAbortResult abort(UUID sessionId, ResourceId reasonCode) {
        SessionRecord record = sessions.get(sessionId);
        if (record == null || record.state == ActivationSessionState.ABORTED) {
            return new ActivationSessionAbortResult(ActivationSessionAbortStatus.ALREADY_ABORTED, reasonCode);
        }

        record.state = ActivationSessionState.ABORTED;
        openSessionsByKey.remove(record.encounterKey);

        return new ActivationSessionAbortResult(ActivationSessionAbortStatus.ABORTED, reasonCode);
    }

    @Override
    public Optional<ActivationSessionView> get(UUID sessionId) {
        SessionRecord record = sessions.get(sessionId);
        if (record == null) return Optional.empty();

        return Optional.of(new ActivationSessionView(
                record.sessionId, record.activatorId, record.encounterKey, record.state, Map.of()));
    }
}

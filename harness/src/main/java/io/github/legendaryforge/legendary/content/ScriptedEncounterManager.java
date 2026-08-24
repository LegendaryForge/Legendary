package io.github.legendaryforge.legendary.content;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Content-side decorator that wires an {@link EncounterScript} into join semantics.
 *
 * <p>Intentionally lives in LegendaryContent (not core). This helps us dogfood what hooks are
 * truly needed before promoting anything into LegendaryCore.
 *
 * <p>Note: {@link EncounterScript#onStart} and {@link EncounterScript#onEnd} are wired via
 * {@link ScriptEventBridge} (event-driven) to avoid missed signals and to prevent double-dispatch.
 */
public final class ScriptedEncounterManager implements EncounterManager {

    private final EncounterManager delegate;
    private final EncounterScript script;

    public ScriptedEncounterManager(EncounterManager delegate, EncounterScript script) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.script = Objects.requireNonNull(script, "script");
    }

    @Override
    public EncounterInstance create(EncounterDefinition definition, EncounterContext context) {
        return delegate.create(definition, context);
    }

    @Override
    public JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role) {
        JoinResult result = delegate.join(playerId, instance, role);
        if (result == JoinResult.SUCCESS) {
            script.onJoin(instance, playerId, role);
        }
        return result;
    }

    @Override
    public void leave(UUID playerId, EncounterInstance instance) {
        delegate.leave(playerId, instance);
    }

    @Override
    public void end(EncounterInstance instance, EndReason reason) {
        delegate.end(instance, reason);
        // onEnd is event-driven via ScriptEventBridge to avoid double-dispatch.
    }

    @Override
    public Optional<EncounterInstance> byInstanceId(UUID instanceId) {
        return delegate.byInstanceId(instanceId);
    }

    @Override
    public Optional<EncounterInstance> byKey(EncounterKey key) {
        return delegate.byKey(key);
    }
}

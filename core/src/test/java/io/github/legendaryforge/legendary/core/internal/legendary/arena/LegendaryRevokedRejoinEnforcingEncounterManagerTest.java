package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class LegendaryRevokedRejoinEnforcingEncounterManagerTest {

    @Test
    void deniesParticipantRejoinWhenRevokedAndActive() {
        UUID instanceId = UUID.fromString("00000000-0000-0000-0000-000000000310");
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000311");

        Set<UUID> legendaryInstances = ConcurrentHashMap.newKeySet();
        legendaryInstances.add(instanceId);

        PhaseGateInvariant phaseGate = new PhaseGateInvariant();
        phaseGate.onStart(instanceId);

        ArenaRevocationTracker revocations = new ArenaRevocationTracker();
        revocations.revoke(instanceId, playerId);

        AtomicInteger delegateCalls = new AtomicInteger();
        EncounterManager delegate = new EncounterManager() {
            @Override
            public EncounterInstance create(
                    EncounterDefinition definition,
                    io.github.legendaryforge.legendary.core.api.encounter.EncounterContext context) {
                return null;
            }

            @Override
            public JoinResult join(UUID pid, EncounterInstance inst, ParticipationRole role) {
                delegateCalls.incrementAndGet();
                return JoinResult.SUCCESS;
            }

            @Override
            public void leave(UUID pid, EncounterInstance inst) {}

            @Override
            public void end(EncounterInstance inst, EndReason reason) {}

            @Override
            public Optional<EncounterInstance> byInstanceId(UUID id) {
                return Optional.empty();
            }

            @Override
            public Optional<EncounterInstance> byKey(EncounterKey key) {
                return Optional.empty();
            }
        };

        EncounterManager mgr = new LegendaryRevokedRejoinEnforcingEncounterManager(
                delegate, legendaryInstances::contains, phaseGate, revocations);

        EncounterInstance instance = proxyEncounterInstance(instanceId);

        assertEquals(JoinResult.DENIED_STATE, mgr.join(playerId, instance, ParticipationRole.PARTICIPANT));
        assertEquals(0, delegateCalls.get());

        assertEquals(JoinResult.SUCCESS, mgr.join(playerId, instance, ParticipationRole.SPECTATOR));
        assertEquals(1, delegateCalls.get());
    }

    private static EncounterInstance proxyEncounterInstance(UUID instanceId) {
        EncounterDefinition def = proxyEncounterDefinition();
        return (EncounterInstance) Proxy.newProxyInstance(
                EncounterInstance.class.getClassLoader(),
                new Class<?>[] {EncounterInstance.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "instanceId" -> instanceId;
                        case "definition" -> def;
                        case "key" -> Optional.empty();
                        default -> {
                            Class<?> rt = method.getReturnType();
                            if (rt.equals(Optional.class)) yield Optional.empty();
                            if (rt.equals(int.class)) yield 0;
                            if (rt.equals(boolean.class)) yield false;
                            yield null;
                        }
                    };
                });
    }

    private static EncounterDefinition proxyEncounterDefinition() {
        ResourceId id = ResourceId.parse("test:any");
        return (EncounterDefinition) Proxy.newProxyInstance(
                EncounterDefinition.class.getClassLoader(),
                new Class<?>[] {EncounterDefinition.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> id;
                    case "displayName" -> "test";
                    case "accessPolicy" ->
                        io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy.PUBLIC;
                    case "spectatorPolicy" ->
                        io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy.ALLOW_VIEW_ONLY;
                    case "maxParticipants" -> 0;
                    case "maxSpectators" -> 0;
                    default -> null;
                });
    }
}

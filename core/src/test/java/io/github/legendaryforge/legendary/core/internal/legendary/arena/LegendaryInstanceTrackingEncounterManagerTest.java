package io.github.legendaryforge.legendary.core.internal.legendary.arena;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterContext;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterInstance;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterKey;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.encounter.EndReason;
import io.github.legendaryforge.legendary.core.api.encounter.JoinResult;
import io.github.legendaryforge.legendary.core.api.encounter.ParticipationRole;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

final class LegendaryInstanceTrackingEncounterManagerTest {

    @Test
    void tracksLegendaryInstanceIdsOnCreate() {
        Set<UUID> ids = ConcurrentHashMap.newKeySet();

        UUID normalInstanceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID legendaryInstanceId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        ResourceId normalId = ResourceId.parse("test:normal_encounter");
        ResourceId legendaryId = ResourceId.parse("test:legendary_encounter");

        EncounterDefinition normalDef = proxyEncounterDefinition(EncounterDefinition.class, normalId);
        LegendaryEncounterDefinition legendaryDef =
                proxyEncounterDefinition(LegendaryEncounterDefinition.class, legendaryId);

        EncounterManager delegate = new EncounterManager() {
            @Override
            public EncounterInstance create(EncounterDefinition definition, EncounterContext context) {
                if (definition instanceof LegendaryEncounterDefinition) {
                    return proxyEncounterInstance(legendaryInstanceId);
                }
                return proxyEncounterInstance(normalInstanceId);
            }

            @Override
            public JoinResult join(UUID playerId, EncounterInstance instance, ParticipationRole role) {
                return null;
            }

            @Override
            public void leave(UUID playerId, EncounterInstance instance) {}

            @Override
            public void end(EncounterInstance instance, EndReason reason) {}

            @Override
            public Optional<EncounterInstance> byInstanceId(UUID instanceId) {
                return Optional.empty();
            }

            @Override
            public Optional<EncounterInstance> byKey(EncounterKey key) {
                return Optional.empty();
            }
        };

        LegendaryInstanceTrackingEncounterManager mgr = new LegendaryInstanceTrackingEncounterManager(delegate, ids);

        EncounterInstance normalInstance = mgr.create(normalDef, null);
        assertFalse(mgr.isLegendary(normalInstance.instanceId()));

        EncounterInstance legendaryInstance = mgr.create(legendaryDef, null);
        assertTrue(mgr.isLegendary(legendaryInstance.instanceId()));
    }

    private static EncounterInstance proxyEncounterInstance(UUID instanceId) {
        return (EncounterInstance) Proxy.newProxyInstance(
                EncounterInstance.class.getClassLoader(),
                new Class<?>[] {EncounterInstance.class},
                (proxy, method, args) -> {
                    if ("instanceId".equals(method.getName())
                            && method.getReturnType().equals(UUID.class)) {
                        return instanceId;
                    }
                    Class<?> rt = method.getReturnType();
                    if (rt.equals(Optional.class)) return Optional.empty();
                    if (rt.equals(int.class)) return 0;
                    if (rt.equals(boolean.class)) return false;
                    return null;
                });
    }

    private static <T> T proxyEncounterDefinition(Class<T> type, ResourceId id) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> switch (method.getName()) {
                    case "id" -> id;
                    case "displayName" -> "test";
                    case "accessPolicy" -> EncounterAccessPolicy.PUBLIC;
                    case "spectatorPolicy" -> SpectatorPolicy.ALLOW_VIEW_ONLY;
                    case "maxParticipants" -> 0;
                    case "maxSpectators" -> 0;
                    default -> null;
                }));
    }
}

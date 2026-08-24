package io.github.legendaryforge.legendary.core.internal.runtime;

import io.github.legendaryforge.legendary.core.api.activation.ActivationInput;
import io.github.legendaryforge.legendary.core.api.activation.ActivationInputResolver;
import io.github.legendaryforge.legendary.core.api.activation.ActivationService;
import io.github.legendaryforge.legendary.core.api.activation.session.ActivationSessionService;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterManager;
import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.identity.PartyDirectory;
import io.github.legendaryforge.legendary.core.api.identity.PlayerDirectory;
import io.github.legendaryforge.legendary.core.api.legendary.access.DefaultLegendaryAccessPolicy;
import io.github.legendaryforge.legendary.core.api.lifecycle.Lifecycle;
import io.github.legendaryforge.legendary.core.api.lifecycle.ServiceRegistry;
import io.github.legendaryforge.legendary.core.api.platform.CoreRuntime;
import io.github.legendaryforge.legendary.core.api.registry.RegistryAccess;
import io.github.legendaryforge.legendary.core.internal.activation.DefaultActivationService;
import io.github.legendaryforge.legendary.core.internal.activation.session.DefaultActivationSessionService;
import io.github.legendaryforge.legendary.core.internal.encounter.DefaultEncounterManager;
import io.github.legendaryforge.legendary.core.internal.encounter.lifecycle.EncounterDurationTelemetry;
import io.github.legendaryforge.legendary.core.internal.event.SimpleEventBus;
import io.github.legendaryforge.legendary.core.internal.gate.DefaultGateService;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.ArenaInvariantBridge;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.ArenaInvariantRegistry;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.ArenaRevocationController;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.ArenaRevocationTracker;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.BoundsInvariant;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.LegendaryInstanceTrackingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.LegendaryRevokedRejoinEnforcingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.arena.PhaseGateInvariant;
import io.github.legendaryforge.legendary.core.internal.legendary.manager.LegendaryAccessEnforcingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.legendary.penalty.NoopLegendaryPenaltyStatus;
import io.github.legendaryforge.legendary.core.internal.legendary.penalty.RevocationPenaltyBridge;
import io.github.legendaryforge.legendary.core.internal.legendary.start.DefaultLegendaryStartPolicy;
import io.github.legendaryforge.legendary.core.internal.legendary.start.LegendaryStartGatingEncounterManager;
import io.github.legendaryforge.legendary.core.internal.lifecycle.DefaultLifecycle;
import io.github.legendaryforge.legendary.core.internal.lifecycle.DefaultServiceRegistry;
import io.github.legendaryforge.legendary.core.internal.registry.DefaultRegistryAccess;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCoreRuntime implements CoreRuntime {

    private final RegistryAccess registries;
    private final Lifecycle lifecycle;
    private final ServiceRegistry services;
    private final EventBus events;
    private final EncounterManager encounters;

    private final Clock clock;

    private final Optional<PlayerDirectory> players;
    private final Optional<PartyDirectory> parties;

    public DefaultCoreRuntime() {
        this(Optional.empty(), Optional.empty(), Clock.systemUTC());
    }

    public DefaultCoreRuntime(Clock clock) {
        this(Optional.empty(), Optional.empty(), clock);
    }

    public DefaultCoreRuntime(Optional<PlayerDirectory> players, Optional<PartyDirectory> parties) {
        this(players, parties, Clock.systemUTC());
    }

    public DefaultCoreRuntime(Optional<PlayerDirectory> players, Optional<PartyDirectory> parties, Clock clock) {
        this.registries = new DefaultRegistryAccess();

        DefaultLifecycle lifecycle = new DefaultLifecycle();
        this.lifecycle = lifecycle;

        this.services = new DefaultServiceRegistry(lifecycle);

        EventBus bus = new SimpleEventBus();
        this.events = bus;

        this.clock = Objects.requireNonNull(clock, "clock");

        EncounterDurationTelemetry durationTelemetry = new EncounterDurationTelemetry(bus, clock);
        bus.subscribe(
                io.github.legendaryforge.legendary.core.api.encounter.event.EncounterStartedEvent.class,
                durationTelemetry::onStarted);
        bus.subscribe(
                io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent.class,
                durationTelemetry::onEnded);

        Set<java.util.UUID> legendaryInstanceIds = ConcurrentHashMap.newKeySet();
        PhaseGateInvariant phaseGate = new PhaseGateInvariant();
        BoundsInvariant bounds = new BoundsInvariant(bus);

        ArenaInvariantRegistry arenaRegistry = definitionId -> {
            Objects.requireNonNull(definitionId, "definitionId");
            return java.util.List.of(phaseGate, bounds);
        };

        ArenaInvariantBridge.bind(bus, arenaRegistry, legendaryInstanceIds::contains, legendaryInstanceIds::remove);

        ArenaRevocationTracker revocations = new ArenaRevocationTracker();

        ArenaRevocationController revocationController =
                new ArenaRevocationController(revocations, bus, legendaryInstanceIds::contains);
        this.services.register(ArenaRevocationController.class, revocationController);

        RevocationPenaltyBridge penaltyBridge = new RevocationPenaltyBridge(bus);
        this.services.register(RevocationPenaltyBridge.class, penaltyBridge);

        GateService gates = new DefaultGateService();
        this.services.register(GateService.class, gates);

        ActivationSessionService sessions = new DefaultActivationSessionService();
        this.services.register(ActivationSessionService.class, sessions);

        ActivationInputResolver activationInputs = request -> new ActivationInput(
                request.activatorId(), request.activationGateKey().orElseThrow(), Map.of(), request.targetRef());

        ActivationService activations = new DefaultActivationService(gates, sessions, activationInputs);
        this.services.register(ActivationService.class, activations);

        bus.subscribe(
                io.github.legendaryforge.legendary.core.api.encounter.event.EncounterEndedEvent.class,
                e -> revocations.clearInstance(e.instanceId()));

        EncounterManager base = new DefaultEncounterManager(players, parties, Optional.of(bus));
        EncounterManager startGated = new LegendaryStartGatingEncounterManager(
                base, new DefaultLegendaryStartPolicy(), new NoopLegendaryPenaltyStatus());
        EncounterManager enforced =
                new LegendaryAccessEnforcingEncounterManager(startGated, new DefaultLegendaryAccessPolicy());
        EncounterManager revokedRejoinEnforced = new LegendaryRevokedRejoinEnforcingEncounterManager(
                enforced, legendaryInstanceIds::contains, phaseGate, revocations);
        this.encounters = new LegendaryInstanceTrackingEncounterManager(revokedRejoinEnforced, legendaryInstanceIds);

        this.players = Objects.requireNonNull(players, "players");
        this.parties = Objects.requireNonNull(parties, "parties");
    }

    @Override
    public RegistryAccess registries() {
        return registries;
    }

    @Override
    public Lifecycle lifecycle() {
        return lifecycle;
    }

    @Override
    public ServiceRegistry services() {
        return services;
    }

    @Override
    public EventBus events() {
        return events;
    }

    @Override
    public EncounterManager encounters() {
        return encounters;
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public Optional<PlayerDirectory> players() {
        return players;
    }

    @Override
    public Optional<PartyDirectory> parties() {
        return parties;
    }
}

# Adopting the questline framework in `mod/hytale`

**Status:** specified, not executed, and **now blocked on a prior decision.** Written
2026-08-24 alongside the module boundary realignment.

> **Read this before executing P1–P3.** On 2026-08-25 it was confirmed by running a server that
> Hytale ships an asset-driven questline system a mod can contribute to as JSON — objectives,
> chaining, weather/time-gated triggers, rewards, and a persistent per-player
> `ObjectiveHistoryComponent`. See `docs/integration/hytale-asset-packs.md`.
>
> That system does **not** replace what this document describes: `core`'s framework answers "how do
> N questlines register into our plugin", the native one answers "what a quest is and how progress
> persists". But the second is the half `quests/stormseeker` currently hand-rolls, and adopting it
> would materially shrink what the SPI has left to carry — possibly below the point where P1–P3 are
> worth doing at all.
>
> P1–P3 are three real code changes that activate dormant paths in the module with the least test
> coverage. **Settle the native-objectives question first**, or risk writing them against a spine
> that is about to be replaced.

## What exists

`core` owns a questline framework: `QuestlineModule` (the SPI a questline
implements), `QuestlineRegistry` (what a server ships), `LegendaryConfig`
(per-questline enable/disable), and `LegendaryWiring` (the aggregator that
walks the registry and calls each enabled questline's register hooks).

`quests/stormseeker` implements the SPI as `StormseekerQuestline`, delegating
to `StormseekerWiring`.

## What does not exist

**`mod/hytale` does not use any of it.** `LegendaryHytalePlugin.start()` wires
Stormseeker imperatively: it constructs `HytaleStormseekerHost`, registers a
`StormseekerTickSystem` with the engine, subscribes two player events, and
registers three commands. The framework is exercised only by two tests in
`quests/stormseeker/src/test/.../stormseeker/harness/`.

So "add questline #2 by registering it" is **not** true today. Adding one
means hand-editing the plugin the way Stormseeker is hand-wired.

## Why it was not migrated during the realignment

Reading `StormseekerWiring` showed the framework's Stormseeker implementation
is scaffold in three places:

1. `registerSystems(LegendarySystemRegistrar)` is an explicit no-op —
   `// Intentionally no-op in Phase C scaffold.` The tick system that actually
   drives Phases 1, 1.5 and 2 (via `StormseekerWiring.tick(host)`) has no path
   through the framework.
2. `registerListeners(EventBus)` is dormant. Nothing calls it. Calling it would
   construct `StormseekerAttunementService(bus, null)` and
   `StormseekerTrekSystem(bus, null)` — `null` where the comments say a
   host-provided world belongs — and subscribe `StormseekerLifecycleBridge` to
   live `EncounterStartedEvent`s for the first time.
3. The `AttunementCompleteEvent` handler body is a comment. The Phase 3 →
   Phase 4 handshake is unimplemented.

Migrating would have wired a coherent framework to a half-built implementation
and activated dormant paths in the only module with no test coverage.

## Preconditions

All three must hold before executing the migration below. Each is a real code
change, not a review:

- **P1.** `StormseekerWiring.registerSystems` registers the tick system, so the
  engine's ECS registration flows through `LegendarySystemRegistrar` instead of
  a direct `getEntityStoreRegistry().registerSystem(...)` call in the plugin.
- **P2.** The two `null` world arguments in `registerListeners` are resolved —
  either a real host-provided world reaches them, or the services are
  restructured so the argument is not needed.
- **P3.** The `AttunementCompleteEvent` handler has a body, or is removed if the
  handshake belongs elsewhere.

P1–P3 are Project D (roadmap re-scope) work, not refactoring.

## The migration, once preconditions hold

In `LegendaryHytalePlugin.start()`, before the existing Stormseeker
construction:

```java
CoreRuntime runtime = new DefaultCoreRuntime();
QuestlineRegistry questlines = new QuestlineRegistry().register(new StormseekerQuestline());
LegendaryConfig config = LegendaryConfig.enablingAll(questlines);

LegendaryWiring.registerAllGates(questlines, runtime.services().require(GateService.class), config);
LegendaryWiring.registerAllListeners(questlines, runtime.events(), config);
LegendaryWiring.registerAllSystems(questlines, system -> getEntityStoreRegistry().registerSystem(system), config);
```

`services().require(...)` is used rather than `get(...)` because `get` returns `Optional<T>` whereas `registerAllGates` expects `GateService` directly; `require` throws if the service is not registered, which is the correct behavior at boot time.

`LegendarySystemRegistrar.register` takes `Object` precisely so the host adapts
it to the engine's ECS type — that lambda is the adapter, and it is why no
Hytale implementation of `core.api.platform.CoreRuntime` is required for this
step. Confirm the `registerSystem` overload accepts what
`StormseekerWiring.registerSystems` passes; cast at the lambda if not.

Then delete from the plugin whatever P1 moved behind `registerSystems`, keeping
the host, the progress store, the player-event subscriptions and the three
commands — those are Hytale adapters, not questline registration, and they stay.

## How to tell it worked

Adding questline #2 should require: a new `quests/<name>/` module, one
`include` in `settings.gradle.kts`, one `api(project(":quests:<name>"))` line
in `mod/hytale/build.gradle.kts` (`mod/hytale` reaches Stormseeker only
through that line today, so a second questline needs its own), its Hytale
adapters under `mod/hytale/.../hytale/<name>/`, and **one
`.register(new <Name>Questline())` line**. If it requires anything else in
`LegendaryHytalePlugin.start()`, the migration is incomplete.

## Related

- `:core:checkNoQuestlineImports` keeps `core` from naming a questline. It does
  not and cannot check that the plugin uses the framework — that is what this
  document is for.
- `mod/hytale` has no tests. Executing this migration changes plugin boot
  behavior with no automated check, so it should be verified against a running
  server.

# Adopting the questline framework in `mod/hytale`

**Status: SUPERSEDED 2026-08-25. Do not execute P1–P3.**

> The native-objectives question this document was blocked on has been **decided**: Hytale's
> objectives system is adopted for the Stormseeker content spine. See
> `docs/architecture/native-objectives-migration-cost.md`.
>
> **P1–P3 below are discarded, not pending.** They are preconditions for migrating a *hand-rolled
> content spine* — a tick system driving phase state, a dormant listener path, an
> `AttunementCompleteEvent` handshake — and that spine will no longer exist. Executing them would
> mean building the thing being replaced. They are left in place unedited as a record of a path not
> taken, because the *reasons* they were not executed (§"Why it was not migrated") turned out to be
> early evidence for the decision that superseded them.
>
> **What survives from this document:** the description of what `core`'s questline framework *is*
> (§"What exists"), the observation that `mod/hytale` never adopted it, and the success test in
> §"How to tell it worked" — which still holds, with a different mechanism. Adding questline #2
> should require one registration, not a plugin rewrite; it is now JSON assets plus a couple of
> codec registrations rather than one `.register(new <Name>Questline())` line.
>
> **What `core`'s questline SPI becomes:** "what Java does a questline register" — custom task
> types, conditions, completions and ECS systems. Re-specifying it is open work. Deleting it
> outright was considered and not chosen.

Written 2026-08-24 alongside the module boundary realignment.

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
   drives Phases 2, 3 and 4 (via `StormseekerWiring.tick(host)`) has no path
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

> **Discarded 2026-08-25** — see the status banner at the head of this document. Retained as record.

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

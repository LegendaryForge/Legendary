# Per-player weather override port — `WeatherGate` + `PlayerWeatherController`

**Rank 1 of the [0.7.2 capability assessment](2026-10-20-hytale-0.7.2-capability-assessment-design.md)
§6.** Gate satisfied: `/weatheroverrideprobe` passed 23/23 against a live client (§5.1).

This is the first port of that assessment to be built. It is also the repo's first
`core`-interface/`mod/hytale`-adapter pair, so it sets a precedent the later ranks will copy.

---

## 1. Scope

**In:** a read side (`WeatherGate`) and a write side (`PlayerWeatherController`) in `core`, one
adapter in `mod/hytale`, and migration of the existing weather readers onto the port.

**Out, deliberately:** claim priorities, claim queuing, persistence of claims across restart,
weather *forcing* (world-global, already reachable), and anything depending on §4's undecided
choice between additive reveal and per-player spoof.

**Why migration is in scope.** §5.1 says "Deletes. Nothing yet." Taken literally that ships a
framework with no consumer, which this project has already done once: the questline framework in
`core` is correctly designed and its only callers repo-wide are its own tests. `CLAUDE.md` states
the lesson — *"If the only callers are its own tests, it is a design, not a mechanism."* So rank 1
ships with a live call site.

The migration also fixes a real defect. `HytaleWeatherReader` reads
`WeatherTracker.getWeatherIndex()`, which §5.1 measured to be a **send-log, not a resolver** — it
returns the last value sent to the client, so a read immediately after a claim returns the
*pre-claim* value. Every current caller is reading a stale field.

---

## 2. Why the logic lives in `core`

`mod/hytale` compiles **nothing** on CI — runners cannot hold the proprietary jar, so the module is
`EXEMPT` on every CI run — and it has no tests. Any rule placed there is a rule no gate can check.

This is not a new judgement. `HytaleStormseekerHost` already carries it as a comment:

> *"The delta/threshold arithmetic lives in `PositionMotionTracker` over in `:quests:stormseeker`.
> It touches no platform type, and keeping it here meant it could not be compiled or tested
> anywhere without the game jar."*

So: **every decision in `core`, the adapter is a translator.** Ownership arbitration, sentinel
rejection, refusal rules and weather classification are all testable; the adapter only reads and
writes engine state.

### Reuse across questlines

`mod/hytale` is the platform adapter layer, not a questline. The mechanism is written once and
consumed by any number of questlines:

```
core                frameworks, engine-agnostic      <- WeatherGate, PlayerWeatherController
quests/stormseeker  questline #1, uses core only     <- consumer
quests/<next>       questline #2, uses core only     <- same ports, nothing new in the adapter
mod/hytale          the ONLY com.hypixel.* module    <- one HytaleWeatherBackend, shared
harness             consumer-side contract tests
```

**Package correction.** `HytaleWeatherReader` currently sits at `mod/hytale/.../mod/hytale/stormseeker/`
— a questline-specific package holding a generic mechanism. The new adapter goes to
`mod/hytale/.../mod/hytale/weather/` so questline #2 does not inherit a dependency on a package
named for a different questline. This is the 2026-08-24 module-boundary cleanup inverted, and
moving it later would be a cross-module rename.

---

## 3. Core speaks weather ids, never indices

A weather index is an asset-map ordinal. This project has been bitten twice by the engine
renumbering or relocating things beneath it — `Vector3d` moving to `org.joml` undetected for ~6
months, and the launcher jumping to a Java 25 jar. An `int` held in `core` is an ordinal carried
across game updates with no way to notice it shifted.

So `WeatherId` wraps the engine's stable string id (`Zone1_Rain`), and **the ordinal never leaves
the adapter.** The `0` sentinel is reported as a resolution *status*, not as a number.

---

## 4. Components

### `core/api/weather/`

| Type | Shape | Notes |
|---|---|---|
| `WeatherId` | `record WeatherId(String value)` | non-blank; wraps `Weather.getId()` |
| `OverrideOwner` | `record OverrideOwner(ResourceId id)` | value equality — this is what makes two of our subsystems distinguishable |
| `WeatherGate` | interface | `effective(PlayerRef)`, `unoverridden(PlayerRef)`, both `Optional<WeatherId>` |
| `PlayerWeatherController` | interface | `claim`, `release`, `holder`, `forget` |
| `WeatherClaimResult` / `WeatherClaimStatus` | record + enum | follows `ActivationAttemptResult`/`Status` and `GateDecision` precedent, not a bare boolean |
| `WeatherKinds` | static predicates | `isStorm(WeatherId)`, `isThunderStorm(WeatherId)` |
| `WeatherResolution` | `enum { OK, UNKNOWN, SENTINEL }` | how the adapter reports an id's resolution without exposing the ordinal |
| `WeatherBackend` | interface, host-implemented | SPI; `api` is where host-implemented interfaces already live (`ActivePlayerProvider`) |

`WeatherKinds` exists because those predicates are today string-`contains` heuristics inside
`mod/hytale`, where nothing can test them. Moving them to `core` makes them tested without changing
behaviour.

### `core/internal/weather/`

| Type | Notes |
|---|---|
| `OverrideRegistry` | owner **and** claimed weather per subject; shared by the gate and the controller |
| `DefaultWeatherGate` | `effective` = registry's claim if held, else the backend's unoverridden |
| `DefaultPlayerWeatherController` | the only writer to the registry; owns every rule below |

The registry is shared because the engine has no override getter — see §6. Without it the gate
cannot answer `effective()` at all.

### `mod/hytale/.../mod/hytale/weather/`

| Type | Notes |
|---|---|
| `HytaleWeatherBackend implements WeatherBackend` | the only platform code; absorbs `HytaleWeatherReader` |

Wiring is imperative in `LegendaryHytalePlugin.start()`, matching how Stormseeker is wired today.

### `PlayerRef` is two different types — every signature here means core's

Two unrelated types carry this name, and conflating them is the easiest way to get this wrong:

| Type | Module | Shape |
|---|---|---|
| `core.api.questline.runtime.PlayerRef` | `core` | `record PlayerRef(String id)` — opaque, platform-free |
| `com.hypixel.hytale.server.core.universe.PlayerRef` | engine | the live entity handle |

**Every signature in this document means core's.** `core` cannot name the engine type at all —
`:core:checkNoPlatformImports` fails the build on any `com.hypixel.` import under `core/src`.

So the adapter must resolve `id -> engine PlayerRef` to reach a `WeatherTracker` or a claimant
`Ref`. `HytaleStormseekerHost` already keeps such a map, but it is Stormseeker-scoped state and a
questline-agnostic backend must not depend on it. `HytaleWeatherBackend` therefore keeps its own
`Map<String, PlayerRef>`, maintained from the plugin's join/leave path — **the same hook that calls
`forget`**, so the two lifecycles cannot drift apart. An unknown id resolves to
`hasTracker == false`, which surfaces as `SUBJECT_UNAVAILABLE` rather than a null dereference.

---

## 5. Claim semantics

`OverrideOwner` is keyed by value. The registry holds **one owner per subject**, because the engine
supports one override per player.

`claim(subject, weatherId, owner)` resolves in order:

| Condition | Status |
|---|---|
| subject has no `WeatherTracker` | `SUBJECT_UNAVAILABLE` |
| weather id unknown to the backend | `UNKNOWN_WEATHER` |
| id resolves to the `0` sentinel | `UNSUPPORTED_WEATHER` |
| no current owner | `GRANTED` |
| current owner equals `owner` | `UPDATED` |
| current owner differs | `REFUSED_HELD` (names the holder) |

`release(subject, owner)` refuses anyone but the recorded owner.

### Why arbitration cannot be left to the engine

Measured in §5.1: `tryClaimOverride(Ref claimant, int)` compares owners by **reference identity**
(`if_acmpeq`), and the claimant `Ref` is arbitrary — distinct from the subject. Our subsystems do
not own entity `Ref`s, so the natural implementation passes the player's own `Ref` as claimant
everywhere. The probe measured what that yields: **a same-claimant re-claim is a silent update, not
a refusal.** Two of our own systems gating one player would overwrite each other with no error.

Our owner identity is therefore finer than the engine's, and both the refusal and the
foreign-release check must happen on our side before the engine sees the call.

### Three rules the probe forced

1. **The sentinel must be rejected before the engine sees it.** `tryClaimOverride(ref, 0)` returns
   **true and does nothing**, so without the check a caller receives a success it did not get.
2. **`setOverrideWeatherIndex` / `clearOverrideWeatherIndex` are never exposed.** Both are public on
   the engine type and perform **no ownership check at all** — measured: an unclaimed caller set an
   override and a non-owner cleared one. They appear only behind `WeatherBackend`, which is internal
   to the port.
3. **Reads never call `getWeatherIndex()`.** It is a send-log. The read side uses
   `getEffectiveWeatherIndex` / `resolveUnoverriddenWeatherIndex`.

### `forget`, and why it is not optional

The engine self-heals a stranded override when the claimant `Ref` goes invalid. Our registry is a
plain map and will not notice a disconnect, so it leaks one owner per departed player and the next
claim against a recycled player id is wrongly refused. `forget(subject)` is called from the same
leave path that already drives `HytaleStormseekerHost.removePlayer`.

### What arbitration is not

It does not touch weather precedence. **Override > world-forced > environment** is the engine's,
measured, with release falling back to the *force* rather than the environment. Ours only decides
who may set the override.

---

## 6. `WeatherBackend` contract

```java
boolean hasTracker(PlayerRef subject);
Optional<WeatherId> unoverriddenWeather(PlayerRef subject);
WeatherResolution resolve(WeatherId weather);   // OK | UNKNOWN | SENTINEL
boolean applyOverride(PlayerRef subject, WeatherId weather);
boolean clearOverride(PlayerRef subject);
void onTick(Object worldStore);                 // adapter-typed; see below
```

`resolve` is what keeps the ordinal inside the adapter while still letting `core` reject the
sentinel. No method on this interface mentions an index.

### `effectiveWeather` was removed, because it cannot be implemented

**This interface originally carried `effectiveWeather(PlayerRef)`. It was removed 2026-10-20 after
`javap` and `/weatherportprobe` established that the adapter cannot answer it.**

`WeatherTracker` exposes `setOverrideWeatherIndex` and `clearOverrideWeatherIndex` but **no
getter**. The only readable per-player value is `getWeatherIndex()` — the send-log this port is
already forbidden from using. `WeatherResource.getEffectiveWeatherIndex(int)` exists but is
*world* effective weather (forced-or-environment), not per-player; that matches §5.1's bytecode
note and does not help here.

So **`WeatherGate.effective()` is computed by `core`**: the claimed `WeatherId` from the port's own
registry if one is held, otherwise `unoverriddenWeather` from the backend. This is sound because
the port owns every legitimate claim — it is blind only to a caller using the engine's unchecked
setters directly, which is exactly what the port exists to prevent.

**Consequence: the gate and the controller are not independent.** They share an `OverrideRegistry`
holding owner *and* claimed weather per subject. The engine's missing getter makes `core`-side
bookkeeping load-bearing for **reads**, not only for refusals — which strengthens the §2 argument
for keeping arbitration in `core` rather than weakening it.

### Why the SPI has `onTick`

`resolveUnoverriddenWeatherIndex` needs a `WeatherResource`, a `TransformComponent` and a
`ComponentAccessor<EntityStore>` — all reachable only from a `Store<EntityStore>`. **`PluginBase`
and `JavaPlugin` expose no route to one**; `TickingSystem.tick(float, int, Store)` does. So the
adapter is fed its store from the tick system and caches it.

Measured by `/weatherportprobe` before being designed in: the `Store` is the **same instance**
across invocations, still answers `getResource`, and the `WeatherResource` beside it is stable too.
Verified within one boot and across two invocations; **not** verified across a world unload, which
is the remaining way this could bite.

`onTick` takes `Object` rather than `Store<EntityStore>` because `core` cannot name the engine type
(`checkNoPlatformImports`). The adapter casts. This is the one place the port pays for the boundary,
and it is confined to a single method the only implementer already types correctly.

---

## 7. Testing

**`core` unit tests** (`core/src/test/.../internal/weather/`) against a `FakeWeatherBackend`:

- all six `claim` statuses, each in isolation
- same-owner re-claim returns `UPDATED` and changes the weather
- different-owner claim returns `REFUSED_HELD` and leaves the held weather untouched
- foreign `release` refused; owner `release` restores
- `forget` clears the registry and permits a subsequent claim
- `WeatherKinds` predicates, including the negative cases the current heuristics never tested

**`harness`** gets a consumer-side contract test asserting the port pair's invariants — that is the
module's stated purpose, and it is where a future questline's expectations belong.

**`mod/hytale`** gets no tests; it cannot have them. The adapter is kept thin enough that this
costs nothing, and `checkModuleCoverage` continues to report it `EXEMPT` by declaration.

**Verification:** `./gradlew build` with all seven guards, then `CENSUS_VERDICT` and
`COVERAGE_VERDICT` read **by name, not position**. On this machine read `0 exempt` rather than the
census — `CENSUS_VERDICT` is structurally blind to a compile failure in `mod/hytale`, which has no
tests.

**Live re-verification:** `/weatheroverrideprobe` already exists and passed 23/23. Re-run it after
the port lands to confirm the engine still behaves as the port assumes. The probe validates the
engine; `core`'s tests validate arbitration. Neither substitutes for the other.

---

## 8. Measured facts this design rests on

All from `/weatheroverrideprobe`, 2026-10-20, live 0.7.2 client-attached, recorded in capability
assessment §5.1:

| Fact | Consequence here |
|---|---|
| precedence is per-player override > world-forced > environment | §5 "what arbitration is not" |
| owner comparison is reference identity, claimant is arbitrary | arbitration must be ours (§5) |
| same-claimant re-claim succeeds as an update | `UPDATED` status exists |
| `set`/`clearOverrideWeatherIndex` perform no ownership check | never exposed (§5 rule 2) |
| index `0` is a sentinel; claiming it returns true and no-ops | `UNSUPPORTED_WEATHER` (§5 rule 1) |
| `getWeatherIndex()` is a send-log | read side forbidden from it (§5 rule 3) |
| `PlayerRef.getReference()` is a cached, stable instance | adapter may re-derive the claimant `Ref` |
| a stale owner cannot strand an override | `forget` covers only *our* registry, not the engine's |

Added 2026-10-20 from `javap` plus `/weatherportprobe`, after three guessed signatures in an
earlier draft of this document were all wrong:

| Fact | Consequence here |
|---|---|
| `WeatherTracker` has **no** override getter | `effectiveWeather` removed from the SPI; the gate computes it (§6) |
| `getEffectiveWeatherIndex(int)` is on `WeatherResource` and is world-scoped | does not serve a per-player read |
| `resolveUnoverriddenWeatherIndex(WeatherResource, TransformComponent, ComponentAccessor)` | the adapter needs a `Store`, hence `onTick` (§6) |
| `WeatherTracker.getEnvironmentId()` — not `getEnvironmentIndex()` | corrected call name |
| `PluginBase`/`JavaPlugin` expose no `Store` | the store must come from the tick system |
| `Store` and `WeatherResource` are the same instance across invocations | the adapter may cache both |
| `IndexedLookupTableAssetMap.getIndex(missing)` returns `Integer.MIN_VALUE` | `UNKNOWN` is distinguishable from `SENTINEL`, so `resolve` works as designed |
| index 0 holds a real asset whose id is `"Unknown"`, and it round-trips | a caller passing `WeatherId("Unknown")` correctly resolves to `SENTINEL` |
| `getIndex(K)` exists | no linear scan over the asset map |

**Still unverified, and it needs a client:** that no public read path returns a claimed override
index, that `resolveUnoverriddenWeatherIndex` is unaffected by an active override, and that the
unoverridden reading moves when world weather is forced (its own instrument check).
`/weatherportprobe` test 3 covers all three and reports `SKIP` headless — which is no run, not a
degraded one.

---

## 9. Open questions

1. **Does `WeatherGate` need a "would be" read for a subject with no tracker?** Currently
   `Optional.empty()` conflates "no tracker" with "no weather". The controller distinguishes them
   via `SUBJECT_UNAVAILABLE`; the gate does not. Left as-is until a consumer needs the difference.
2. ~~**Should `harness` own the fake backend?**~~ **RESOLVED 2026-10-20 by precedent:** yes. Test
   sources are not shared between modules here, and `harness` already owns
   `RecordingStormseekerHostRuntime`. `core` keeps a `FakeWeatherBackend` for unit tests; `harness`
   keeps a `RecordingWeatherBackend` that also records call order, which a consumer-side test wants.
   No `java-test-fixtures` plugin is introduced.
4. **Does a cached `Store` survive a world unload/reload?** `/weatherportprobe` test 2 measured
   instance stability across invocations within one boot, which is what the adapter needs
   moment-to-moment, but not across a world lifecycle event. If a world can be unloaded while the
   plugin lives, the cache needs invalidation. Unknown whether this server ever does that.
3. **§4 remains the operator's decision** and is unaffected by this port, but whichever mechanism
   wins will consume `WeatherGate`. If per-player spoof is chosen, the spoof's re-send policy and
   this port's claim lifecycle both key on the same player-leave hook.

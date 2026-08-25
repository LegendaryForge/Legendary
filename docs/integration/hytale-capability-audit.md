# Hytale Engine Capability Audit

> **Build audited:** Server `0.5.9`, patchline `release`, revision `214c57c5a63e6e5d51ed8be4c8a508dfcc177d16`,
> `Java-Version: 25`, jar mtime 2026-08-17. 118 MB, 39,618 entries, 8,728 `com.hypixel.*` classes.
> **Audited:** 2026-08-24. **Method:** `javap` signature inspection of the installed
> `HytaleServer.jar` plus structural survey of `Assets.zip` (60,150 files, 3.2 GB).
>
> **This document expires.** It describes one build of a game in Early Access that updates every
> 2–6 weeks. Re-run it after any launcher update before relying on a row below. To confirm which
> build you are on: `unzip -p <HytaleServer.jar> META-INF/MANIFEST.MF | grep Implementation-Version`.
>
> **Supersedes** the API-status section of `phase-0-1-redesign-final.md`, which audited build
> `2026.02.06-aa1b071c2` and lived only in the archived `LegendaryHytale` repository. Hytale
> changed versioning scheme between that build and this one; treat the February audit as history.

---

## Why this document exists

The v2.0 Stormseeker design was built on a **movement restriction mechanic** and had to be
scrapped when a February API audit found movement speed modification was not exposed to plugins.
The replacement design (v3.0) was written against that same audit, whose own
*Implementation Priority #1* was an **"API testing sprint"** that never happened. Five of its
mechanics therefore rest on capabilities recorded as "found but untested" or "not found".

Every one of those five is resolved below. **Four were wrong.**

---

## 1. The February unknowns, resolved

| Capability | Feb 2026 verdict | 0.5.9 verdict | Evidence in the jar |
|---|---|---|---|
| Entity registration + spawn | found, untested | **Confirmed, first-class** | `PluginBase.getEntityRegistry()`; `EntityRegistry.registerEntity(String, Class<T>, Function<World,T>, DirectDecodeCodec<T>)` |
| DynamicLight | found, untested | **Confirmed, as an ECS component** | `modules.entity.component.DynamicLight`, `PersistentDynamicLight`, `entity.dynamiclight.DynamicLightSystems` |
| Block state read/write | likely, untested | **Confirmed, and richer than assumed** | `world.accessor.BlockAccessor`: `getBlock`/`setBlock`(8 overloads)/`breakBlock`/`placeBlock`/`testPlaceBlock`/`getBlockType`, **plus `getBlockComponentHolder(x,y,z)` — blocks carry ECS components** |
| Particle spawning | **not found** | **Wrong — a full asset system exists** | `asset.type.particle.config.{Particle, ParticleSystem, ParticleSpawner, ParticleSpawnerGroup, ParticleAttractor, ParticleCollision, WorldParticle}`; 1,743 `.particlespawner` + 598 `.particlesystem` assets ship with the game |
| Sound / audio playback | unknown | **Wrong — confirmed** | `modules.entity.component.AudioComponent`, `MovementAudioComponent`, `entity.system.AudioSystems`; asset types `soundevent`, `audiocategory`, `blocksound`, `itemsound`; 4,243 `.ogg` files ship |
| Camera shake | found, untested | **Confirmed, first-class** | `builtin.adventure.camera.asset.camerashake.CameraShake`, `cameraeffect.CameraShakeEffect`, `ShakeIntensity`, `CameraShakeInteraction`, `CameraEffectSystem` |
| Debug shapes | found, untested | **Confirmed at protocol level** | `protocol.DebugShape`, `packets.player.ClearDebugShapes`; `DebugShape{Sphere,Cube,Cylinder,Arrow,Cone,Clear}Command` |
| Navigation / pathfinding | **not found** → planned a "lateral-nudge" fallback | **Wrong — full A\* exists** | `server.npc.navigation.{AStarBase, AStarWithTarget, AStarNode, AStarNodePool, AStarEvaluator, PathFollower, IWaypoint}` |
| Movement speed modification | not exposed — **killed the v2.0 design** | **Still no speed scalar. But binary directional control exists.** | `asset.modifiers.MovementEffects` fields: `disableAll`, `disableForward`, `disableBackward`, `disableLeft`, `disableRight`, `disableSprint`, `disableJump`, `disableCrouch`; `NetworkSerializable<protocol.MovementEffects>`. Also `TriggerVolume` `SetVelocityEffect`. |

**Read the last row carefully.** February's finding was *narrowly* correct and *broadly* misleading.
There is still no speed multiplier, so v2.0's gradient "resistance" is still not expressible. But
per-direction movement *disabling* is, and so is setting velocity outright. A design that wants
"the storm will not let you walk away" has two mechanisms available; only the soft-gradient
version is absent.

---

## 2. What a plugin actually gets

`PluginBase` (the superclass of `JavaPlugin`) exposes these registries. Marked ✅ where
`mod/hytale` already uses them:

| Registry | Purpose | Used by us |
|---|---|---|
| `CommandRegistry` | slash commands | ✅ |
| `EventRegistry` | global event subscription | ✅ |
| `ComponentRegistryProxy<EntityStore>` | ECS components on entities | ✅ |
| `EntityRegistry` | **register custom entity types** | ✗ |
| `AssetRegistry` | **`register(AssetStore)` — ship your own assets** | ✗ |
| `ComponentRegistryProxy<ChunkStore>` | ECS components on chunks/blocks | ✗ |
| `TaskRegistry` | scheduled tasks | ✗ |
| `ClientFeatureRegistry` | `register(ClientFeature)`, `registerClientTag(String)` | ✗ |
| `getCodecRegistry(...)` ×3 | register codecs for assets / JSON assets / map keys | ✗ |
| `withConfig(BuilderCodec)` | plugin config files | ✗ |

Lifecycle is `preLoad()` → `setup()` → `start()` → `shutdown()`.
**`LegendaryHytalePlugin` overrides only `start()` and `shutdown()`.**

There is also an **early-plugin** tier with bytecode transformation —
`plugin.early.{EarlyPluginLoader, TransformingClassLoader, ClassTransformer}` — a deeper hook than
`JavaPlugin`. Not needed for this project, but it exists.

---

## 3. The architecture that explains everything above

**The client is a native binary, not Java.** `Client/HytaleClient` is a 49 MB native executable
linked against Noesis (XAML UI), OpenAL, SDL3, Opus/Ogg, and quiche/msquic (QUIC). There is no
client-side Java modding surface.

This is why February's audit came back "not found" for particles, audio and shaders: it was
looking for a client API. What exists instead is a **server-authoritative, asset-and-packet
architecture** — note the recurring `*PacketGenerator` class beside each asset type
(`BlockParticleSetPacketGenerator`, `AudioCategoryPacketGenerator`, `CameraShakePacketGenerator`,
`ViewBobbingPacketGenerator`, …). The server owns definitions and pushes them to the client.

So mod capability is not "what can I call on the client" but:

> **what asset types can I define, and what packets can the server send**

Both are open to plugins: `AssetRegistry.register(AssetStore)` for the first, and the protocol
package (900 classes) for the second. Hypixel Studios has also stated it intends to publish the
full server source within 1–2 months of EA launch.

---

## 4. Two systems that did not exist in February

### 4.1 Native objectives — Hytale has a quest framework

`com.hypixel.hytale.builtin.adventure.objectives` (+ `npcobjectives`) is a complete,
asset-driven quest system. This is the single most consequential finding in this audit and it is
covered in depth in `stormseeker-canon-alignment.md`; the capability facts are:

- **`ObjectiveLineAsset`** — `id`, `category`, `objectiveIds[]`, title/description keys,
  **`nextObjectiveLineIds[]`**. An objective *line* is a questline, and lines chain.
- **`ObjectiveAsset`** — `id`, `category`, `taskSets[]`, `completionHandlers[]`, title/description
  keys, `removeOnItemDrop`.
- **Task types (10):** `Bounty`, `Craft`, `Gather`, `KillNPC`, `KillSpawnBeacon`,
  `KillSpawnMarker`, `ReachLocation`, `TreasureMap`, `UseBlock`, `UseEntity`. *(Corrected
  2026-08-25 from a twelve-entry list that included `Count` and `Kill`. Neither is an accepted
  `"Type"`: `CountObjectiveTaskAsset` is an abstract base whose `Count` field appears inside
  `Gather`/`Craft`, and the concrete kill task is spelled `KillNPC`. The original list was derived
  from class names; this one from the loader's own discriminator — see
  `hytale-asset-packs.md` §6.)*
- **Trigger conditions:** **`WeatherTriggerCondition`** (carries `weatherIds[]` *and*
  `weatherIndexes[]`, reads `WeatherResource` + `TransformComponent`),
  `ObjectiveLocationTriggerCondition`, `HourRangeTriggerCondition`.
- **Completions (3):** `GiveItems` (by drop list), `ClearObjectiveItems`, `Reputation`.
  The third is registered at runtime by `ObjectiveReputationPlugin` via
  `CodecMapCodec.register("Reputation", ...)` from a *separate* Maven module — a first-party
  demonstration of the extension path a mod would use. See `hytale-asset-packs.md` §7.
- **Markers:** `ObjectiveLocationMarker` (Box or Radius areas, with separate entry/exit boxes),
  `ReachLocationMarker`, `ObjectiveTaskMarker`.
- **Location providers:** `LocationRadiusProvider`, `CheckTagWorldHeightRadiusProvider`,
  `LookBlocksBelowProvider`.
- **Durability:** `ObjectiveDataStore`, `ObjectiveHistoryComponent`, per-reward history data, and a
  `transaction/` package (`TransactionRecord`, `TransactionStatus`, `SpawnEntityTransactionRecord`,
  …) giving transactional spawn/registration semantics.
- **NPC integration:** `ActionStartObjective`, `ActionCompleteTask`, `SensorHasTask`.
- **UI:** `ObjectiveAdminPanelPage`, `DialogPage`, `ObjectivePanelCommand`.

Both asset classes expose `getAssetStore()`, which is exactly what
`AssetRegistry.register(AssetStore)` takes — so **a mod can ship a questline as JSON.**

> **Confirmed by running, 2026-08-25.** A probe plugin shipped an `ObjectiveLineAsset` from a mod
> asset pack into a live server; it entered the store attributed to the mod's pack, resolved
> references to base-game objectives, overrode a base-game asset when given its name, and was
> rejected by the engine's own validator when made deliberately invalid. The mechanism, the pack
> layout, the reproduction recipe, and the `--generate-asset-schema` tooling are documented in
> `hytale-asset-packs.md`.

Shipped examples live at `Server/Objective/` in `Assets.zip`. Verified shape:

```jsonc
// Server/Objective/ObjectiveLines/ObjectiveLine_Test.json
{ "ObjectiveIds": ["Objective_Gather", "Objective_Craft"] }

// Server/Objective/Objectives/Objective_Gather.json
{
  "TaskSets":    [ { "Tasks": [ { "Type": "Gather",
                                  "BlockTagOrItemId": { "ItemId": "Soil_Dirt" },
                                  "Count": 3 } ] } ],
  "Completions": [ { "Type": "GiveItems", "DropList": "Trork_Camp_Inventory" } ]
}
```

### 4.2 Trigger Volumes — no-code encounter scripting

Shipped in **Update 5 (2026-05-26)**. 187 trigger-related classes; the in-game editor alone
accounts for ~440 localization keys. It is an event → condition → effect engine with an in-world
authoring tool, grouping, cooldowns, delays, and saveable presets.

- **Effects:** `Teleport`, `SetVelocity`, `DamageEntity`, `EntityEffect`, `SendMessage`,
  `ShowEventTitle`, `PlaySound`, `SetMusic`, `ReplaceBlockType`, `ControlDoors`, `ModifyTags`,
  `EnableVolume`, `TriggerNpcMarkers`.
- **Conditions:** `BlockType`, `Tag`, `VolumeTagMatcher`, `Cooldown`, `RandomChance`,
  `PlayerCount`, `GameMode`, `Permission`.
- **Structure:** success-effects vs rejection-effects, per-effect delay, condition timing, volume
  shapes, groups.

Anything expressible as "when a player enters this region under these conditions, do these
things" needs no plugin code at all.

---

## 4.3 Two frameworks that are built and effectively unused

Both are mod-registrable asset types, and both matter more than their shipped usage suggests.

**`EntityEffect` — the status-effect framework.** `asset/type/entityeffect/config/` has
`EntityEffect`, `ApplicationEffects`, `AbilityEffects`, `ModelOverride`, `OverlapBehavior`,
`RemovalBehavior`, plus `entity/effect/ActiveEntityEffect`, `LivingEntityEffectSystem`,
`EntityEffectPacketGenerator`, an `EntityEffectCommand`, a `TargetEntityEffect` interaction and a
`ClearEntityEffectInteraction`. **Exactly one asset ships against it: `Test_Apply_EntityEffect`.**
Buffs, debuffs, stacking rules, removal rules and appearance overrides are all available and almost
nothing uses them.

**`DamageCause` is an asset, not an enum.** It implements `JsonAssetWithMap` with an
`AssetBuilderCodec`, an `id` and an `inherits`, so a mod can register its own. Shipped causes live
in `Server/Entity/Damage/`: Bludgeoning, Command, Drowning, **Elemental**, Environment,
Environmental, Fall, **Fire**, **Ice**, OutOfWorld, Physical, Poison, Projectile, Slashing,
Suffocation. `Elemental.json` carries the comment *"This damage type exists to facilitate sub
types"*, and `Fire.json` / `Ice.json` are three-line files that inherit it. **There is no Lightning
subtype** — the same shaped gap as the missing storm circle.

`Damage` itself is directly constructible — `new Damage(Damage.Source, DamageCause, float)` — and
carries its presentation as metadata: `IMPACT_PARTICLES`, `IMPACT_SOUND_EFFECT`,
`PLAYER_IMPACT_SOUND_EFFECT`, `CAMERA_EFFECT`, `KNOCKBACK_COMPONENT`, `HIT_LOCATION`, `DEATH_ICON`,
`STAMINA_DRAIN_MULTIPLIER`.

---

## 4.4 Announced but not shipped — do not design around their absence

Hypixel Studios has **no published roadmap**; it has said it is waiting for player feedback before
producing one. What follows is from studio statements and press, cross-checked against what is
actually in build 0.5.9. Treat the dates as unknown and the shapes as provisional — but knowing
these are coming is enough to avoid building something they will obsolete.

| Announced | State in 0.5.9 | What it means for us |
|---|---|---|
| **Mana system**, described as in development, with current magic items called deliberate previews | **Zero `Mana` classes in the jar.** Present only as item names — `Prototype - Mana Book`, `Prototype - Mana Guitar`, `Prototype - Mana Staff` — all explicitly `Prototype_` | Do **not** invent a Stormseeker-specific resource meter. If a costed ability is ever wanted, stub the cost and wait. |
| **Ability rune system** (announced July 2026) | `asset/type/entityeffect/config/AbilityEffects` + `protocol/AbilityEffects` + `AbilityCombatAction` exist; `Rune` appears in 11 asset paths and no lang strings | `AbilityEffects` under `EntityEffect` is the plausible substrate. Build any Stormseeker ability *on `EntityEffect`* rather than on a bespoke system. |
| **Magic "much bigger than a few spell books"** | Two schools ship as items: `Recipe_Book_Magic_Air` = **"Air Grimoire"**, `Recipe_Book_Magic_Void` = "Void Grimoire". Plus `Weapon_Spellbook_Grimoire_*` and `Grimoire: Rekindle Embers` | **An Air school already exists and is the nearest neighbour to storm/lightning.** If air magic is developed, Stormseeker's identity will sit beside it — worth watching so the two do not contradict each other. |
| Goblin faction + Goblin Breach world event, hardcore mode, cubic-chunk world tech, party minigames | Not present | No overlap with this project. Cubic chunks could affect worldgen assumptions if we ever place structures. |

**The practical rule:** where a Stormseeker mechanic could plausibly be expressed as an
`EntityEffect`, express it that way rather than inventing a parallel system — that is the surface
the announced work appears to be building on. Re-check this table at every re-audit; it is the part
most likely to change.


---

## 5. Constraints and gotchas

- **There is no weather forecasting API. `WeatherForecast` is not a forecast.** It is one row of a
  weighted probability table — `weatherId` + `weight`, implementing `IWeightedElement` — i.e. the
  `{"WeatherId": "Zone1_Storm", "Weight": 1}` entries in `Env_Zone1.json`. Weather is rolled from
  those weights on an hour boundary (`WeatherResource.compareAndSwapHour(int)`). **Nothing exposes
  "what weather is coming next."** A design that wants the player to anticipate a storm cannot read
  it from the engine; it must either derive its own schedule or force the weather. Recorded because
  the class name reads like a capability it does not have, and was taken for one during this audit.
- **The server can force weather — but forcing it BLINDS the asset-side weather condition.**
  `WeatherResource.setForcedWeather(String)` / `getForcedWeatherIndex()` /
  `consumeForcedWeatherChange()`. Useful for a scripted one-off; note it changes the sky for every
  player in that environment, not just the one who triggered it.

  `getWeatherIndexForEnvironment(int)` reads current weather per environment — and this is the
  trap: it reads the `environmentWeather` map, whose **only** writer is a block that
  `WeatherSystem$TickingSystem` skips outright while a forced weather is set
  (`getForcedWeatherIndex(); ifne`). So a forced storm never reaches
  `WeatherTriggerCondition`, and a marker gated on that storm silently fails to fire. Absent keys
  return `Integer.MIN_VALUE`, so the failure is total — a condition naming *every* weather id in
  the game still does not match. Verified by running, both directions, 2026-08-25;
  see `docs/integration/hytale-asset-packs.md` §8d. Anything that needs to *observe* forced
  weather must go through the player's `WeatherTracker` component instead.
- **Post-processing shaders are almost certainly unavailable.** February listed them as "Unknown"
  with the Leyline Sight visual "TBD". The client is a closed native binary and nothing in the
  server jar or protocol surfaces a shader hook. Treat any screen-space effect as unavailable until
  proven otherwise: an in-world effect built from particles and `DynamicLight` is the supported
  shape. This bounds what a "vision mode" can look like.
- **Storms are rare.** In `Env_Zone1`'s forecast table, `Zone1_Storm` has `Weight: 1` against
  `Zone1_Sunny`'s `52` (~1.5% of rolls). Any design gated on an active storm is gated on an
  uncommon event. Check the forecast weights per zone before assuming availability.
- **Weather should be detected by tag, not by ID substring.** Every weather asset carries a `Tags`
  map, and **tag placement is inconsistent**: `Zone1_Storm` is `{"Zone1": [], "Rain": ["Storm"]}`
  while `Zone2_Sand_Storm` is `{"Zone2": ["Sandstorm", "Storm"]}` — the storm marker sits under a
  weather-type key in one and under the zone key in the other. Handle both.
- **`Zone2_Thunder_Storm` is the only thunder storm in the game** (87 weather definitions
  surveyed). Thunder is a Zone 2 phenomenon.
- **The game auto-updates underneath the build.** The 2026-08-17 update shipped a Java 25 jar
  against a Java 21 build and broke compilation project-wide. `:mod:hytale:checkHytaleJarVersion`
  now catches that with one message.
- **The jar is decompiled and ships no sources or Javadoc.** Signatures via `javap` are the
  contract. Community-generated documentation exists but is built from older jars — at time of
  writing, the most complete community reference is generated from `2026.01.15`, seven months
  behind this build.
- **CI can never verify any of this.** Runners have no game jar, so `mod/hytale` is `EXEMPT` on
  every CI run. The only observation point is a session on a machine with the game installed —
  tracked as `Workspace_Deferrals` #17.

---

## 6. Re-audit checklist

Run after every launcher update, before trusting section 1:

1. `unzip -p <jar> META-INF/MANIFEST.MF | grep -E 'Implementation-(Version|Revision-Id)'` — record it here.
2. `./gradlew :mod:hytale:compileJava` — API drift shows up as a compile error.
3. Re-check any row in §1 the current design depends on.
4. Diff `Server/Weathers/` and `Server/Objective/` against the previous build — these are the two
   asset trees this project's design is coupled to.

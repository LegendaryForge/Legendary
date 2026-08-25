# Mod Asset Packs — how a mod ships content as JSON

> **Verified by running, on server `0.5.9`, revision `214c57c5a63e6e5d51ed8be4c8a508dfcc177d16`.**
> Not inferred from class names. A probe plugin was compiled against the installed
> `HytaleServer.jar`, packaged with an asset pack, loaded by a real server, and its effect on the
> live asset store read back through the engine's own API. The evidence lines are quoted below.
> **Probed:** 2026-08-25.
>
> **This document is more durable than `hytale-capability-audit.md`.** That document lists *what
> exists* and expires on every game update. This one describes the *mechanism* by which a mod
> contributes content — a load-bearing engine contract far less likely to churn. The
> vocabularies in §6 are the part that expires; regenerate them with §5 rather than trusting them.

---

## Why this document exists

`mod/hytale`'s `manifest.json` shipped `"IncludesAssetPack": false` from the beginning, and
nothing in this repository has ever referenced `AssetStore`. The flag was never flipped because
nobody knew what it did.

It turns out to be the entire content pipeline. Hytale's questline system — objectives, chains,
weather-gated triggers, rewards, persistent per-player history — is **asset-driven**, and mods
contribute to it by shipping JSON, not by calling registration APIs. `DefaultAssetMap`'s
`put`/`putAll` are `protected`; there is no public "add an asset at runtime" call, and looking for
one is the wrong search.

The practical consequence: a large part of what `quests/stormseeker` hand-rolls has a native,
persistent equivalent that is authored rather than coded. See §7.

---

## 1. What was proven

A probe plugin shipped one `ObjectiveLineAsset` as JSON. Read back from the live store through
`ObjectiveLineAsset.getAssetMap()` during plugin `start()`:

```
PROBE_BEGIN count=3
PROBE_LINE id=ObjectiveLine_Test           pack=Hytale:Hytale                            objectives=[Objective_Gather,Objective_Craft]
PROBE_LINE id=ObjectiveLine_Tutorial       pack=Hytale:Hytale                            objectives=[Objective_Tutorial]
PROBE_LINE id=ObjectiveLine_LegendaryProbe pack=io.github.legendaryforge:AssetPackProbe  objectives=[Objective_Gather,Objective_Craft]
```

Four properties, each separately confirmed:

| Property | Evidence |
|---|---|
| **A mod pack loads** | `[AssetModule] Loaded pack: io.github.legendaryforge:AssetPackProbe from AssetPackProbe.jar` |
| **Its asset enters the live store** | present in `getAssetMap()`; `getAssetPack(id)` attributes it to our pack |
| **Cross-pack references resolve** | our line references base-game `Objective_Gather` / `Objective_Craft` |
| **A mod can override a base asset** | shipping our own `ObjectiveLine_Test.json` **replaced** the game's — count stayed 3, `pack=` flipped to ours, contents became `[Objective_Kill]` |

**The negative control is what makes the rest trustworthy.** A deliberately invalid asset
(`"ObjectiveIds": []`, violating the `nonEmptyArray` validator on that field) was rejected by name:

```
[AssetStore|ObjectiveLineAsset] Failed to validate asset: ObjectiveLine_LegendaryProbe,
/Server/Objective/ObjectiveLines/ObjectiveLine_LegendaryProbe.json
Id: ObjectiveLine_LegendaryProbe
Key: ObjectiveIds
```

Mod assets are first-class: parsed, validated against the same rules as the game's own, and failed
loudly with the offending key named. They are not silently tolerated, and — importantly — a broken
mod asset is not silently *ignored* either.

---

## 2. The mechanism

Four links, each read from bytecode and then observed at runtime:

1. **`PluginManifest.includesAssetPack()`** — the `IncludesAssetPack` field in `manifest.json`.
2. **`PluginManager.registerAssetPackIfNeeded(PendingLoadPlugin)`** — when that flag is true, calls
   `AssetModule.registerPack(pluginId, pluginJarPath, manifest, PackSource.RUNTIME)`. **The mod's
   own jar becomes the asset pack.** A matching `unregisterAssetPackIfNeeded` runs on unload, so
   packs are symmetric with the plugin lifecycle.
3. **`AssetModule.initStore(AssetStore)`** — for every registered pack, resolves
   `pack.getRoot().resolve("Server").resolve(store.getPath())` and loads it if it is a directory.
   This is where the `Server/` prefix in the layout below comes from.
4. **`ObjectivePlugin`** registers its stores with `setPath("Objective/ObjectiveLines")` (and
   `Objective/Objectives`, `Objective/ObjectiveLocationMarkers`, `Objective/ReachLocationMarkers`),
   then `build()` → `AssetRegistry.register(...)`. **It never calls `unmodifiable()`** — that
   builder method exists and is simply not used here, which is why mods can contribute and override.

`AssetModule` also carries the log string `Asset pack '%s' overriding %s with %s: %s`, and computes
a pack load order from manifest dependencies. Override is a designed feature, not an accident.

---

## 3. Pack layout

A mod jar that ships assets is just a jar with a `Server/` tree at its root:

```
LegendaryHytale.jar
├── manifest.json                     "IncludesAssetPack": true
├── io/github/legendaryforge/...      (the plugin classes, as today)
└── Server/
    └── Objective/
        ├── ObjectiveLines/ObjectiveLine_Stormseeker.json
        ├── Objectives/Objective_Stormseeker_TheMark.json
        └── ObjectiveLocationMarkers/ObjectiveLocationMarker_StormNexus.json
```

**Asset ids come from filenames**, not from a field inside the file. `ObjectiveLine_Test.json`
defines the asset `ObjectiveLine_Test` — which is also why shipping a file with a base-game name
overrides that asset.

Mirroring the base game's own layout (`Server/Objective/...` inside `Assets.zip`) is deliberate:
the base game is itself a pack, with a two-line `manifest.json` reading
`{"Group": "Hytale", "Name": "Hytale"}`.

> **A jar in the mods directory is always loaded as a Java plugin.** An assets-only jar with no
> `Main` fails with an NPE in `PendingLoadJavaPlugin.load` (it calls `loadLocalClass(null)`). If a
> pack needs to ship without code, that path needs its own investigation — it was not established here.

---

## 4. Reproducing the probe

The whole harness is a ~20-line plugin and one server flag set. `--bare` skips world loading and
port binding, so it is fast and touches nothing.

```java
public class ProbePlugin extends JavaPlugin {
    public ProbePlugin(@Nonnull JavaPluginInit init) { super(init); }

    @Override protected void start() {
        var map = ObjectiveLineAsset.getAssetMap();
        getLogger().atInfo().log("PROBE_BEGIN count=" + map.getAssetCount());
        for (String key : map.getAssetMap().keySet()) {
            getLogger().atInfo().log("PROBE_LINE id=" + key + " pack=" + map.getAssetPack(key));
        }
    }
}
```

```bash
GAME=~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/install/release/package/game/latest

java -jar "$GAME/Server/HytaleServer.jar" \
     --assets "$GAME/Assets.zip" --mods <dir-containing-the-mod-jar> \
     --bare --auth-mode offline --disable-sentry --disable-file-watcher
```

`map.getAssetPack(id)` is the key call: it reports which pack a given asset came from, which is what
distinguishes "our asset loaded" from "the base game already had one by that name".

**Adding `--validate-assets --shutdown-after-validate` turns this into an asset gate** that exits
non-zero when any asset fails validation. That is a genuine CI-shaped check — but note it also
validates world instances, which fail under `--bare` with no universe, so it currently exits non-zero
for an unrelated reason. Read the `[AssetStore|...]` lines, not the exit code. (This repo's standing
rule about reading verdicts by name rather than by exit status applies here too.)

---

## 5. `--generate-asset-schema` — the authoring reference

```bash
java -jar "$GAME/Server/HytaleServer.jar" --assets "$GAME/Assets.zip" \
     --generate-asset-schema <out-dir> --auth-mode offline
```

Emits **105 JSON Schema files**, one per authorable asset type, with human descriptions,
`markdownDescription`, and editor UI hints. It is the authoritative answer to "what fields does this
asset take" and it is regenerable in one command — **do not transcribe it into documentation that
will then rot.** Regenerate it after any game update.

The list of 105 is itself a capability map. Beyond objectives it includes `Weather`, `DamageCause`,
`EntityEffect`, `CameraShake`, `ParticleSystem`, `Interaction`, `CraftingRecipe`, `Item`, `NPCRole`,
`Projectile`, `BiomeAsset`, `WorldStructureAsset`, `TriggerEffectAsset`, `ReputationGroup`.

Two consequences worth flagging against the capability audit's recorded gaps: **`Weather` and
`DamageCause` are authorable asset types.** "No `Lightning` DamageCause" and "no storm weather for
our zone" are things a pack can *add*, not absences to design around.

### Asset inheritance

Every asset schema carries a `Parent` field:

> "When set this asset will inherit properties from the named asset. […] In the case where both
> child and parent provide a field the child field will simply replace the value provided by the
> parent, in the case of nested structures this will apply to the fields within the structure. In
> some cases the field may decide to act differently, for example: by merging the parent and child
> fields together."

So a family of related assets can share a base definition. This had not been recorded anywhere
before and it materially changes how a per-element questline family would be authored.

---

## 6. Vocabularies — schema-derived, expires with the build

These come from the generated schemas' `hytaleSchemaTypeField.values`, which is the discriminator
the loader actually accepts. **Regenerate rather than trust after any update.**

| Union | Accepted `"Type"` values |
|---|---|
| Objective task | `Bounty` `Craft` `Gather` `KillNPC` `KillSpawnBeacon` `KillSpawnMarker` `ReachLocation` `TreasureMap` `UseBlock` `UseEntity` |
| Objective completion | `ClearObjectiveItems` `GiveItems` `Reputation` |
| Marker trigger condition | `HourRange` `Weather` |
| Marker setup | `Objective` `ObjectiveLine` |
| Marker area | `Box` `Radius` |

> **Correction to `hytale-capability-audit.md` §4.1.** That section lists twelve task types
> including `Count` and `Kill`. Neither is an accepted `"Type"`: `CountObjectiveTaskAsset` is an
> abstract base (its `Count` field appears *inside* `Gather` and `Craft`), and the concrete kill task
> is spelled `KillNPC`. The true count is **ten**. The audit derived its list from class names; this
> one is derived from the loader's own discriminator, which is why they disagree. It also lists two
> completions — there are three, for the reason in §7.

---

## 7. Extending the vocabulary from a mod

Each union above is backed by a `public static final CodecMapCodec` with a **public
`register(String, Class, Codec)`**: `ObjectiveTaskAsset.CODEC`, `ObjectiveCompletionAsset.CODEC`,
`ObjectiveLocationTriggerCondition.CODEC`, `TaskConditionAsset.CODEC`,
`ObjectiveLocationMarkerArea.CODEC`, `ObjectiveTypeSetup.CODEC`.

**This is not a theoretical seam — Hypixel uses it themselves.**
`ObjectiveReputationPlugin` calls:

```
CodecMapCodec.register("Reputation", ReputationCompletionAsset.class, <codec>)
```

which is exactly why `Reputation` appears in the completion vocabulary above while living in a
separate Maven module (`ObjectiveReputation`) from `Objectives`. A shipped first-party plugin
extending the vocabulary the same way a mod would is the strongest available evidence that the path
is supported.

The runtime half of a custom completion is one method: subclass `ObjectiveCompletion` and implement
`handle(Objective, ComponentAccessor<EntityStore>)`.

So the division of labour for a questline is:

- **Author in JSON** — the chain, stages, tasks drawn from the ten built-in types, location markers,
  weather/time gating, item rewards.
- **Write in Java** — anything the vocabulary lacks, registered as a new `"Type"` and then *used
  from JSON*: Stormseeker phase transitions, attunement, storm-survival tasks.

---

## 8. Gameplay, also verified by running

**Updated 2026-08-25.** This section previously said no gameplay had been exercised — the original
probe ran `--bare` with no world. That gap is now closed. A questline authored as four JSON files,
shipped from a mod asset pack with a logging-only plugin, was played end to end on a dedicated
server by a connected player:

| Behaviour | Result |
|---|---|
| `Gather` tracks real inventory events | ✅ |
| Objective auto-completes when its tasks are satisfied | ✅ no command needed |
| Line chains to the next objective automatically | ✅ |
| `Craft` tracks a real crafting action | ✅ |
| Completion delivers rewards | ✅ the `GiveItems` drop list we named |
| Line-level completion recorded | ✅ |
| State persists to disk | ✅ `universe/players/<uuid>.json`, plus a `.bak` |

The persisted `ObjectiveHistoryComponent` keys history by **our** line id, nests both objectives
under it with completion counts and timestamps, records the reward per objective, and round-trips
the `"Category": "Stormseeker"` field we wrote in our own JSON.

**So a questline needs no Java gameplay code.** Chaining, task tracking, completion, rewards, and
persistence are all native.

### `Gather` and `Craft` are different in kind — this matters

Found by the probe failing, which is what it was for. `GatherObjectiveTask implements
InventoryChangeAware`; `CraftObjectiveTask` does **not**.

- **`Gather` is possession** — "have N of X". Recounted on every `InventoryChangeEvent` via
  `countObjectiveItemInInventories(...)`, so it is satisfied by acquiring the items *by any means*,
  including `/give`.
- **`Craft` is an action** — "perform the crafting of X". Being handed the item does nothing; the
  crafting must actually happen.

That pairing is worth designing with rather than around: a gather-then-craft phase gate cannot be
bypassed by trade or drop, because only the crafting half counts.

### Admin completion is not equivalent to play

`/objective complete objective <id>` completes the objective but does **not** roll up to line
completion — the line's `TimesCompleted` stayed `0`. A genuine play-through set it to `1`.
Line-level bookkeeping only happens on the real advance path, so admin commands are a shortcut for
*reaching* a state, not a substitute for exercising one.

---

## 8b. What this still does *not* establish

- **`WeatherTriggerCondition` firing.** The marker asset carrying it parses, validates and loads;
  nothing has yet stood in a storm and watched it trigger. This is the Stormseeker-specific mechanic,
  so it remains the most load-bearing untested thing here.
- **Assets-only packs (no `Main`) were not established** — see the callout in §3.
- **Nothing here says we should adopt the native framework.** It says adoption is *possible*,
  cheap to author, and now demonstrably works at runtime. The design decision is separate and belongs
  with `docs/architecture/native-objectives-migration-cost.md`,
  `docs/architecture/questline-framework-adoption.md` and
  `docs/stormseeker/stormseeker-canonical.md`.

---

## 9. Bearing on `questline-framework-adoption.md`

That document specifies preconditions P1–P3 — three real code changes to `StormseekerWiring` that
activate dormant paths in the module with the least test coverage — before `core`'s
`QuestlineModule` SPI can be adopted by `mod/hytale`.

Those preconditions were written before this mechanism was known. `core`'s framework answers "how do
N questlines register into our plugin"; the native system answers "what a quest is and how progress
persists" — the half `quests/stormseeker` currently hand-rolls, including a phase state machine and
an on-disk progress store that `ObjectiveHistoryComponent` already provides per player.

They are not substitutes, but the second materially shrinks the first. **Settle the native-objectives
question before executing P1–P3**, or that work risks being written against a spine that is about to
be replaced.

# Mod Asset Packs — how a mod ships content as JSON

> **Verified by running, on server `0.5.9`, revision `214c57c5a63e6e5d51ed8be4c8a508dfcc177d16`.**
> Not inferred from class names. A probe plugin was compiled against the installed
> `HytaleServer.jar`, packaged with an asset pack, loaded by a real server, and its effect on the
> live asset store read back through the engine's own API. The evidence lines are quoted below.
> **Probed:** 2026-08-25.
>
> **Second pass, 2026-08-25 — Stormseeker Act II played end to end.** A real questline was shipped
> from `mod/hytale`'s own asset pack and **played on a dedicated server by a connected client** —
> not a probe with a world attached, but content a person walked into and read. That run added
> §4b, §7b, §7c, §7d and the `Interaction.CODEC` row in §7, and it **corrected three claims this
> document already made**: §4's `--bare` recommendation, §8's "no Java gameplay code" conclusion,
> and §8's line-completion row. Each correction is applied *where the wrong text lived*, with the
> superseded claim quoted rather than deleted, so a reader cannot pick up the old claim and miss
> the retraction two sections later.
>
> Throughout, **"verified by running"** means observed in a log line or on a screen during that
> session; **"read from bytecode"** means decompiled and reasoned about but not exercised. The two
> are labelled separately everywhere they appear.
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

**It is flipped now.** As of the Stormseeker Act II branch `mod/hytale`'s `manifest.json` reads
`"IncludesAssetPack": true` and the module ships a `Server/` tree — one objective line, two
objectives, one reach-location marker, five item/block assets and one `server.lang`. The rest of
this document is the mechanism that made that possible; §7b–§7d are what shipping it taught.

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
    ├── Objective/
    │   ├── ObjectiveLines/ObjectiveLine_Stormseeker.json
    │   ├── Objectives/Objective_Stormseeker_TheMark.json
    │   └── ObjectiveLocationMarkers/ObjectiveLocationMarker_StormNexus.json
    ├── Item/Items/Furniture_Stormseeker_Inscription_Five.json
    └── Languages/en-US/server.lang          ← translations; see §7b
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

> **Correction, 2026-08-25 (Act II). `--bare` cannot check a *mod's* assets — no mod is loaded.**
> This section originally read "`--bare` skips world loading and port binding, so it is fast and
> touches nothing," and recommended it as the probe harness. That advice was written for
> inspecting **base-game** assets, where it is still correct and still the fastest thing available.
> It is wrong for anything shipped by a mod: **verified by running**, under `--bare` the
> `PluginManager` never runs at all, so no plugin loads, `registerAssetPackIfNeeded` never fires,
> and no mod pack is registered — which means the §1 probe, itself a plugin, could never have run
> under `--bare` in the first place. That probe predates this finding and has been re-checked
> against a normal boot.
>
> `--validate-assets --shutdown-after-validate` has the same defect for the same reason: it
> completes **before** mods are loaded, so it validates the base game and nothing of ours.
>
> Worse, both then fail — **verified by running** — with
>
> ```
> Asset validation FAILED with 1 reason(s): failed to validate instances
> ```
>
> which is an unrelated failure (no universe exists under `--bare`) that reads exactly like a real
> asset failure. A reader who trusted it would conclude their mod's assets were broken when in fact
> their mod had never been loaded.
>
> **Only a normal boot registers a mod's pack.** Use §4b's launch line for anything touching mod
> content, and keep `--bare` for the base-game inspection it was written for.

The whole harness is a ~20-line plugin and one server flag set. For **base-game** inspection,
`--bare` skips world loading and port binding, so it is fast and touches nothing.

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

# NOT --bare: loading a mod's asset pack requires PluginManager to run, which --bare skips
# entirely (see the correction above). Use a normal boot — see §4b for the full launch line.
java -jar "$GAME/Server/HytaleServer.jar" \
     --assets "$GAME/Assets.zip" --mods <dir-containing-the-mod-jar> \
     --auth-mode offline --disable-sentry --disable-file-watcher
```

`map.getAssetPack(id)` is the key call: it reports which pack a given asset came from, which is what
distinguishes "our asset loaded" from "the base game already had one by that name".

**`--validate-assets --shutdown-after-validate` is not an asset gate for mod content.** This
section previously described it as "a genuine CI-shaped check". It is not one for us: it runs
before plugins load, so it never sees a mod's assets, and under `--bare` it always exits non-zero
for the unrelated world-instance reason above. What actually validates our assets is a **normal
boot** — the engine parses every mod asset at load and fails each one loudly and by name (§1), so
the check is "boot the server and grep for `Failed to validate`". Read the `[AssetStore|...]`
lines, never the exit code. (This repo's standing rule about reading verdicts by name rather than
by exit status applies here too, and this is a good example of why: the exit code here is a lie in
both directions.)

For build-time checking that needs no server at all, `mod/hytale`'s `checkAssetPackIntegrity`
guard reads the shipped files directly and asserts every asset filename and every `server.lang`
key carries a Stormseeker-scoped prefix — the collision hazard in §7b, caught without booting
anything.

---

## 4b. Driving a running server — in-game command syntax

**Verified by running**, 2026-08-25. This cost real operator time and is worth writing down: the
commands are not shaped the way the base game's own docs or the jar's argument names suggest.

**Required arguments are positional; optional arguments are `--name=value` flags.** The working
form of `/give` is:

```
/give <player> <item> --quantity=1
```

`/give <item> <count>` — the obvious guess — does not work. The confirmed set used to play Act II:

| Command | What it does |
|---|---|
| `/give <player> <item> --quantity=1` | put a block or item in a player's inventory |
| `/objective start objectiveLine <id>` | start a line directly, bypassing whatever normally starts it |
| `/objective reachLocationMarker add <id>` | **place** a `ReachLocationMarker` at your current position |
| `/objective history` | dump the calling player's `ObjectiveHistoryComponent` |
| `/gamemode <adventure\|creative\|a\|c>` | switch mode |

> **`/give` needs the `hytale:Builder` role or Admin, and a fresh server directory starts
> unprivileged.** The refusal reads like a syntax error, which is how an hour goes missing. Launch
> with `--allow-op` and `/op` yourself in game.

**The in-game chat box autocompletes commands as you type, and that is a better authority than
reading argument names out of the jar.** It knows the real arity, the real flag names and the real
enum values, and it is right when a decompiled signature is merely suggestive. Reach for it first.

> **`/objective complete objective <id>` is not a substitute for playing.** It reaches a state
> without exercising the path that gets there — see the line-completion finding in §8, where the
> distinction turned out to matter more than expected.

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
before and it materially changes how a per-element questline family would be authored. **That
paragraph was the schema's claim, not a result** — §7d records what the field was then observed to
actually carry, which is more than the wording above would let you assume.

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

**`Interaction.CODEC` is a seventh, and this list omitted it.** Added 2026-08-25: it is an
`AssetCodecMapCodec` with the same public `register(String, Class, BuilderCodec)`, so the
**interaction** vocabulary — the `"Type"` values legal inside an `Interactions` array — is
extensible from a mod exactly like the objective vocabulary is. That omission mattered: Act II
needed a custom interaction (§7c) and this document said nothing about whether one was possible.

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

### Register from `setup()`, not `start()`

**Verified by running**, 2026-08-25. Registration has to happen in the plugin's `setup()`, because
`setup()` runs **before assets are parsed** and an asset referencing an unregistered `"Type"` fails
validation at parse time. `start()` is too late — by then the asset that needed the codec has
already been rejected.

This is confirmed twice over: `ObjectiveReputationPlugin`, first-party, registers its codecs in
`setup()` for the same reason; and `mod/hytale`'s own registration, made from `setup()`, produced

```
[LegendaryHytale|P] Registered interaction type: StormseekerStartLine
```

followed by **zero** `Failed to validate` lines on a boot where an asset in the same jar referenced
`"Type": "StormseekerStartLine"` by name. So a mod jar can ship both a new `"Type"` and the assets
that use it, in one artifact, with no ordering ceremony beyond putting the `register` call in
`setup()`.

So the division of labour for a questline is:

- **Author in JSON** — the chain, stages, tasks drawn from the ten built-in types, location markers,
  weather/time gating, item rewards.
- **Write in Java** — anything the vocabulary lacks, registered as a new `"Type"` and then *used
  from JSON*: Stormseeker phase transitions, attunement, storm-survival tasks.

---

## 7b. A mod pack ships translations — and this is how text reaches players

**Verified by running**, 2026-08-25. `ObjectiveAsset.TitleId` and `DescriptionId` are
**localisation keys, not literal text**, so without this the objective-text delivery channel does
not work at all — the player sees raw keys where the story should be.

**The mechanism, read from bytecode.** `I18nModule.setup` calls `AssetModule.getAssetPacks()`,
iterates it, and calls `loadMessagesFromPack(pack)` for each; that resolves
`pack.getRoot()/Server/Languages` and reads every `.lang` beneath it. A mod jar shipping
`Server/Languages/en-US/server.lang` therefore contributes translations by exactly the same route
it contributes assets — the pack is the unit, and there is nothing extra to declare.

**Confirmed at runtime**, on a boot of the Act II jar:

```
[AssetModule|P] Loaded pack: io.github.legendaryforge:LegendaryHytale from hytale-0.0.0-SNAPSHOT-all.jar
...
[I18nModule|P] Loaded 18 entries for 'en-US' from /Server/Languages
[I18nModule|P] Loaded 9993 entries for 'en-US' from /Server/Languages
```

**Two `en-US` loads, not one.** The 9993 is the base game; the 18 is ours, and our `server.lang`
has exactly 18 entries. The count matching is the evidence — it distinguishes "our file was read"
from "a file was read". And on a connected client, objective titles, task lines and inscription
messages then appeared **as prose**, not as keys. Both halves matter: the log proves the file
loaded, the screen proves it resolved for the player.

**The filename is the key prefix.** A key written in `server.lang` as `objectives.X.title`
resolves as `server.objectives.X.title`. This is why the base game's own file contains
`objectives.Objective_Kill.title` while the schema's editor template reads
`server.objectives.{assetId}.title`. Neither is wrong; they are the two ends of the same string.

The conventions the base game uses, all confirmed in its own `Server/Languages/en-US/server.lang`:

| Key | What it names |
|---|---|
| `objectives.<AssetId>.title` | objective title |
| `objectives.<AssetId>.desc` | objective description |
| `objectives.<AssetId>.taskSet.<i>.task.<j>` | one task's line — **each task gets its own string** |
| `items.<ItemId>.name` | item / block name |

Values support inline markup: `Gather <item is="Soil_Dirt"/>`.

### The `objectivelines` prefix — a schema contradiction, settled empirically

The generated schemas disagree with each other. `ObjectiveAsset`'s editor template reads
`server.objectives.{assetId}.title`; `ObjectiveLineAsset`'s reads
`objectivelines.{assetId}.title` — **without** the `server.` prefix. Both cannot be right, and
reading harder would not have decided it.

**The `server.`-prefixed form works for both.** Act II ships
`server.objectivelines.ObjectiveLine_Stormseeker_TheSixthCircle.title` alongside
`server.objectives.…title`, in one file, and **both the line title and the objective titles
rendered correctly in game**. So the `server.` prefix is supplied by the *filename* uniformly — it
is not a per-asset-type convention — and **no separate `objectivelines.lang` file is needed**. The
`ObjectiveLineAsset` template is the one to distrust.

> **Translations are global.** A key we ship that collides with a base-game key **replaces that
> text for every player on the server**, including players who never touch our content. It is the
> same hazard as an asset filename collision (§3) and has the same shape: designed behaviour,
> invisible to the compiler, fails green. `mod/hytale`'s `checkAssetPackIntegrity` guard requires
> every key we ship to carry a scoped prefix for exactly this reason.

---

## 7c. Attaching behaviour to a block

**Verified by running**, 2026-08-25 — and this section exists because the first two attempts were
wrong in ways nothing in the build could see.

### The field is `BlockType.Interactions.Use`, and the item's `Interactions.Secondary` is not it

A block item's **top-level** `Interactions.Secondary` is the **place-the-block action**. The
shipped `Temple_Wind` sign declares:

```json
"Interactions": { "Primary": "Block_Primary", "Secondary": "Block_Secondary" }
```

`Block_Secondary` *is* placement. Overwriting it with a message chain — the intuitive reading of
"what happens when I right-click this" — makes the block **unplaceable**: the item talks in your
hand and never becomes a block. That was done, shipped, and reported back by the player holding it.

Behaviour on the **placed** block lives one level down, under `BlockType`:

```json
"BlockType": { "Interactions": { "Use": { "Interactions": [ … ] } } }
```

The shipped reference is `Furniture_Crude_Bed`, whose `BlockType.Interactions` is
`{"Use": {"Interactions": [{"Type": "Bed"}]}}`.

**The `UseBlock` objective task tracks the `Use` interaction on a placed block** — the shipped
`Objective_UseBlock` targets `Furniture_Crude_Bed`, that same block. So the two facts are one fact:
getting this field wrong breaks the interaction **and** silently breaks objective tracking that
depends on it, and neither failure is visible until someone right-clicks in game.

### You cannot attach behaviour to a *shipped* block

Asset ids come from filenames (§3), so adding interactions to `Furniture_Temple_Wind_Sign` means
shipping a file by that name — which **replaces that block for every player on the server**. There
is no additive route.

The working pattern is to **ship a new id and reuse the base game's art**: point `CustomModel`,
`CustomModelTexture` and `Icon` at the base pack's paths. **Cross-pack references resolve** (§1),
so the new block is visually indistinguishable from the palette around it while being a distinct
asset that overrides nothing. Act II's four inscription blocks are `Temple_Wind` signs and a
`Temple_Light` statue by exactly this route.

### `StartObjective` is item-only — and starting a line from a block needs Java

**This is the finding that qualifies §8's headline claim.** The shipped
`StartObjectiveInteraction` exists to stamp an objective UUID into a quest **item's** BSON
metadata: `firstRun` resolves the player, then calls `ctx.getHeldItem()` and reads metadata off the
result. Fired from a block interaction there is no held item, so it throws — **observed live**:

```
java.lang.NullPointerException: Cannot invoke
"...ItemStack.getFromMetadataOrNull(...)" because "itemStack" is null
  at ...objectives.interactions.StartObjectiveInteraction.firstRun(StartObjectiveInteraction.java:55)
```

and the consequence is not a logged warning. The interaction system removes the entity, and **the
player is disconnected**. A JSON-only authoring mistake ends someone's session.

This is an engine constraint, not a config error: `StartObjective` is item-only by design. The fix
is a custom interaction registered through `Interaction.CODEC` (§7).
`StormseekerStartLineInteraction` extends the shipped class so it **inherits the `Setup` field via
the parent codec** — `BuilderCodec.builder(…, StartObjectiveInteraction.CODEC)` — and overrides
`firstRun` to call

```java
objectiveTypeSetup.setup(Set.of(player.getUuid()), world.getWorldConfig().getUuid(), null, store);
```

directly, skipping every item-stamping step (`withMetadata`, `setObjectiveItemStarter`,
`setHeldItem`, `replaceItemStackInSlot`) — none of which applies when there is no item. Roughly
forty lines, and the JSON then reads `"Type": "StormseekerStartLine"` with the same `Setup` block
`StartObjective` takes.

---

## 7d. `Parent` inheritance carries nested structures, not just scalars

**Verified by running**, 2026-08-25, on server `0.5.9`. §5 quotes the schema's claim that a child
inherits the parent's properties "in the case of nested structures … within the structure". That
is a documentation string; this is the result.

A probe asset was authored declaring **only** `Parent` plus a name override — no model, no
textures, no interactions. In game it:

1. **Loaded** — no validation error at boot;
2. **Rendered as the inherited block model**, indistinguishable from its parent, so `CustomModel`,
   `CustomModelTexture`, `Icon` and the whole `BlockType` art block came through;
3. **Spoke the inherited `BlockType.Interactions.Use` chain when right-clicked** — the parent's
   nested interaction array ran, unmodified, from a child that never mentioned it.

Point 3 is the one that was genuinely open. Inheriting scalars is cheap; inheriting a **nested
behaviour chain** means a family of assets can share a mechanism, not just a look.

Act II is authored on this result: `Furniture_Stormseeker_Inscription_Base` carries the whole
`Temple_Wind` sign definition and each inscription is a handful of lines — `Parent`, a name key,
and its own `Use` chain. The three near-identical files collapsed to one base plus what differs.

**This matters well past one questline.** Act IV's Circle is 5–7 tier assets differing by a
number; the Class A–E materials share a backbone; a per-element questline family repeats the entire
asset set per element. Whether that work is authored as near-identical copied files or as a base
plus deltas turned on this field, and the answer is deltas.

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
| Line-level completion recorded | ❌ **not reproduced — see below** (this row read ✅ until 2026-08-25) |
| State persists to disk | ✅ `universe/players/<uuid>.json`, plus a `.bak` |

The persisted `ObjectiveHistoryComponent` keys history by **our** line id, nests both objectives
under it with completion counts and timestamps, records the reward per objective, and round-trips
the `"Category": "Stormseeker"` field we wrote in our own JSON.

### A questline needs no Java gameplay code — with one qualification

**Corrected 2026-08-25.** This paragraph previously read, without qualification:

> **So a questline needs no Java gameplay code.** Chaining, task tracking, completion, rewards, and
> persistence are all native.

The body of that is still true and still the important finding — chaining, task tracking,
completion, rewards and persistence are all native, and Act II shipped as JSON on that basis. But
the claim is **too strong at its edge**, and the edge is the first thing a new questline hits:
*how does the line start?*

- **Started by an item, or by a proximity `ObjectiveLocationMarker`** → no Java. The shipped
  `StartObjective` interaction covers the item case; markers start lines on their own.
- **Started by a block** → **Java is required.** `StartObjective` reads the held item and NPEs from
  a block interaction, disconnecting the player. Starting a line from a block needs a custom
  interaction registered through `Interaction.CODEC` — about forty lines, once, reusable by every
  later questline. Full detail and the fix in §7c.

So the honest form is: **a questline needs no Java for its gameplay, but its entry point may need
some.** That distinction is cheap to state and expensive to discover.

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

**Correction, 2026-08-25.** This claim's original evidence was that `/objective complete objective
<id>` left the line's `TimesCompleted` at `0`. That measurement no longer discriminates anything:
§8's play-through record shows a **genuine, cleanly-disconnected completion** also leaves line
`TimesCompleted` at `0` (the caller-side gate found there, cause unidentified). So "stays at `0`"
is not evidence admin completion differs from play — both do it.

The claim itself still holds, on different grounds: `/objective complete` reaches an objective's
*completed* state directly, skipping whatever the real advance path does along the way — task
progress events firing, `InventoryChangeAware` hooks running, whatever chain of calls a genuine
completion walks through before the state flips. Line-level bookkeeping (whatever produces a
correct `TimesCompleted` on the line, when it does) is coupled to that real advance path, not to
the objective's completed/not-completed state alone — an admin command is a shortcut for
*reaching* a state, not a substitute for exercising the path that gets there. Act II was played
rather than commanded for this reason; it is just that the specific number originally cited as
proof turned out not to distinguish the two after all.

### Line-level completion did not roll up — and this is unresolved

**Correction, 2026-08-25.** The paragraph above used to continue "A genuine play-through set it to
`1`," and the table above used to record line-level completion as ✅. **That was not reproduced.**

Act II was played honestly end to end — both objectives completed by doing the thing, no
`/objective complete` — and then the player **disconnected cleanly**, which the log confirms:

```
... left with reason: Disconnect - PlayerLeave
... Checking objectives for disconnecting player
```

The persisted `ObjectiveHistoryComponent` in `universe/players/<uuid>.json` then reads:

| Key | Value |
|---|---|
| `Objective_Stormseeker_TheChamber` → `TimesCompleted` | `1` |
| `Objective_Stormseeker_TheTrace` → `TimesCompleted` | `1` |
| `ActiveObjectiveUUIDs` | `[]` |
| **line** `ObjectiveLine_Stormseeker_TheSixthCircle` → `TimesCompleted` | **`0`** |

Both objectives recorded, nothing left active, and the line still zero.

**How far it was traced (read from bytecode, not resolved).** `ObjectivePlugin.objectiveCompleted`
→ `storeObjectiveLineHistoryData` → a per-player lambda → `ObjectiveLineHistoryData.completed(uuid,
data)`. `CommonObjectiveHistoryData`'s increment is **unconditional**, so whatever suppresses the
count is a **caller-side gate**, upstream of the increment. That gate was not identified.

**One difference from the earlier run is known but unconfirmed as the cause:** our line has no
`NextObjectiveLineIds`, and the spike's line — the one reported as rolling up — may have differed.
That is a hypothesis, not a finding, and it is stated here so the next person tests it rather than
rediscovering the symptom.

> **The practical consequence, which does not wait on the root cause.** Do **not** gate "has this
> player finished a line" on the line's `TimesCompleted`. Use the per-objective `TimesCompleted`
> inside `ObjectiveLineHistory.Objectives[]` — those **are** recorded correctly, on the same
> play-through, in the same file. Any design that treats line-level completion as the durable
> "finished" signal is building on a value observed to stay `0` after a real completion.

---

## 8b. What this still does *not* establish

**Reviewed 2026-08-25 against the Act II play-through.** Items the run closed are gone from this
list and recorded as findings above; items it opened are new here.

- **`WeatherTriggerCondition` firing.** The marker asset carrying it parses, validates and loads;
  nothing has yet stood in a storm and watched it trigger. Act II does not exercise it. This is the
  Stormseeker-specific mechanic, so it remains the most load-bearing untested thing here.
- **Why line-level `TimesCompleted` stays `0`** after a genuine, cleanly-disconnected
  play-through — see §8. Traced to a caller-side gate above
  `ObjectiveLineHistoryData.completed(...)`; the gate itself was not found. The
  `NextObjectiveLineIds` hypothesis is untested. **This is the largest open question the run
  produced**, because it invalidates the obvious "is this player done" check.
- **Worldgen from a mod pack.** `WorldStructureAsset` and `BiomeAsset` are authorable types (§5),
  but nothing has confirmed a *mod* pack can contribute a structure that the generator then places.
  Act II sidesteps it entirely — its ruin is hand-placed. Unproven, not disproven.
- **Assets-only packs (no `Main`) were not established** — see the callout in §3.
- **The native-objectives question this section used to leave open is now decided.** As of
  2026-08-25, `docs/architecture/native-objectives-migration-cost.md` records **Status: DECIDED** —
  adopt Hytale's native objectives for the Stormseeker content spine. This document's job was
  showing adoption is *possible*, cheap to author, and demonstrably works at runtime; that evidence
  is what the decision was made on. See the migration-cost doc for the decision record,
  `docs/architecture/questline-framework-adoption.md` (superseded by the decision) and
  `docs/stormseeker/stormseeker-canonical.md` (what is being built on it).

---

## 9. Bearing on `questline-framework-adoption.md`

That document specifies preconditions P1–P3 — three real code changes to `StormseekerWiring` that
activate dormant paths in the module with the least test coverage — before `core`'s
`QuestlineModule` SPI can be adopted by `mod/hytale`.

Those preconditions were written before this mechanism was known. `core`'s framework answers "how do
N questlines register into our plugin"; the native system answers "what a quest is and how progress
persists" — the half `quests/stormseeker` currently hand-rolls, including a phase state machine and
an on-disk progress store that `ObjectiveHistoryComponent` already provides per player.

**Update, 2026-08-25 — settled, not open.** This section used to end by telling the reader to
settle the native-objectives question before executing P1–P3. That question is now decided:
`docs/architecture/native-objectives-migration-cost.md` records **Status: DECIDED**, adopting the
native system for the Stormseeker content spine, and states explicitly that P1–P3 are **discarded,
not pending**. The reasoning above — that the native system materially shrinks what P1–P3 were
scaffolding — is what the decision was made on; it is preserved here as the historical argument,
not as an open question for the next reader to resolve.

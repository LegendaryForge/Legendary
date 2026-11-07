# Hytale Engine API — living reference

> **This is the accreting reference. The other integration documents are dated snapshots.**
>
> `hytale-capability-audit.md` audited build `0.5.9` on 2026-08-24 and has not been re-run since;
> the game is now `0.7.6` (this line said `0.7.2` until 2026-10-21, `0.7.3` until 2026-10-23, and `0.7.4` until 2026-11-07 —
> re-derived from `../../scripts/api-baseline/snapshots/`, whose newest entry is `0.7.6-c540fda1`,
> after session 27 recorded every one of its measurements against `0.7.4` while this line still
> said `0.7.3`), and its own §6 *Re-audit checklist* was never executed for `0.6.1`, `0.7.2`,
> `0.7.3`, `0.7.4` or `0.7.6`.
>
> **Read the build off the snapshots directory, not off this sentence** — `ls
> ../../scripts/api-baseline/snapshots/ | tail -1`. A hand-maintained version string in prose is
> the one thing here guaranteed to go stale, and it has now done so three times — the third while sitting in an unmerged PR whose own purpose was to stop asserting a build in prose.
> `docs/README.md` already warns that it is "unreliable per row". That document stays as
> history. **New findings land here**, one row at a time, each stamped with the build it was
> established against and how.
>
> **Why a living file rather than another audit.** A wholesale re-audit is a day of work that goes
> stale in 2–6 weeks, so it does not get re-run — which is exactly what happened twice. A per-row
> file can be updated by whoever happens to verify one row, which is the only cadence that matches
> how these facts are actually discovered: as a side effect of needing them.

**Evidence tiers.** Every entry carries one. They are not interchangeable:

| Tier | Means | Trust |
|---|---|---|
| **[measured]** | executed against a running server, output recorded | strongest |
| **[compiled]** | our code calls it and `:mod:hytale:compileJava` resolves it against the real jar | strong — proves existence and shape, not behaviour |
| **[bytecode]** | a method body, constant or call site read out of the jar's bytecode | strong for logic and control flow — proves what the code does and which path reaches it, not that it ran |
| **[javap]** | signature read, never called | **a hypothesis** |
| **[asset]** | read out of `Assets.zip` | strong for structure, says nothing about runtime |

The project's standing method rule is that a signature is a hypothesis and a probe is confirmation.
A `[javap]` row has not been confirmed. Do not design on one without saying so.

**`[bytecode]` was added 2026-11-07 (session 30), and the gap it fills was demonstrable rather than
theoretical.** The locality section below had a decompiled *method body*, two constants and a
`x >> 5` implementation all filed as `[javap]` — a tier whose own definition is "signature read,
never called", which is literally false for every one of them. The distinction is not pedantic: a
signature says a method exists, while a call site says *which path invokes what*, and claim 2 below
is answered by exactly that and by nothing weaker short of execution. `[bytecode]` is still static
and still not `[measured]`; what it rules out is reading a control-flow fact as a mere hypothesis.

---

## Positional terrain reads

**Established 2026-10-21 against `0.7.2`.** These are the signals the residue network's terrain
derivation would key on — see `../superpowers/specs/2026-10-20-residue-terrain-derivation-design.md`
§5 and §9.

### `SectionReader` — arbitrary-coordinate reads

`com.hypixel.hytale.server.core.universe.world.accessor.SectionReader`, constructed from
`world.getChunkStore()`. [compiled]

| Method | Returns | Notes |
|---|---|---|
| `getEnvironment(x, y, z)` | `int` | environment index; resolve a name via `Environment.getAssetMap().getAsset(i)` |
| `getBlock(x, y, z)` | `int` | **`0` means no block — air or void.** Measured 0.7.4 via `/blockidprobe`, three known-answer positions in one column: the player's own feet level `0`, open sky above terrain `0`, bedrock depth `445`. Corroborated by mechanism: at `y=16` the same column read `getBlock=0` with `getFluidId=8`, so a fluid occupies a position `getBlock` calls empty — a real block type could not co-occupy. Nonzero values seen: 445, 1530, 1599, 2654. [measured, 0.7.4] |
| `getFluidId(x, y, z)` / `getWorldFluidId(x, y, z)` | `int` | a **`Fluid` asset** index — resolve via `Fluid.getAssetMap().getAsset(i)`. **Not** a block-type id; see the namespace row below. Both accessors agreed at every position measured so far |
| `getSupportValue(x, y, z)` | `int` | |
| `hasStorageInMemory(x, y, z)` | `boolean` | **the loaded/unloaded discriminator** |
| `sectionAt(x, y, z)` | `Ref<ChunkStore>` | coordinate → section ref |
| `canHoldY(y)` | `boolean` | the engine's own world-height predicate |
| `supportsCubicSections()` | `boolean` | V1 / V2 generator shape |

This is the **ergonomic positional read**, and it is not entity-anchored. Note the contrast with
`WeatherConditionProbeCommand`, which reads environment via `transform.getSectionRef()` — that proves
"anywhere inside the section a player occupies", which is a narrower claim and was briefly mistaken
for the general one.

### Fluids live in two namespaces, and the wrong one silently scores zero

**Established 2026-10-21 against `0.7.3`.** [measured]

`getFluidId` returns an index into the **`Fluid` asset** map, whose ids are the filenames under
`Server/Item/Block/Fluids/`:

| Fluid asset | Shape |
|---|---|
| `Water_Source`, `Lava_Source` | the body — `MaxFluidLevel: 1` |
| `Water`, `Lava` | the flowing child — `Parent: <X>_Source`, `MaxFluidLevel: 8`, `CanDemote: true` |
| `Water_Finite` | `Parent: Water_Source` with `Ticker.Type: Finite` — contained water |
| plus `Poison`, `Slime`, `Slime_Red`, `Tar`, `Tar_Void`, `Fire`, `Dungeon_Fire_NoSpread` and their `_Source` variants | 17 files; the boot log counts `Fluid: 19` |

**Measured**, two positions, both with a client connected:

| Standing in | Position | Reported |
|---|---|---|
| a river | `-1001,113,150` | `fluid=Water_Source getFluidId=2 getWorldFluidId=2` |
| flowing lava | `-522,164,284` | `fluid=Lava getFluidId=14 getWorldFluidId=14` |

Note the second is the **flowing** child, not `Lava_Source`. A required-fluid set holding only the
`_Source` variants would miss fluid a player is standing in.

**The trap.** `Server/BlockTypeList/Fluids.json` is a *different* namespace — a block-type list
naming `Fluid_Water`, `Fluid_Lava`, `Fluid_Poison`, `Fluid_Slime`, `Fluid_Slime_Red`, `Fluid_Tar`.
Those names are real, so verifying a fluid id against that file **passes** while the runtime never
produces them. `HytaleDerivationRules` required `Fluid_Lava` and `Fluid_Water` on exactly that
basis, which made both `Physical` groundings — Fire and Water, the only two with a required fluid —
unable to match anything, while presenting as correct strictness. Corrected the same day.

Nothing in the build could catch it: `:core` treats fluid names as opaque strings by design, and
`SiteScorerTest` supplies the same invented name to both the grounding and its fake terrain, so the
suite is self-consistent and namespace-blind. This is the third instance of *the asset tree and the
runtime API are different surfaces, and neither is a subset of the other* — see the 2026-10-21
`#surprise` entry in the vault's `Hytale_Observations.md`.

### Measured runtime values

From `/signalprobe` on a headless `0.7.2` server, 2026-10-21
(`.scratch/hytale-server/signalprobe-run.log`): [measured]

```
supportsCubicSections: false            (V1 column shape)
canHoldY: y-1=false y0=true y160=true y319=true y320=false y400=false
loaded survey at y=64, offsets 0..4096: all out
absent case at 2,000,000: hasStorageInMemory=false, sectionAt null/invalid
```

**Client-attached run, same build, player at `-982, 121, 103`:** [measured]

```
[StephKBart] hasStorageInMemory=true  sectionAt=valid
  getEnvironment=78 (Env_Zone1_Plains)
  BlockSection=present  hasGlobalLight=true  hasLocalLight=true
  getSkyLight global world-coords=15  section-local=15
  getSkyLight local  world-coords=15                      max=15
  y+12: env=78  skyGlobal=9/9
  y-12: env=78  skyGlobal=0/0
cost over 20000 reads: getEnvironment 99ns/read, getSkyLight 34ns/read
SIGNALPROBE_VERDICT: PASS | env=READ sky=READ absent=ok
```

**So both reads work at arbitrary coordinates inside a loaded section, and both are cheap** —
99 ns and 34 ns, i.e. order 10⁷ reads/second. §9 q1's "cheaply" is answered with room to spare; a
derivation can afford to sample these per-position rather than caching.

Three caveats, because the verdict line is cleaner than the data:

- **The coordinate form is *likely* masked, not confirmed.** World and section-local agreed — but at
  **15**, which is the maximum and ubiquitous across the open surface, so a wrongly-addressed cell
  would very probably have returned 15 too. The informative agreement is `y+12: 9/9`. Treat
  world-coordinate addressing as probably-safe and re-check at an intermediate value before relying
  on it.
- **Global and local light are not yet discriminated.** They returned identical values at all three
  samples (15/15, 9/9, 0/0), which is exactly what is expected with no player-placed light nearby. So
  **§5's claim that sky light is immune to a player lighting a cave remains unverified** — it needs a
  reading taken beside a torch, where the two should diverge.
- **`y+12` reading 9 is unexplained.** Sky light should not *fall* twelve blocks above a position that
  reads 15 in open air. Either there is cover above that spot, or the addressing is wrong in a way the
  saturated surface reading hid. Open.

Sky light does vary usefully with depth (15 surface → 0 at −12), so it is a real terrain signal
regardless of how those three resolve.

### Sky light is time-independent, and environment is 3D

Further client-attached readings, same session, 2026-10-21: [measured]

| Reading | In-game time | Position | `getEnvironment` | sky at player | sky at y+12 | sky at y−12 |
|---|---|---|---|---|---|---|
| surface 1 | unknown | `-982,121,103` | 78 `Env_Zone1_Plains` | **15** | 9 | 0 |
| surface 2 | **night** | `-990,121,79` | 78 `Env_Zone1_Plains` | **15** | 12 | 0 |
| surface 3 | **midday** | `-997,120,87` | 78 `Env_Zone1_Plains` | **15** | 15 | 0 |
| underground | night | `-931,64,135` | 106 `Env_Zone1_Caves_Plains` | **0** | 0 (env 23 `Env_Zone1_Caves`) | 0 (env 23) |

**Sky light is sky *access*, not current brightness.** 15 at an open-sky surface position at **both**
night and midday, with time changed deliberately via `time set midday` between readings as the single
varied input. This is what the residue design needs: a channel that is identical for every player and
constant across the day/night cycle. A brightness-modulated value would shift every derived residue
figure at dusk.

**Environment varies with Y — it is not a 2D biome map.** Underground at `y=64` the environment is
`Env_Zone1_Caves_Plains` (106) while twelve blocks above and below, at the same x/z, it is
`Env_Zone1_Caves` (23). At the surface all three samples returned the same `Env_Zone1_Plains`. So
environment natively distinguishes surface from underground and shades between cave types vertically,
which means it already supplies some of what a terrain derivation would otherwise compute.

### Sky light is immune to player-placed light — measured, with a verified control

Client-attached readings underground in `Env_Zone1_Caves_Volcanic_T1`, 2026-10-21: [measured]

| Position | light source | `getSkyLight` | `rgb` (r·100+g·10+b) | `packed` |
|---|---|---|---|---|
| `-1002,50,107` | beside a candle | **0** | 987 (r9 g8 b7) | 1929 = `0x0789` |
| `-985,53,97` | beside a torch | **0** | 987 (r9 g8 b7) | 1929 = `0x0789` |
| `-986,51,96` | ~2 blocks further off | **0** | **765** (r7 g6 b5) | **1383** = `0x0567` |

**Sky stays 0 while the colour channels track distance from the light.** That is §5's *"immune to a
player lighting a cave"* claim, measured — and the control that makes it mean anything is itself
verified, which took one extra reading.

**Why the extra reading was needed.** The candle and torch readings were byte-for-byte identical at
two different positions with two different sources, and `9, 8, 7` is a descending sequence — which
looks far more like a constant than a measurement. Had it been one, "rgb is non-zero" would not have
been evidence that light reached that cell, and the sky-0 reading would have proved nothing. Stepping
~2 blocks away moved it to `765`, so the read is genuinely per-cell. **The identical pair was a
coincidence**: both sources are warm lights with the same profile and the player was at equivalent
distance from each.

Two further facts from the same readings:

- **Light colour profile is `r = g+1 = b+2`** for both candle and torch, and attenuation reduced all
  three channels equally (−2 over ~2 blocks). So these sources are near-white with a slight warm bias,
  not strongly tinted.
- **Packed nibble layout is `0x0bgr`** — sky in the high nibble, then blue, green, red. `0x0789` with
  r9 g8 b7, and `0x0567` with r7 g6 b5. Note this is **not** independent corroboration of the sky
  reading: `getBlockLight` and the three colour getters read the same underlying data, so their
  agreement shows self-consistency only. An earlier version of this section claimed independence; it
  was wrong.
- **`getGlobalLight()` and `getLocalLight()` are indistinguishable on read** — identical in sky, every
  colour channel and the packed value across **six** readings, now including three beside a live block
  light. So the distinction is not about read content; the write/invalidation pairs
  (`setLocalLight`/`setGlobalLight`, `invalidateLocalLight`/`invalidateGlobalLight`) are the likelier
  locus. **Either accessor is safe for a derivation that only reads.**

**Still open after these readings:**

- **The `y+12` series (9 → 12 → 15) is confounded** and is *not* evidence of time-modulation. Position
  changed along with time across the three readings, so the variation cannot be attributed; overhead
  terrain differing per spot is the likelier cause. Recorded unexplained rather than explained — one
  reading with position held fixed across a time change would settle it.
- ~~Which of global/local carries sky.~~ **Answered above** — indistinguishable on read across six
  readings including three beside a live block light.

Three further facts worth keeping:

1. **The world is 320 blocks tall and starts at y=0**, confirmed by the engine's own predicate rather
   than inferred from `EnvironmentSection[10] × 32`. `y319` true with `y320` false, and `y-1` false.
2. **`supportsCubicSections()` is `false`** — the V1 column shape is still live. This is a **better
   observation point than the boot-log line** that `[[Workspace_Deferrals]]` #18 currently watches for
   the V1→V2 handover, because it is a direct runtime read rather than a string match on startup
   output.
3. **A headless server holds no chunk region at all** — nothing loaded at any surveyed offset. So the
   practical bound on "on-demand derivation works in loaded chunks" is that with no player connected
   the bound is **empty**. Derivation cannot pre-compute a region before a player arrives.
4. **The loaded region is local to a player, and small.** With a player connected at `-982, 103`, the
   survey offsets `0..4096` on the origin diagonal were *still all unloaded* — only the player's own
   neighbourhood is in memory. "Loaded" is not "the explored world"; it is a working set around
   whoever is online.

### Sky light

`BlockSection` (`…world.chunk.section.BlockSection`) is a `Component<ChunkStore>`: [compiled]

| Method | Returns |
|---|---|
| `getGlobalLight()` / `getLocalLight()` | `ChunkLightData` |
| `hasGlobalLight()` / `hasLocalLight()` | `boolean` |

`ChunkLightData` carries **four** 4-bit channels — `RED_CHANNEL`, `GREEN_CHANNEL`, `BLUE_CHANNEL`,
`SKY_CHANNEL` — with `MAX_VALUE` and: [compiled]

- `getSkyLight(int x, int y, int z)` → `byte` — **public.**
- `getBlockLight(x, y, z)` → `short` (all channels packed), plus per-channel RGB getters.

> **Two unknowns, both still open.** Whether these take world or section-local coordinates — the
> accessors appear to mask internally, since `EnvironmentSection.get` works with world coordinates,
> but "appear to" is not verified. And **which of global/local carries sky**, which matters because
> §5's whole argument for sky light is that it is deterministic and immune to a player lighting a
> cave — a claim that can only hold for one of the two. `/signalprobe` reads both forms and both
> sources side by side; it needs **a connected client**, because a headless server loads nothing.

The engine also ships `/lighting get <pos>` (`LightingGetCommand`), an independent positional read
worth comparing against.

### Environment, the other access paths

- `EnvironmentChunk` — `Component<ChunkStore>`, with `sliceToSection(int)`. [javap]
- `EnvironmentColumn` — `get(y)` → environment id, `getMin`/`getMax`, `MIN`/`MAX`. [javap]
- `EnvironmentSection.get(x, y, z)` → `int` — what `WeatherConditionProbeCommand` uses. [compiled]
- `EnvironmentProvider.evaluate(Context)` → `int`, where `Context` holds **only** an
  `org.joml.Vector3i`. A pure function of position, so it would answer for *unloaded* coordinates.
  **But it lives in `builtin/hytalegenerator` (998 classes), and `HytaleWorldGenProvider` — the
  generator this world actually runs — references that package zero times.** [javap] So it is not
  reachable for the live world today, and becomes reachable if/when deferral #18's handover happens.

---

## Creature placement — the engine's own spatial predicate

**Established 2026-10-21 against `0.7.2`.** [javap] + [asset]

`com.hypixel.hytale.server.spawning.assets.spawns.config.NPCSpawn`:

| Method | Returns |
|---|---|
| `getEnvironments()` / `getEnvironmentIds()` | `String[]` / `IntSet` |
| `getLightRange(LightType)` | `double[]` |
| `getDayTimeRange()` / `getMoonPhaseRange()` | `double[]` / `int[]` |
| `getNPCs()` | `RoleSpawnParameters[]` |

`RoleSpawnParameters` adds `getSpawnBlockSet()`, `getSpawnBlockSetIndex()`, `getSpawnFluidTagIndex()`,
`getWeight()`, `getFlockDefinitionId()`.

`LightType` is an enum: `Light`, **`SkyLight`**, `Sunlight`, `RedLight`, `GreenLight`, `BlueLight`.

So the engine places creatures by

```
environment  ×  sky-light range  ×  block set  ×  time of day / moon phase
```

which is the **same three spatial inputs** a terrain-derived residue field would use. That is why
residue keyed to those signals agrees with creature placement by construction. Worked through in the
residue spec §5.

### Asset schema for placement

- `Server/NPC/Spawn/**` — 523 files. Shape: `{"Environments": [...], "NPCs": [{"Id", "Weight",
  "SpawnBlockSet"}], "DayTimeRange": [...]}`. [asset]
- `Server/Drops/NPCs/**` — 290 files; what each creature yields. [asset]
- `Server/Environments/**` — 134 files; keys observed are `Tags`, `Parent`, `WeatherForecasts`,
  `SpawnDensity` (a **scalar**, not a creature list), `WaterTint`. [asset]
- `Server/Abilities/Elements/*.json` — five files, each `{"DamageCause": "X"}`. `DamageCause` values
  across **all** shipped JSON are exhaustively `Fire, Earth, Lightning, Water, Wind`. [asset]

> **Do not read an asset list as a taxonomy.** The game ships four partially-overlapping elemental
> rosters and no two are the same list, so the assets cannot answer "which elements does Hytale have"
> — only "keyed to what". Catalogued in `../setting/hytale-orbis-setting-brief.md` §3, and the trap is
> written up in `../../CLAUDE.md` § "Lore first".

---

## Worldgen feature locality — there is no 3x3-style bound

**Established 2026-10-23 against `0.7.4`, confirmed by execution 2026-11-07 against `0.7.6`.** This
answers the question session 27 left as *"the most valuable unknown left"*: what bounds a worldgen
feature's footprint, and therefore whether a residue network can be derived at generation time.

> The heading of this section read *"All [javap] — signatures read, nothing executed"* until
> 2026-11-07. Both halves were wrong by then: the measured rows below had landed, and the rows that
> remain static are `[bytecode]` — bodies and constants, not signatures. Re-tiered in session 30.

**The answer is that nothing bounds it, because features are re-derived per chunk from a
deterministic point generator rather than written across chunk borders.** Minecraft's 3x3-chunk
feature bound has no analogue here.

### `ChunkGenerator` computes its own feature reach

`com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator` — the live generator, reached via
`world.getWorldConfig().getWorldGenProvider().getGenerator()`. Its constructor ends:

```java
int extents = getLargestFeatureChunkExtents(zonePatternProvider, CHUNK_BOUNDS_PADDING);
this.minChunkCoord = -67108864 + extents;
this.maxChunkCoord =  67108863 - extents;
```

and the method it calls, decompiled from bytecode:

```java
private static int getLargestFeatureChunkExtents(ZonePatternProvider provider, int padding) {
    double max = 0.0;
    for (Zone zone : provider.getZones()) {
        if (zone.caveGenerator() != null) {
            for (CaveType t : zone.caveGenerator().getCaveTypes()) {
                max = Math.max(max, t.getMaximumSize());               // blocks
            }
        }
        for (Biome b : zone.biomePatternGenerator().getBiomes()) {
            if (b.getPrefabContainer() == null) continue;
            for (PrefabContainerEntry e : b.getPrefabContainer().getEntries()) {
                max = Math.max(max, e.getPrefabPatternGenerator().getMaxSize());   // blocks
            }
        }
    }
    return ChunkUtil.chunkCoordinate(MathUtil.ceil(max)) + padding;    // -> CHUNKS, + 16
}
```

| Fact | Value | Tier |
|---|---|---|
| `ChunkGenerator.CHUNK_BOUNDS_PADDING` | **16**, and it is **chunks = 512 blocks** | [bytecode] |
| `ChunkUtil.chunkCoordinate(int)` | `x >> 5`, so blocks -> chunks | [bytecode] |
| `ChunkUtil.SIZE` | 32 | [javap] |
| World chunk range before clamping | `-67108864 .. 67108863` | [bytecode] |
| `ChunkUtil.minBlock(int)` / `maxBlock(int)` | `c << 5` and `(c << 5) + 31` | [bytecode] |

So the engine derives its feature reach from whatever the largest **declared** feature size is, and
reserves 16 chunks on top. The padding's *only* use is clamping world bounds so a feature near the
edge cannot overflow — it is not a per-chunk write budget.

### The native placement model is the one our anchor design arrived at independently

`com.hypixel.hytale.server.worldgen.prefab.PrefabPatternGenerator` fields, and the correspondence is
close enough to be worth stating plainly:

| Engine field | Our equivalent |
|---|---|
| `IPointGenerator gridGenerator` | `CandidateLattice` |
| `int exclusionRadius` | `AnchorParameters.suppressionRadius` |
| `int maxSize` | measurement + suppression footprint |
| `int seedOffset` | the per-element hash domain we do **not** have (see below) |
| `heightCondition`, `mapCondition`, `parentCondition`, `prefabPlacementConfiguration` | `ElementGrounding`'s predicates |

And `com.hypixel.hytale.procedurallib.logic.point.IPointGenerator` is queried **per region, from a
seed**:

| Method | Meaning |
|---|---|
| `collect(int seed, double minX, double minZ, double maxX, double maxZ, PointConsumer2d)` | every point in a rectangle — this is `CandidateLattice.candidatesWithin` |
| `nearest2D(int seed, double x, double z)` / `nearest3D(...)` | nearest point(s) to a position |
| `transition2D` / `transition3D` | boundary queries |
| `getInterval()` | point spacing — this is `cellSize` |

A generator that can be asked "which points fall in this rectangle" is a generator each chunk can
query independently over its own bounds expanded by a declared reach. That is what makes a declared
size necessary at all, and it is why no cross-chunk write is needed.

> **This read "expanded by `maxSize`" until 2026-11-07, and the term was wrong.** The inference
> itself was right — the next section has the actual call — but the expansion is by
> `PrefabContainerEntry.getExtents()`. `maxSize` feeds the *world bound*; `getExtents()` feeds the
> *per-chunk query region*. Two quantities, two uses, and session 29 had already measured that they
> are not interchangeable without knowing what the second one was for.

### What this settles, and what it does not

**Settles.** Generation-time derivation of a bounded-footprint feature is the engine's own model, not
a thing we would be working against. At the corrected sizing (`suppressionRadius` 256,
`measurementRadius` 128 — see `../superpowers/specs/2026-10-23-residue-sizing-re-derivation.md`) our
footprint is **384 blocks = 12 chunks**, which sits inside the 16-chunk padding the engine already
reserves. The architecture fork session 27 posed — *"a run-ahead derivation service, or bounding the
footprint"* — is answered by neither: declare the footprint and derive per chunk, as the engine does.

**Confirmed by execution 2026-11-07 against `0.7.6`**, by `/featurelocality` — design and full
results in `../superpowers/specs/2026-11-07-feature-locality-confirming-probe-design.md`. The rows
above were written `[javap]`; these two are now `[measured]`.

| Fact | How | Tier |
|---|---|---|
| The world bound **is** `(largest declared feature blocks >> 5) + CHUNK_BOUNDS_PADDING` | `minChunkCoord` is private, but **`isChunkOutsideGeneratableArea(int,int)` is public** — binary-searching it recovered `minChunkCoord=-67108837`, `maxChunkCoord=67108836`, i.e. **extents 27 chunks, symmetric**, against `(370 >> 5) + 16 = 27` predicted from the public declared sizes. MATCH | **[measured, 0.7.6]** |
| `CHUNK_BOUNDS_PADDING = 16` chunks is a real term in that computation | falls out of the match above | **[measured, 0.7.6]** |
| A real feature's blocks **do** lie in chunks other than its own | `UniquePrefabEntry.getLowBound*/getHighBound*` give a placed prefab's actual box: `Temple` is `x[-1117..-1032]` chunks `[-35..-33]`, `z[262..380]` chunks `[8..11]` — 86x118 blocks, crossing 2 and 3 chunk borders | **[measured, 0.7.6]** |
| No aggregate the live generator exposes clamps reach | 283 aggregates over 28 zones and 1,429 patterns, **0 clamping**, each compared against *its own scope* | **[measured, 0.7.6]** |

So the reach claim is no longer a hypothesis: nothing gates a feature's footprint below what it
declares, and features demonstrably span chunk borders.

**Three new rows the probe established on the way**, all `[measured, 0.7.6]`:

- **`PrefabContainerEntry.getExtents()` is only callable on a worldgen thread.** It resolves through
  the **thread-local** `ChunkGeneratorResource`, whose `chunkGenerator` back-reference is set only on
  the generator's own pool threads; from a command thread every one of 1,429 calls threw
  `NPE: Cannot invoke "ChunkGenerator.getPrefabLoadingCache()" because "this.chunkGenerator" is null`.
  `ChunkGenerator.execute(int, IntObjectFunction)` submits onto that pool and the read then succeeds
  1,429/1,429.
- **`ChunkGenerator.execute` is `@Deprecated` and marked FOR REMOVAL on `0.7.6`.** The only known
  route to a worldgen thread is therefore on a removal path.
- **`getExtents()` is not comparable to `getMaxSize()`.** It ranges 0..131 and came out *above* its
  entry's declared `maxSize` for 1,049 of 1,429 entries, *below* for 361 and *equal* for 19. A split
  in both directions is two different quantities; `getLargestFeatureChunkExtents` never consults it.
  **Its role was found in session 30 and is in the next section: it is the per-chunk `collect`
  region's expansion term.** Read the original sentence narrowly — it is true of
  `getLargestFeatureChunkExtents` specifically, and was recorded when nothing was known to consult
  `getExtents()` at all, which made it easy to read as "it does nothing".

### `IPointGenerator.collect` is what the per-chunk prefab pass calls (claim 2)

**Established 2026-11-07 against `0.7.6`** (session 30) —
[`../superpowers/specs/2026-11-07-collect-call-edge-claim2-design.md`](../superpowers/specs/2026-11-07-collect-call-edge-claim2-design.md).

```java
// ChunkGeneratorExecution, during chunk generation ->
public static void populate(int seed, ChunkGeneratorExecution execution) {
    ChunkGenerator.getResource().prefabPopulator.run(seed, execution);
}

public void run(int seed, ChunkGeneratorExecution execution) {
    this.worldSeed = seed;
    this.uniquePrefabs = Objects.requireNonNullElse(
            execution.getChunkGenerator().getUniquePrefabs(seed), EMPTY_UNIQUE_PREFABS);
    collectBiomes(seed, execution);
    collectPrefabs(seed, execution);
    invalidatePrefabs();
    generatePrefabs(seed, execution);        // places from `prefabs`
    generateUniquePrefabs(seed, execution);  // the OTHER placement system
    // ... then clears biome, entry, execution, uniquePrefabs, biomes, prefabs, invalidated
}

private void collectPrefabs(int seed, ChunkGeneratorExecution execution) {
    for (int i = 0; i < biomes.size(); i++) {
        Biome biome = biomes.get(i);
        PrefabContainer container = biome.getPrefabContainer();
        if (container == null) continue;
        int ordinal = 0;
        for (PrefabContainerEntry entry : container.getEntries()) {
            this.biome = biome;
            this.entry = entry;
            this.execution = execution;
            this.prefabSeed = HashUtil.hash(seed, biome.getId(), ++ordinal);
            entry.getPrefabPatternGenerator()
                 .getGridGenerator()
                 .collect(seed,
                          ChunkUtil.minBlock(execution.getX()) - entry.getExtents(),
                          ChunkUtil.minBlock(execution.getZ()) - entry.getExtents(),
                          ChunkUtil.maxBlock(execution.getX()) + entry.getExtents(),
                          ChunkUtil.maxBlock(execution.getZ()) + entry.getExtents(),
                          this::collectPrefab);
        }
    }
}
```

| Fact | How | Tier |
|---|---|---|
| The per-chunk prefab pass calls `IPointGenerator.collect` | call site in `PrefabPopulator.collectPrefabs`, reached as `ChunkGeneratorExecution` → `populate` → `run` → `collectPrefabs` | **[bytecode, 0.7.6]** |
| The region is **the chunk's own bounds expanded by `entry.getExtents()`** | the call's four coordinate arguments, with `minBlock(c) = c << 5` and `maxBlock(c) = (c << 5) + 31` | **[bytecode, 0.7.6]** |
| `ChunkGenerator.getResource()` is public static and `ChunkGeneratorResource.prefabPopulator` is `public final`, and both are populated in the live world | read on a worldgen thread by `/featurelocality` Part 3 | **[measured, 0.7.6]** |
| **Every** prefab entry answers that call. 1,429 entries x 2 chunks = **2,858 attempted, 2,858 grid generators present (0 null, 0 threw), 2,858 usable intervals, 2,858 `collect` calls completed (0 threw), 120,369 points returned** | the populator's own arguments, reproduced at chunk `(0,0)` and `(100,-37)` | **[measured, 0.7.6]** |
| Worked example: `Zone4_Tier4`, `interval=333`, **`extents=131` against `maxSize=100`**, chunk `(100,-37)` → x region `[3200 - 131 .. 3200 + 31 + 131] = [3069..3362]`, width **294 = 32 + 2x131** | same run | **[measured, 0.7.6]** |
| Pattern prefabs and unique prefabs are **two placement systems**. `UniquePrefabGenerator` has no `IPointGenerator` — not on it, not on its `UniquePrefabConfiguration`, which places by `anchor` + `maxDistance` + `maxAttempts` rejection sampling against a `Random`. They are coupled only by suppression: `collectPrefab` calls `isWithinUniquePrefabExclusionRange(x, z, patternGenerator, uniquePrefabs)` | `javap` of both config types, plus the `run` body above | **[bytecode, 0.7.6]** |

**What this does not establish.** That the engine *used* those points to place something in a given
chunk. The test for that is containment — every pattern prefab placed in a chunk sits at one of the
points `collect` yields for its expanded region — and it is **blocked on enumerating placed prefabs,
which no public API appears to offer**: `PrefabContainerEntry` holds no instance records, `run` clears
its candidate list on the way out, and `getPrefabLoadingCache()` is a type-keyed template cache
(`Map<WorldGenPrefabSupplier, PrefabBuffer>`) rather than a placement record. Recorded as blocked, not
as unrun work with a known method.

> **The instrument session 29 named for this job would have given a confident wrong answer.** It was
> `UniquePrefabEntry.getPosition()` against `collect()` output — which, per the last row above,
> compares two different placement systems and therefore disagrees by construction. It would have
> printed `CLAIM2_REFUTED`, and it would have been the fourth cross-scope comparison in this one
> probe's history. Caught by reading the two config types before building it.

**Still unconfirmed:**

- Whether a mod can register into this path, or only read it. **Deliberately out of scope**, not
  merely unrun: the 2026-10-21 adoption derives residue in `:core` behind an injected terrain port
  and authors no worldgen JSON, and `Workspace_Deferrals` #18 records the `WorldGenModifier` route as
  V1-only — the branch the operator ruled against investing in. It reopens only if that decision
  does.

> **The probe's own first answers were wrong four times**, each time reporting a confident verdict
> rather than an error: `CLAIM1_REFUTED` twice from comparing quantities across incompatible scopes,
> `claim2 MEASURED` off n=8 as the best of four candidates, and `crossing OBSERVED` from two
> correlated columns against a 20% noise floor. The spec records all seven corrections. Read it
> before extending this section — the failure mode is specific and it recurs: **a measurement that
> does not test what it claims, and that fails by answering.**

### One asymmetry worth carrying to the anchor model

`PrefabPatternGenerator` has a `seedOffset` per generator. `AnchorSelector.positionHash` is
`mix(worldSeed, x, z)` with **no element term**, so two elements that tie across the same candidates
select identical anchors — recorded in
`../superpowers/specs/2026-10-23-residue-sizing-re-derivation.md` §5. The engine having a per-pattern
seed offset is independent evidence that the per-element domain is the expected shape, not a nicety.

**And session 30 found the engine's actual per-scope derivation, which is not `seedOffset`.**
`collectPrefabs` computes `prefabSeed = HashUtil.hash(worldSeed, biome.getId(), ordinal)`, and
`collectPrefab` then reseeds a `FastRandom` from `HashUtil.hash(prefabSeed, pointX, pointZ) *
1609272495L`. So the **lattice itself is queried with the plain world seed** and per-entry variation
comes from that derived seed, not from `seedOffset`. [bytecode, 0.7.6]

That is evidence about the *shape* of one option for the open anchor-hash decision —
`hash(seed, scopeId, ordinal)`, scope rather than position carrying the identity — and nothing more.
It does not make the decision.

---

## How to add a row

1. Establish the fact at the strongest tier you can. Prefer calling it over reading it.
2. Add it here with its **tier** and the **build** it was established against.
3. If a design document needed the fact, link from the design *to here* rather than restating it —
   designs get superseded (four residue specs were bannered as superseded on 2026-10-21) while an API
   fact outlives the design that discovered it. **That mismatch is why this file exists.**
4. If you find an existing row is wrong for a newer build, correct it in place and say what it said
   before. A silently-corrected row teaches that the file is unreliable; a visibly-corrected one
   teaches what changed.

*Created 2026-10-21, for `0.7.2`. Prompted by the observation that this project had been recording
engine findings inside design specs and lore documents — surfaces that are either superseded or
version-stamped for a different purpose — while the one document named as the engine reference sat two
builds behind.*

---

## The Void event

**Established 2026-10-21 against `0.7.2`.** Answers §9 q3 and q4 of
`../superpowers/specs/2026-10-20-residue-terrain-derivation-design.md`, which both blocked §6.
**Nothing here is `[measured]`** — the conclusions are read off assets and signatures. The probe that
would confirm them is named at the end.

### It is Fragment-scoped, not world-scoped — and that is the headline

The config is **not** global. It hangs off a *gameplay config*, under
`Plugin.Portal.VoidEvent.Stages`, and the two configs that carry one are both portal destinations:
[asset]

| Gameplay config | `Death.RespawnController` | Shape |
|---|---|---|
| `Server/GameplayConfigs/Portal.json` | `ExitInstance` | thin instance config, `Parent: Default` |
| `Server/GameplayConfigs/PortalsGoblins.json` | `WorldSpawnPoint` | full world config — Gathering, Respawn, Player, Spawn … |

Eight gameplay configs ship in total; only those two define a `VoidEvent`. So the Void event is
content **of a place you travel to**, not weather that arrives at home. `portals.discoverySubtitle =
"Fragment of Orbis"`, and `PortalsGoblins` is `portals.goblinBreach` — *"The Guttering Hollows …
Chaos stirs in the ruins of a lost Enkindled settlement."* [asset]

### Staging — the numbers §9 q4 asked for

`PortalsGoblins.json`, six stages, seconds **into the instance**: [asset]

| `SecondsInto` | `ForcedWeather` | Sound | Title |
|---|---|---|---|
| 120 | `Cave_Goblin_Void_Event_Faint` | `SFX_Void_Encroaching` | `portals.voidSurge.title`, style `VoidEviction`, 5s |
| 240 | `Cave_Goblin_Void_Event_Mild` | `SFX_Void_Encroaching_Whispers` | |
| 390 | `Cave_Goblin_Void_Event_Light` | `SFX_Void_Encroaching_Whispers` | |
| 540 | `Cave_Goblin_Void_Event_Heavy_Surge` | `SFX_Void_Encroaching_Whispers` | |
| 690 | `Cave_Goblin_Void_Event_Heavy_Late` | | |
| 780 | `Cave_Goblin_Void_Event_Storm` | `SFX_Void_Encroaching_Whispers` | |

`Portal.json` is shorter and spawns the invasion: stage 1 at **420s** forces
`Portals_Void_Event_Light` and spawns `VoidInvasion_Portal` blocks via three beacons
(`Portal_Void_Event_Minor` / `_Major` / `_Critter`) with `Void_Dragon_Effects`; stage 2 at **540s**
forces `Portals_Void_Event_Intense`. [asset]

**There is no terminal stage and no duration field.** The event escalates and then the Fragment
*ejects* you: `portals.goblinBreach.closing.title = "The Veil of Void has closed once more"`,
subtitle *"Your presence is no longer permitted"*, and `VOID_EVICTION` is a first-class title style
(*"The themed title used by Void Eviction events"*). [asset]

> **So §6's question resolves as: the window is the visit.** Roughly two to thirteen minutes of
> escalating pressure, ending in eviction rather than expiry. A harvest loop built on this is
> bounded by being thrown out, not by a timer running down — which is a stronger pacing device than
> the question anticipated.

### Shapes worth knowing

- **`VoidEventConfig`** — `getEventStartSeconds()`, `getInvasionPortalSpacing()`,
  `getActiveWeatherId(stage)`, `getActiveMusicIndex(stage)`, `getActiveInvasionPortals(stage)`,
  `getMaxPortalCount(stage)`, `findLatestStage(double)`, `forEachStage(...)`; `MAX_PORTAL_COUNT`,
  `DEFAULT_PORTAL_COUNT`. Both it and `VoidEventStage` carry a `BuilderCodec`, which is why the
  numbers live in assets. [javap]
- **`VoidEventStage`** — `getSecondsInto()`, `getExtraPortalCount()`, `getForcedWeatherId()`,
  `getInvasionPortals()`, `getMusicContainerId()`, `getSoundEventId()`, `getTitle()`,
  `isAfter(stage)`. [javap]
- **Stage order is a real constraint, and Hypixel documented it in the asset:** `PortalsGoblins.json`
  carries `"$Comment": "Stages must be listed in ascending SecondsInto order: stage lookup reads the
  array as written."` So `findLatestStage(double)` does not sort. [asset]
- **Systems:** `StartVoidEventInFragmentSystem extends DelayedSystem<EntityStore>` with
  `delayedTick(float, int, Store<EntityStore>)`, plus `VoidEventStagesSystem`, `VoidEventRefSystem`,
  `VoidEventPlayerJoinSystem`, `VoidInvasionPortalsSpawnSystem`, `VoidSpawnerSystems`. [javap]
  Commands also exist (`VoidEventCommands`, `StartVoidEventCommand`), so it is both auto-started and
  manually triggerable.

### What is inferred, not established

**q3's answer — "it fires on its own" — is inference.** A `DelayedSystem` whose only method is
`delayedTick`, plus a staged config keyed on seconds-into, plus a player-join system, is a strong
read. It is not a run. Two things specifically unverified:

1. Whether a **mod** can attach a `VoidEvent` config to a world it defines, which is what §6 would
   need to have this happen anywhere other than Hypixel's own Fragments.
2. Whether the stage clock starts on instance creation or on first player join —
   `VoidEventPlayerJoinSystem` exists, so either is plausible, and it changes whether an empty
   instance advances.

**The probe that settles both:** enter a goblin-breach Fragment, record wall-clock against the
observed weather/title transitions, and compare to 120/240/390/540/690/780. One visit answers the
clock question and confirms auto-start; a second visit with a delayed join separates
creation-time from join-time.

### One lore note the assets volunteer

`portals.incomplete.description` — *"The energies within this Fragment have yet to take their final
form. All you encounter within — lore, function, and form — awaits its true shape."* [asset] That is
Hypixel telling players, in-game, that Fragments are unfinished. Useful corroboration for the
project's standing posture that a zero in this build is not a zero in the design.

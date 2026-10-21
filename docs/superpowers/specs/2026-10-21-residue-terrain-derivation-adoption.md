# Terrain derivation — adopted, and the probe that must judge it

**Status: ADOPTED 2026-10-21.** The direction in
[`2026-10-20-residue-terrain-derivation-design.md`](2026-10-20-residue-terrain-derivation-design.md)
is approved. The convergence star retires. §6 (Void) is **not** adopted here — its own questions were
answered the same day but its proposal is untouched.

**First deliverable is a probe, not an implementation.** `/residueprobe` ships with this decision and
places nothing. Why, below.

---

## What is adopted

**The model.** Placed nexuses, then lines. Candidates come from a seed-hashed jittered lattice;
terrain scores and types them; lines connect same-element nexuses; `CurrentGeometry.crossings()` is
demoted from generator to *bonus* nexuses.

**What retires.** `GrandConvergence` and the seeded arm walk. The justification is measured, not
aesthetic: nexus density falls to **0.10/km² at 2–3 km and 0.00 beyond 3 km**
(`scripts/residue-scans/out-chosen.txt` §7). Hytale is unbounded horizontally; the star is not, and
every prior fix moved supply around *inside the same disc*. Separately the designed height profile
puts 9–11% of geometry outside the 320-block world, fitted to calibration from a harness that was
deleted.

**Blast radius is `:core`-internal.** `GrandConvergence` is package-private with one caller;
`DefaultResidueNetwork`'s every construction is in a test; `NullCoreResidueNetwork` is a decorator
supplying no delegate. Nothing outside `:core` consumes residue. One existing test,
`ElementIdentityTest.allElements_shareOneGrandConvergence`, **pins the defect as a guarantee** and
goes with it.

## Two decisions added at adoption

### 1. The §7 trap is declined by construction

Derivation lives **only** in `:core`, as a pure function of `(seed, position, injected terrain
reads)`. **No residue is authored into worldgen JSON.**

§7's trap — "two implementations of one structure", which N9 §3 already rejected when it refused to
let a nexus be *"two objects that must agree"* — only arises if nexuses are placed via V2 graph nodes
in JSON *while* the field is computed in `core`. So V2's graph machinery is not used for placement.
The cost is explicit: we give up shipped machinery to keep one source of truth.

That cost turns out to be theoretical, which is worth recording because it was *verified rather than
assumed*: `HytaleWorldGenProvider` — the generator the live world actually runs — has **zero**
bytecode references to `builtin/hytalegenerator`, across all 1,106 of its classes. V2's
`DistanceToGraphEdge`, `SquareGrid2d` and `CellWallDistanceDensity` are not callable at runtime at
all, and `GraphSpace` is generation-scoped and unpersisted even where it does run. So the lattice was
always going to be ours.

### 2. Timing is a deployment choice, not an architecture choice

This is what unblocks the fork §9 q1 and q2 left open, and it follows from determinism.

Candidates are a pure function of `(worldSeed, cellX, cellZ)` through SplitMix64; terrain is itself
seed-determined. So the derivation returns the same answer whenever it runs.

- **Today:** on demand, near players. That is the only thing possible — a headless server loads no
  chunks, and a server with a player loads a region local to that player. It is also sufficient,
  because residue only matters where a player is.
- **Later:** if mod participation in worldgen proves out, or workspace deferral #18's V1→V2 handover
  makes `EnvironmentProvider.evaluate` reachable for arbitrary positions, precompute into
  `PointManager` (persisted, KD-tree indexed, radius-queryable, survives restart [measured]).

The second is a **cache of a deterministic function, not a second source of truth** — which is why it
satisfies §7's constraint instead of violating it. Note `PointManager`'s measured constraint for when
that day comes: tag-filtered radius queries match on the tag **key**, not `key:value`, so element
typing must live in the key.

## The capability question, answered by compilation

The operator's question at adoption was whether we can actually find lava, water and their meeting
points. Everything below now **compiles against the real jar** in `mod/hytale`, which upgrades it from
`[javap]` hypothesis to `[compiled]` — existence and shape proven, behaviour still owed to a run.

| Need | Mechanism |
|---|---|
| lava / water at a position | `SectionReader.getFluidId` → `Fluid.getAssetMap().getAsset(id).getId()` → `Fluid_Lava` / `Fluid_Water` |
| volcanic / glacial / swamp / shore terrain | `getEnvironment` → one of 134 shipped environments; 11 are volcanic, tiered `T1`–`T3` |
| exposure | `ChunkLightData.getSkyLight` via `BlockSection.getGlobalLight()` — sky *access*, `[measured]` |
| loaded-or-not | `hasStorageInMemory`, `canHoldY` |

The six fluid ids are the contents of `Server/BlockTypeList/Fluids.json`.

**Mixed nexuses are detectable, and more precisely than "both nearby":** actual contact is adjacent
cells with *differing* fluid ids, computed from point reads inside the neighbourhood the lattice
already bounds.

## What is not solved, stated plainly

- **River versus ocean is not expressible.** No `River`, `Ocean` or `Lake` environment ships, no
  sea-level constant exists in the jar, and there is no river or ocean worldgen node. Water is water.
  Separating them needs a heuristic we invent.
- **Lightning over-reports.** Its rule shares Wind's exposure signals, because storm frequency per
  environment — measured in session 16, Zone 1 at 1.4% against `Env_Zone4_Crucible` at 50% — is not
  available through the terrain port.
- **The probe samples a slab, not the surface.** `IHeightmap.getHeight` exists, but the accessor that
  hands a caller the *world's* heightmap is unresolved, and a downward block scan would have required
  assuming which block id means empty. Finding that accessor is the next probe's question rather than
  a silent approximation inside this one.
- **§9 q6 and q7 remain open:** the empty-candidate case, and cell size controlling both total supply
  and mixed-nexus frequency with one dial.

## Why a probe before an implementation

The capability question is answered; the question that decides whether this is *good* is not, and it
cannot be answered from the jar: **does scoring a lattice against real terrain put a Fire nexus at the
lava lake, or thirty blocks into the basalt beside it?**

That is tuning, and this project has twice had measuring-first change the rules it was meant to
validate — the docs guard shipped with the exact gap it guards against, and a proposed
"every doc must be indexed" rule died on contact with the measurement. So `/residueprobe` prints the
derivation's opinion about ground the operator can walk to, with every contributing signal and every
near-miss, and places nothing.

It is also not throwaway. The scoring is the real implementation: `:core` holds `TerrainSample`,
`ElementRule`, `SiteTyping`, `LatticeParameters`, `CandidateLattice` and `SiteScorer` as pure, tested
code; `mod/hytale` holds the adapter, the roster's rules and the command. What a bad probe run would
change is the rules and the dials, which are data.

## Restructured the same day: elements are not grounded the same way

**The uniform rule was wrong in both directions, and it shipped that way before being corrected.**
One record with fluids, environments and a sky range meant an environment match plus exposure reached
the qualifying threshold exactly — so `Env_Zone4_Volcanoes` with no lava anywhere scored as a Fire
site, while Wind and Lightning could *only* ever qualify that way, having no physical feature to
require. One shape, two opposite failures.

`ElementRule` is replaced by a sealed `ElementGrounding`, so `SiteScorer` dispatches on type and
cannot forget the distinction:

| Grounding | Elements | Qualifies on |
|---|---|---|
| `Physical` | Fire, Water | the fluid is **required**; environment only corroborates |
| `Geometric` | Earth, Wind | environment **and** exposure; either alone scores below threshold |
| `Weather` | **Lightning** | storm probability past a gate, **and** exposure |
| *(none)* | Void | nothing — a staged Fragment incursion, not a terrain feature |

### Lightning is the one Stormseeker needs, and its signal is authored data

Lightning has no physical presence: it is not a thing that *is* somewhere, it is a thing that
*happens* somewhere. So its grounding reads storm probability, and that probability is in the asset:
`Env_Zone4_Crucible.json` carries `WeatherForecasts` with `Zone4_Swamp_Storm` at weight **50** against
`Zone4_Swamp_Gloomy` at **50** — session 16's measured 50% share, authored rather than inferred.

Reachable at runtime, now `[compiled]`: `Environment.getWeatherForecasts()`,
`getWeatherForecast(int)`, `WeatherForecast.getWeatherId()`, `getWeight()`, and
`IWeightedMap.forEachEntry` — which is the only way to see the weights rather than sample from them.

**So for Lightning an environment id is not a coarse proxy, it is the carrier of the primary signal.**
The reliability concern inverts: it bites hardest for Fire and Water, where a physical check exists as
a backstop, and least for Lightning, where environment *is* the data.

### Three approximations in the Lightning signal, each deliberate

- **Storm is not lightning.** `Zone2_Sand_Storm` and `Zone3_Snow_Storm` carry no thunder, so counting
  every `Storm` would site Lightning in a desert. Weather *tags* discriminate cleanly —
  `Rain:[Storm]` and `Thunderstorm` against `Sandstorm` and `Snow:[Storm]` — but there is **no tag
  accessor on the runtime `Weather` object**, so the id is all there is. `Zone4_Storm` is genuinely
  ambiguous (tagged only `Zone4:[Storm]`, no precipitation group) and is counted.
- **The forecast key's meaning is unverified.** `getWeatherForecasts()` is an `Int2ObjectMap` keyed
  0,1,2,3… — season, day index, or something else. All slots are averaged equally, which is correct
  if it is a season and reasonable otherwise.
- **It is the environment's share, not the column's.** Weather is regional; no per-position storm
  probability exists to read.

### Environment tags: the right key, unavailable and incomplete

Measured rather than assumed, because the question was whether to switch off name matching:

- **No tag accessor on the runtime `Environment` object.** Only `getId()`. Tags are an
  authoring-time concept that does not survive to the object.
- **And they are incomplete.** 105 of 134 environments carry any sub-tag; **29 carry none**. Volcanic
  is **11 by name against 5 by tag**, and the six gaps are exactly the `_T2` and `_T3` tiers with
  `_T1` tagged in every zone.

So name substrings are both the only runtime option and the more complete one. The fragility is in
our parsing of an undocumented convention, not in the engine's data.

## Corrections made while building it

Two claims in this work were wrong and were caught by tests rather than review, which is worth
recording because both were stated confidently first.

- **The extent guarantee is `2 × cellSize`, not `cellSize`.** Because `jitterFraction` is capped at
  0.5 a candidate never leaves its own cell, so an *unaligned* window of one cell can contain none.
  Every window of two cells always fully contains one whole cell and therefore one candidate.
  Worst-case neighbour spacing is `(1 + 2 × jitterFraction) × cellSize` — about 1.7 cells at the
  default — which is the honest answer to "how far might a player walk between nexuses". The first
  version of the test asserted the one-cell form, failed, and the failure was the useful output.
- **The cell-enumeration margin is defensive, not required.** Its comment claimed the margin was
  needed for determinism. It is not: candidates cannot escape their cells. The margin is what keeps
  that true if the jitter cap is ever raised.

## Verification

```
BUILD SUCCESSFUL           (all eight guards, including :core:checkNoPlatformImports)
CENSUS_VERDICT:   GREEN | 385 tests | 0 failures          (was 356; +29 for the derivation)
COVERAGE_VERDICT: GREEN | 4/4 modules | 0 exempt          (mod/hytale compiled against the real jar)
DOCS_VERDICT:     GREEN | dirs=9 curated=12 dated=21 links=34 specs=15 stamped=6
```

`:core` stayed pure: the terrain port is an interface over `String`, `int` and `Optional`, so the
platform-import guard passes with derivation in place.

## What would change this decision

- A probe run that sites elements somewhere a player would call wrong, and that rules and dials
  cannot fix. That is a finding about the model, not the tuning.
- Deferral #18 firing: a V1→V2 handover makes arbitrary-position environment reads available and
  reopens the timing question **in the better direction**.
- Mod participation in worldgen proving out, which turns the cache from hypothetical to buildable.

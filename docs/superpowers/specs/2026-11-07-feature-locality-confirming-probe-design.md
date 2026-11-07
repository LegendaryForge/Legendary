# Feature locality — the confirming probe

**Status: EXECUTED 2026-11-07** against game build `0.7.6`. Results and the method corrections
the run forced are in § *Results* at the end; the specification above is left as written so what
was predicted and what was found can be compared.

**Status (original): PROPOSED 2026-11-07.** Supplies the probe that `2026-10-23`'s worldgen-locality
finding names but does not execute.

> **That finding is not on `main`.** It lives in
> [`integration/hytale-engine-api.md`](../../integration/hytale-engine-api.md) **on PR #149's
> branch**, which was green and unmerged for two weeks. The operator's decision on 2026-11-07 was
> to run this probe *before* merging it, so the row lands as `[measured]` rather than as a
> hypothesis. Read the section "Worldgen feature locality" on `docs/worldgen-feature-locality`, not
> on `main`, until #149 merges. That finding is `[javap]` throughout, which this project's own tier table calls **a
hypothesis**; this document specifies what turns it into `[measured]`, and scopes out one of its
three open claims as off the critical path.

Nothing here changes the residue model. It confirms or refutes a fact about the engine that the
model's *timing* rests on.

---

## What is being confirmed

The finding (PR #149, open) says: **nothing bounds a worldgen feature's footprint, because features
are re-derived per chunk from a deterministic point generator rather than written across chunk
borders.** Minecraft's 3x3-chunk feature bound has no analogue in Hytale.

It left three claims unconfirmed. They are **not equally load-bearing**:

| # | Claim | On the critical path? |
|---|---|---|
| 1 | `getLargestFeatureChunkExtents` is the *only* gate on feature reach | **Yes** — if a second, smaller gate exists, generation-time derivation is bounded after all |
| 2 | `IPointGenerator.collect` is what prefab placement actually calls per chunk | **Yes** — it is the evidence that the engine's model *is* ours |
| 3 | Whether a mod can **register into** that path, or only read it | **No — see below** |

**Claim 3 is scoped out, deliberately.** The adopted architecture
([`2026-10-21-residue-terrain-derivation-adoption.md`](2026-10-21-residue-terrain-derivation-adoption.md))
is that derivation is **pure `:core` code behind an injected terrain port, with no residue authored
into worldgen JSON** — a decision taken specifically to decline the two-implementations trap. So
whether a mod can register a prefab pattern is a question about a route we have already declined.
Confirming it would also mean authoring a `WorldGenModifier` asset, and
`Workspace_Deferrals` #18 records that route as **V1-only** — the terminal
branch the operator ruled against investing in on 2026-08-25.

It is recorded here as not-needed rather than left looking open, because an unconfirmed claim with
no owner reads as a gap. If the no-worldgen-JSON decision is ever reversed, claim 3 becomes live
again and this paragraph is where to start.

## Why this is answerable read-only, which it was not thought to be

The probe the finding names is *"declare a trivial feature with a known `maxSize` and an
`exclusionRadius`, and check that its blocks appear in chunks other than the one containing its
point."* That phrasing assumes declaring a feature, i.e. claim 3's machinery.

It is not necessary. **The base game already ships thousands of declared features**, and as of
`0.7.6` the entire walk from the live generator down to their declared sizes is **public API** —
verified by `javap`, and verified again by the probe compiling, which is the `[compiled]` tier:

```
ChunkGenerator.getZonePatternProvider()              public
  ZonePatternProvider.getZones() / getMaxExtent()    public
    Zone (a record): biomePatternGenerator(), caveGenerator(), uniquePrefabContainer()
      BiomePatternGenerator.getBiomes() / getExtents()
        Biome.getPrefabContainer()
          PrefabContainer.getEntries() / getMaxSize()
            PrefabContainerEntry.getExtents() / getPrefabPatternGenerator()
              PrefabPatternGenerator.getMaxSize() / getExclusionRadius() / getGridGenerator()
      CaveGenerator.getCaveTypes() -> CaveType.getMaximumSize()
```

`ChunkGenerator` is already reached live, today, by `/worldgenquery`
(`world.getWorldConfig().getWorldGenProvider().getGenerator()`), so there is no new access problem
to solve.

## The probe — `/featurelocality`

One command in `mod/hytale`, four parts, read-only. It places nothing, writes no blocks, and ships
no assets.

### Part 1 — Extent census, and the refutation test for claim 1

Walk the chain above and print **every** extent-like quantity the live generator exposes, per zone
and per biome, plus the maximum of each kind.

The finding's weakness is stated in its own words: *"It is the only one found; absence of a second
gate was not established."* Enumeration converts that into a test. Four quantities are candidate
second gates, and all were found while specifying this probe rather than being known before:

| Quantity | Why it is a candidate |
|---|---|
| `ZonePatternProvider.getMaxExtent()` | an extent on the provider that computes zone layout |
| `BiomePatternGenerator.getExtents()` | an extent per zone's biome layout |
| `PrefabContainer.getMaxSize()` | an aggregate over a biome's prefab entries |
| `PrefabContainerEntry.getExtents()` | per entry; backed by a mutable `extend` field |

**The test:** let `D` be the largest declared feature size in blocks
(`max(PrefabPatternGenerator.getMaxSize(), ceil(CaveType.getMaximumSize()))`).

- If any candidate gate is **smaller than `D`**, claim 1 is **refuted** — something clamps reach
  below what features declare, and generation-time derivation is bounded after all.
- If every candidate is **greater than or equal to `D`**, claim 1 **survives** at `[measured]`.

A surviving claim is not a proof of absence and the output must say so: it establishes that no
smaller gate exists *among the quantities the live generator exposes publicly*, which is a strictly
weaker statement than "no second gate exists". That is the honest ceiling of a read-only probe and
is worth more than the current ceiling, which is one person's grep.

### Part 2 — Execute `getLargestFeatureChunkExtents` instead of reading it

`minChunkCoord` and `maxChunkCoord` are private, so the computed extents cannot be read directly.
But **`isChunkOutsideGeneratableArea(int, int)` is public**, and the constructor sets

```
minChunkCoord = -67108864 + extents
maxChunkCoord =  67108863 - extents
```

So binary-search `isChunkOutsideGeneratableArea` for the boundary chunk, recover `extents`, and
compare it against the value **predicted from Part 1's public declared sizes**:

```
predicted = (ceil(D) >> 5) + 16        // ChunkUtil.chunkCoordinate, + CHUNK_BOUNDS_PADDING
```

- `recovered == predicted` → the decompiled arithmetic is confirmed **by execution**, and
  `CHUNK_BOUNDS_PADDING = 16` chunks is confirmed as a real term rather than a read constant.
- `recovered != predicted` → there is another term in the computation, and the difference names its
  size. This is informative either way, which is the property a probe should have.

Binary-search both the min and max side. They should be symmetric about the world range; asymmetry
would itself be a finding.

### Part 3 — Does `collect` name where features actually are? (claim 2)

> **⚠️ SUPERSEDED — this part no longer exists.** Replaced 2026-11-07 (session 30) by the `collect()`
> call-edge test in
> [`2026-11-07-collect-call-edge-claim2-design.md`](2026-11-07-collect-call-edge-claim2-design.md).
> The design below is retained because its *diagnosis* is what retired it: the null was structural,
> not a tuning problem, and § Results records why. The approach is not to be tuned and re-run.

For the prefab pattern with the largest `maxSize`:

1. `getGridGenerator().getInterval()` — report it against our `cellSize`.
2. `collect(seed, minX, minZ, maxX, maxZ, consumer)` over a region around the player, with the real
   world seed from `WorldConfig.getSeed()`.
3. At each returned point, compare the **actual topmost solid block** against
   `generateHeight(seed, x, z)`. A deviation means something was *placed* there. This reuses a
   measurement already validated on `0.7.4`: `generateHeight` matched the topmost solid block
   **7/7 exactly** across y=50–159.

**A matched set of control points is mandatory, not optional.** Sample the same number of positions
*not* in the collected set, from the same region and the same height band, and compute the deviation
rate for both. The reason is specific: `PrefabPatternGenerator.isFitHeightmap()` exists, so the
generator may already account for placed prefabs — in which case deviation is near zero *everywhere*
and the raw collected-point number means nothing. Only the contrast is interpretable.

- deviation at collected points **>>** control → `collect` names feature sites. Claim 2 `[measured]`.
- the two rates **indistinguishable** → the probe has not shown claim 2, and the output must say
  "not shown", never "refuted". A null here is at least as likely to be about the instrument as
  about the world, and the standing rule applies: change one variable and re-run before explaining
  it. The first variable to change is the pattern — a pattern whose prefabs sit *on* terrain rather
  than carved into it.

Report `fitHeightmap` for the chosen pattern alongside the numbers, so the reader can interpret a
null without re-deriving why it might be null.

### Part 4 — The chunk-crossing question, literally

Take a collected point whose `x mod 32` or `z mod 32` places it within `maxSize` of a chunk border.
Report whether deviating columns appear on **both** sides of that border.

This is the finding's own sentence, answered. A feature whose blocks appear in two chunks cannot
have been written by a single-chunk pass that knows nothing of its neighbours.

## What the probe prints

A `LOCALITY_VERDICT:` line, read by name — never by position or exit code. Five occasions in this
workspace have seen something appended after a verdict and the reader see the wrapper instead.

```
LOCALITY_VERDICT: <CLAIM1_SURVIVES|CLAIM1_REFUTED> | extents recovered=<n> predicted=<n> <MATCH|MISMATCH>
                  | claim2 <MEASURED|NOT_SHOWN> collected=<a>/<n> control=<b>/<n>
                  | crossing <OBSERVED|NOT_OBSERVED>
```

Each of the four parts reports independently. A part that cannot run says so rather than being
omitted — an absent line must not read as a passing one, which is the failure mode every gate in
this project is shaped around.

## Scope and placement

- **One new command**, `FeatureLocalityCommand`, in `mod/hytale` — the only module permitted to
  touch `com.hypixel.*`. `checkCommandRegistration` enforces that its name is unique and appears in
  the boot log.
- **One new driver**, `scripts/live-probes/drive-featurelocality.sh`, following the existing
  `drive-*.sh` pattern.
- **Nothing in `:core`.** This reads the engine; it does not change the model. `:core` has no
  `com.hypixel` reference by design and the build enforces it.
- **No asset shipping, no block writes, no base-asset override.**

## Known risks, stated before the run

- **The harness last ran against `0.7.4`.** `live-server-testing.md` is stamped `0.6.1` and expires
  with the build, so the first boot is itself a re-verification. Harness breakage is in scope to fix
  and must be reported **separately** from the probe result, so a harness fix is not mistaken for a
  finding.
- **Reading the live session log is ambiguous by construction.** The harness truncates
  `live-session.log` at boot while the dying server may still write at the offset it held, so stale
  content sits at high line numbers while the live server writes from line 1. `grep | tail -1` and
  `until grep -q` are both defeated by this — recorded twice on 2026-10-23. The only sound form is a
  **count baseline taken after boot**, then waiting for the count to exceed it. Server liveness is
  `kill -0 $(cat .server.pid)`, never a log line.
- **Part 3 may return null**, and that is a reported outcome with a named next variable, not a
  failure to be explained away.
- **Lightning and Fire are untouched by this.** The probe establishes an engine fact; it does not
  site anything.

## What this does not do

- Does not settle claim 3 (mod registration) — scoped out above, with the condition that would
  reopen it.
- Does not establish behaviour at identical signatures. A signature diff cannot see a behaviour
  change and neither can this; that is what the live probes exist for, one question at a time.
- Does not re-audit `hytale-capability-audit.md`. That document stays the `0.5.9` snapshot.


---

## Results — run against `0.7.6`, 2026-11-07

```
LOCALITY_VERDICT: CLAIM1_SURVIVES | extents recovered=27 predicted=27 MATCH
                  | claim2 NOT_SHOWN | crossing OBSERVED
```

### Part 2 — the result

| | |
|---|---|
| `minChunkCoord` / `maxChunkCoord` | `-67108837` / `67108836` |
| extents recovered | **27 chunks** from the min side, **27** from the max — symmetric |
| predicted from public declared sizes | `(370 >> 5) + 16` = **27 chunks** |
| | **MATCH** |

`getLargestFeatureChunkExtents` is confirmed **by execution**, `CHUNK_BOUNDS_PADDING = 16` chunks is
a real term rather than a read constant, and feature reach is derived from what features declare.
That is the claim the finding rested on, now `[measured]`.

### Part 1 — claim 1 survives

**283 aggregates** across **28 zones** and **1,429 prefab patterns**, **0 clamping**. Every aggregate
the live generator exposes is at least as large as the scope it aggregates over.

### Part 1b — and the gap the accounting found

`PrefabContainerEntry.getExtents()` threw on every one of 1,429 attempts:

```
NullPointerException: Cannot invoke "ChunkGenerator.getPrefabLoadingCache()"
                      because "this.chunkGenerator" is null
```

It resolves through the **thread-local** `ChunkGeneratorResource`, whose `chunkGenerator`
back-reference is set only on the generator's own pool threads. `ChunkGenerator.execute` is public
and submits onto that pool, so the read succeeds there: **1,429 / 1,429, 0 failures.**

**`ChunkGenerator.execute` is `@Deprecated` and marked for removal on `0.7.6`.** Recorded, not worked
around; the probe prints it.

### Part 4 — answered exactly, by a different instrument than this spec proposed

`UniquePrefabEntry` exposes `getLowBoundX/Y/Z` and `getHighBoundX/Y/Z` — a placed prefab's actual
bounding box — so crossing is arithmetic, `lowX >> 5 != highX >> 5`:

```
unique prefab entries: 2   spanning a chunk border in x: 2   in z: 2
largest bounding box: 118 blocks, 3 chunk border(s) crossed
example: Temple x[-1117..-1032] chunks[-35..-33]  z[262..380] chunks[8..11]
```

**OBSERVED.** A feature whose declared box lies in two chunks cannot have been written by a
single-chunk pass that knows nothing of its neighbours. No terrain inference, no noise floor, and
**no player required** — §"The probe" assumed this part needed one and it does not.

### Claim 2 — NOT_SHOWN, and not answerable by this method

With a player connected: **76 comparisons**, best eligible single-table p = **0.0059**,
Bonferroni-corrected **0.4517**. Not significant. `NOT_SHOWN`, not refuted.

The diagnosis matters more than the number, and it is structural rather than a tuning problem:

- **Background noise swamps the signal.** Control deviation rates run **15–31%** everywhere, so
  "topmost solid disagrees with `generateHeight` by ≥3 blocks" fires on about a fifth of *all*
  columns. A feature-presence signal has to beat that.
- **No pattern is both dense enough and large enough.** The usable `getInterval()` band for one
  player's loaded area is 16..67 blocks, and **every in-band pattern has `maxSize = 5`** — 5-block
  props that barely perturb a column. The only large features (`maxSize = 100`) have
  `getInterval() = 333`, which yields ~8 points in the region. Size and density are inversely
  related, so the band that gives statistics contains nothing detectable.

**The instrument to use instead** — found while fixing Part 4, not yet run: `UniquePrefabEntry`
carries `getPosition()`, so claim 2 can be tested by comparing `collect()` output against where the
engine says prefabs **actually are**, with no terrain inference and no noise floor. That is a direct
test of the same claim and should replace Part 3's approach rather than tune it.

> ## ⚠️ That instrument was unsound, and claim 2 is now closed by a different one
>
> **Do not build the comparison above.** It spans two different placement systems:
> `UniquePrefabEntry` comes from `UniquePrefabGenerator`, which has **no `IPointGenerator`** — not on
> the generator, not on its `UniquePrefabConfiguration`, which places by anchor + maxDistance +
> maxAttempts rejection sampling against a `Random`. The engine runs the two as separate steps of one
> pass (`generatePrefabs` from the collect-filled list, then `generateUniquePrefabs`). So the two
> sides disagree by construction and the probe would have printed `CLAIM2_REFUTED` — the **fourth**
> cross-scope comparison in this probe's history, after corrections #1, #3 and #6 below.
>
> Claim 2 was closed instead by the `collect` call site in `PrefabPopulator.collectPrefabs`, which
> states the claim directly and also gives the region. See
> [`2026-11-07-collect-call-edge-claim2-design.md`](2026-11-07-collect-call-edge-claim2-design.md)
> (session 30). Caught by reading the two configuration types before building anything.

### Seven things the run corrected, all found by executing

Listed because the shape repeats and the spec did not anticipate most of it.

1. **Cross-scope comparison, first instance.** Claim 1's test compared *local* quantities against the
   *global* largest feature: `D` is 370 from a cave in one zone while `PrefabContainer.getMaxSize` is
   a per-biome prefab aggregate. ~500 false gates, reported `CLAIM1_REFUTED`. Fixed by comparing each
   aggregate against its own scope, accumulated bottom-up.
2. **Absence reading as a pass.** "283 aggregates" against 1,429 patterns did not add up. Per-kind
   accounting — added only to check that — showed `PrefabContainerEntry.getExtents` at **0 checked /
   1,429 unreadable**: an entire aggregate kind missing, hidden inside a clean "0 clamping". The
   helper had also swallowed the throwable, so the reason was added too.
3. **Cross-scope comparison, second instance.** Part 1b then called 361 entries whose `getExtents()`
   was below their declared `maxSize` a clamp, and reported `CLAIM1_REFUTED` again — while **1,049 of
   1,429 were above**. A split in both directions is two different quantities, not a clamp.
   `getExtents()` ranges 0..131 and is never consulted by `getLargestFeatureChunkExtents`. Demoted to
   characterisation.
4. **A ratio threshold is not a test.** Claim 2 first returned `MEASURED` on **5/8** vs 10/48 — no
   sample floor, no multiple-comparison correction, and it was the best of four seed candidates.
   Single-table p ≈ 0.026, corrected ≈ 0.10. Replaced with Fisher's exact right tail, a floor of
   n ≥ 30 per set, and Bonferroni over every comparison tried.
5. **Selecting by extremes.** Largest `maxSize` → `interval` 333 → 8 points. Then densest →
   `interval` 1 → 130,812 points and **control 0/0**, because every position sits near a collected
   one. The usable interval is a *band*, derived from the region size and the sample floor.
6. **Part 4's original test was unsound.** It inferred placement from terrain at `border-2` and
   `border+1` — three blocks apart, strongly correlated, against a ~20% background rate — and
   reported `OBSERVED`. Replaced with the bounds arithmetic above.
7. **The verdict line contradicted the body.** After Part 4 was refactored it stopped setting
   `trace.crossing`, so the summary read `NOT_OBSERVED` while the section above it read `OBSERVED`.
   The repo's own gate discipline is that the verdict line is what a reader trusts; this is that
   failure inside a probe written under that discipline. `crossing()` now returns its verdict.

Six of the seven are one shape: **a measurement that does not test what it claims**, and in four
cases it produced a confident wrong verdict rather than an error. The two that were caught early were
caught by an arithmetic cross-check (1,429 vs 283) and by a negative control behaving like the real
seeds — not by reading the code.

### Harness

`server-up.sh` ran green against `0.7.6` with no changes across eight boot/stop cycles, so the
`0.7.4` → `0.7.6` move cost the harness nothing. Reported separately from the probe result, as
§"Known risks" required, so a harness fix could not be mistaken for a finding.

One harness defect was introduced and fixed here: `drive-featurelocality.sh` deployed to
`mods/hytale-all.jar` while `server-up.sh` uses `mods/hytale-0.0.0-SNAPSHOT-all.jar`, so after using
both the directory held two copies of the same plugin and the server refused to boot
(`Tried to load duplicate plugin`). The driver had reported only `SERVER DIED EARLY` — the symptom,
not the cause. It now writes the same filename and fails loudly on any stray jar.

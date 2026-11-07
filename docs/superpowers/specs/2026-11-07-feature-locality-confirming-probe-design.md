# Feature locality — the confirming probe

**Status: PROPOSED 2026-11-07.** Supplies the probe that `2026-10-23`'s worldgen-locality
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

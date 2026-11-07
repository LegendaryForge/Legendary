# Claim 2 — the `collect()` call edge

**Status: PROPOSED 2026-11-07** (session 30). Closes claim 2 of the worldgen feature-locality
finding, the last `[javap]` claim in it, and retires the instrument the previous session named for
the job.

Replaces Part 3 of [`2026-11-07-feature-locality-confirming-probe-design.md`](2026-11-07-feature-locality-confirming-probe-design.md).
Nothing here changes the residue model. It confirms a fact about the engine that the model's
*timing* rests on.

---

## The named instrument was unsound, and that is the first finding

Session 29 closed with claim 2 `NOT_SHOWN` and named its replacement: *"`UniquePrefabEntry` carries
`getPosition()`, so claim 2 can be tested by comparing `collect()` output against where the engine
says prefabs actually are."*

**That comparison spans two different placement systems**, so it cannot test claim 2 and its
disagreement is guaranteed:

| | pattern prefabs | unique prefabs |
|---|---|---|
| Config type | `PrefabPatternGenerator` | `UniquePrefabGenerator` + `UniquePrefabConfiguration` |
| Placement driven by | **`IPointGenerator gridGenerator`** | `anchor` + `maxDistance` + `maxAttempts` + `Random` rejection sampling |
| Has an `IPointGenerator`? | **yes** — the field claim 2 is about | **no** — not on the generator, not on its configuration |
| Produces | `PrefabPopulator.Candidate` (private, cleared each pass) | `UniquePrefabContainer.UniquePrefabEntry[]` |
| Positions readable? | no instance record is exposed | `getPosition()`, `getLowBound*`, `getHighBound*` |

The engine runs them as two separate steps of the same per-chunk pass — `generatePrefabs` then
`generateUniquePrefabs`, over two separate fields (`prefabs`, filled by `collect`; `uniquePrefabs`,
from `ChunkGenerator.getUniquePrefabs`). So `getPosition()` describes placements that `collect` was
never asked about, and comparing the two would have produced a confident `CLAIM2_REFUTED`.

**They are disjoint in placement mechanism but coupled by exclusion**, and the distinction matters:
`collectPrefab` calls `isWithinUniquePrefabExclusionRange(x, z, patternGenerator, uniquePrefabs)`, so
a unique prefab suppresses nearby pattern prefabs. One-way suppression, not a shared mechanism — and
it is probably why the two systems look conflatable from outside.

This would have been the **fourth** cross-scope comparison in this probe's history, after
corrections #1, #3 and #6 of the previous spec. It is listed first here because the shape is the
recurring one, not because the finding is large.

## What actually answers claim 2

Claim 2 asks whether `IPointGenerator.collect` is what prefab placement calls per chunk. There is a
direct call edge, in `PrefabPopulator`, decompiled from `0.7.6` bytecode:

```java
// ChunkGeneratorExecution (chunk generation) ->
public static void populate(int seed, ChunkGeneratorExecution execution) {
    ChunkGenerator.getResource().prefabPopulator.run(seed, execution);
}

public void run(int seed, ChunkGeneratorExecution execution) {
    this.worldSeed = seed;
    this.minPriority = Integer.MAX_VALUE;
    this.uniquePrefabs = Objects.requireNonNullElse(
            execution.getChunkGenerator().getUniquePrefabs(seed), EMPTY_UNIQUE_PREFABS);
    collectBiomes(seed, execution);
    collectPrefabs(seed, execution);     // <- the edge
    invalidatePrefabs();
    generatePrefabs(seed, execution);        // places from `prefabs`
    generateUniquePrefabs(seed, execution);  // the OTHER system
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

`ChunkUtil.minBlock(c)` is `c << 5` and `maxBlock(c)` is `(c << 5) + 31`, both read from their
bodies.

**This is a stronger statement than claim 2 asked for.** It does not merely say `collect` is called;
it gives the region, and the region is *the chunk's own bounds expanded by the pattern's own extent*
— which is the sentence the engine doc currently asserts as an inference from the shape of the
`IPointGenerator` interface. The inference was right. One term in it was wrong, below.

### Two corrections to the engine doc fall out

1. **The expansion term is `PrefabContainerEntry.getExtents()`, not `getMaxSize()`.** The doc says a
   chunk queries "its own bounds expanded by `maxSize`". `maxSize` is what feeds
   `getLargestFeatureChunkExtents` and therefore the *world bound*; `getExtents()` is what feeds the
   *per-chunk query region*. Two quantities, two distinct uses.

2. **"`getLargestFeatureChunkExtents` never consults `getExtents()`" is true but was read too
   broadly.** Session 29 measured `getExtents()` as incomparable to `getMaxSize()` (range 0..131,
   above for 1,049 of 1,429 entries, below for 361) and left it as characterisation with no known
   role. Its role is this call. That also explains session 29's own surprise about *why* it needs a
   worldgen thread: it is read inside the populator, which only ever runs on one.

### One row worth recording for a different open question

`prefabSeed = HashUtil.hash(worldSeed, biome.getId(), ordinal)`, and `collectPrefab` then reseeds
from `HashUtil.hash(prefabSeed, pointX, pointZ) * 1609272495L`. The lattice itself is queried with
the **plain world seed**; per-entry variation comes from this derived seed, not from
`PrefabPatternGenerator.seedOffset`.

That is the engine's answer to the shape of next action #4 — *add an element term to the anchor hash*
— namely `hash(seed, scopeId, ordinal)`. Recorded, not acted on: it is evidence about one option,
and that decision is not this spec's.

## The tier problem, and the proposal

The engine doc's tiers are `[measured]`, `[compiled]`, `[javap]`, `[asset]`, and `[javap]` is defined
as *"signature read, never called"* and labelled **a hypothesis**. A call edge is not that. It is not
a claim that a method *exists*; it is evidence about which code path invokes what.

The gap is already causing mislabelling in this very document's subject matter: the locality
section's decompiled `getLargestFeatureChunkExtents` **body** sits at `[javap]`, as do
`ChunkUtil.chunkCoordinate(int) = x >> 5`, `CHUNK_BOUNDS_PADDING = 16` and the world chunk range —
all read from bodies and constants, none of them signatures. For those rows the tier's own definition
is literally false.

**Proposal — add a fifth tier**, between `[compiled]` and `[javap]`:

| Tier | Means | Trust |
|---|---|---|
| **[bytecode]** | a method body, constant or call site read out of the jar's bytecode | strong for logic and control flow — proves what the code does and which path reaches it, not that it ran |

and re-tier the four rows named above from `[javap]` to `[bytecode]`. `[javap]` keeps its meaning and
stays the weakest non-asset tier. This is a change to a shared documentation convention, so it is
called out here rather than made silently.

## The probe — Part 3, replaced

Part 3 of `/featurelocality` currently measures terrain deviation at collected points against a
matched control. That approach is retired, not tuned: its null was structural (a ~20% background
deviation rate, and no pattern both dense enough for statistics and large enough to detect).

The replacement **asserts the call edge statically and measures its runtime preconditions.** It runs
inside the existing `ChunkGenerator.execute` worldgen-thread route, because `getExtents()` is only
readable there.

1. **Print the chain** `ChunkGeneratorExecution → populate → run → collectPrefabs → collect`, with
   the region arithmetic, as the recorded static evidence. The probe does not re-decompile; the
   decompilation lives in this spec and the engine doc.
2. **`ChunkGenerator.getResource()` is non-null and its `prefabPopulator` field is non-null.** Both
   are public (`getResource()` static, `prefabPopulator` `public final`), so this is a direct read of
   the populator instance the live generator actually holds — the object whose code contains the edge.
3. **Every retained `PrefabContainerEntry` has a usable grid generator.** For each:
   `getPrefabPatternGenerator()` non-null, `getGridGenerator()` non-null, `getInterval()` finite
   and `> 0`. This is the precondition the edge depends on — a null anywhere would make
   `collectPrefabs` throw for that entry, so non-nullity across all of them is measurable support for
   the model holding universally rather than for the patterns that happen to be nearby.
4. **Reproduce the engine's exact call.** For one chunk, compute
   `[minBlock(cx) - extents, maxBlock(cx) + extents]` in x and z and call `collect` with precisely
   those arguments and the real world seed, reporting the point count. This confirms the call the
   populator makes is callable with the populator's own arguments and yields a non-empty set.
5. **Report `extents` against `maxSize` per entry**, so correction 1 above is visible in the output
   rather than only asserted here.

**Every count accounts for every attempt, with reasons.** Per correction #2 of the previous spec — an
entire aggregate kind sat at `0 checked / 1,429 unreadable` inside a clean "0 clamping", and the
helper had swallowed the throwable. Each check above reports `ok / null / threw`, and a throw reports
its class and message.

### Verdict

`claim2` in the existing `LOCALITY_VERDICT` line takes one of:

| Value | Means |
|---|---|
| `CALL_EDGE` | the edge is recorded and every runtime precondition above holds |
| `PRECONDITION_FAILED` | the resource, the populator field, or some grid generator was absent |
| `UNTESTED` | the worldgen thread was not reachable |

**The verdict is returned by the part and assigned by the caller**, never read back off a field the
body is expected to have set. That is correction #7 of the previous spec, which produced a verdict
line contradicting its own body.

### Part 3 no longer needs a connected player

The retired version read the topmost solid block, so it needed loaded chunks and therefore a client.
Nothing in the replacement does. The whole probe becomes headless, and
`scripts/live-probes/drive-featurelocality.sh` can run all four parts instead of three.

## The honest ceiling

**This does not prove the engine used those points to place anything in a given chunk.** It proves
the per-chunk pass calls `collect` over a chunk-local region, that the call is well-formed against
the live world's own data, and that every pattern in the world can answer it.

The test that would close the remaining gap is a **containment** test: for one chunk, check that
every pattern prefab actually placed in it sits at one of the points `collect` yields for that
chunk's expanded region. Containment has no noise floor — one placed prefab away from every collected
point refutes it — which is what makes it the right shape where Part 3's rate contrast was the wrong
one.

**It is named and unrun because its first step has no known API, and the obvious candidate has been
checked and ruled out.** `PrefabContainerEntry` holds no instance records, and `run` clears the
`prefabs` candidate list before returning (`ObjectArrayList.clear()` on the way out), so there is
nothing to read after a pass completes. `ChunkGenerator.getPrefabLoadingCache()` is public and looked
like the route, but `PrefabLoadingCache` is a **type-keyed template cache** —
`Map<WorldGenPrefabSupplier, PrefabBuffer>` with `getPrefabAccessor(supplier)` — so it holds prefab
*content*, not placements, and cannot enumerate what was placed where. Identifying placements from
blocks instead is the terrain inference that just failed.

So the remaining gap is recorded as **blocked on an enumeration API that does not appear to exist**,
not as unrun work with a known method. Checked here so the next reader does not re-derive it; the
cache was the only candidate visible from the public surface.

## Scope

- `mod/hytale` — `FeatureLocalityCommand`, Part 3 replaced.
- `docs/integration/hytale-engine-api.md` — the `[bytecode]` tier, the `collectPrefabs` body, the two
  corrections, the disjoint-systems row, the `prefabSeed` row, and four re-tiered rows.
- This spec.

Out of scope: claim 3 (unchanged — still deliberately declined), the measurement radius, the anchor
hash decision, and anything in the residue model.

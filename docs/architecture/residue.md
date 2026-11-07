# Residue — the current design

**Verified against `main` on 2026-11-07.** This is the only document that states what the residue
design *is*. Read it first and read nothing else unless you need provenance.

> ## Why this exists
>
> There are **14** dated residue design documents. Each is a *delta*: it supersedes parts of earlier
> ones, and records that supersession in itself rather than in what it supersedes. So the current
> design was not written down anywhere — it was the result of diffing 14 files in date order, and a
> session that got the chronology slightly wrong got the design slightly wrong.
>
> That is not a memory failure. There was nothing to remember *from*. The measured consequence, on
> 2026-11-07 alone: the suppression radius was stated as `512` in two places while the code said
> `256`; `measurementRadius` was "decided" in one document and "the live follow-up" in another; and
> a session spent a research block rediscovering a fork a spec had closed the day before.
>
> ```bash
> ls docs/superpowers/specs/ | grep -iE 'residue|nexus|geode|crossing' | wc -l
> ```

## The one rule that keeps this document true

**No number is transcribed without the command that re-derives it.** A value copied into prose is a
value that will disagree with the code later — that is exactly how `512` outlived `256` in two
documents at once. Every figure below carries its own one-line check, so a reader who doubts it
spends five seconds rather than an hour.

Where code owns a value, **the code is the authority and this document is a cache of it.** If they
disagree, the code is right and this file is stale — fix it here.

---

## 1. What is built and green

The anchor pipeline, end to end: a deterministic candidate lattice, a volume strength measurement
per element, and hard-core suppression that selects anchors from candidates.

| Piece | Class | Does |
|---|---|---|
| Candidate positions | `CandidateLattice` | one seed-hashed jittered candidate per cell |
| Strength | `VolumeStrength` | counts sample points satisfying the element's predicate in a window |
| Per-element predicates | `ElementGrounding`, `HytaleDerivationRules` | what each element is grounded in, and its floor |
| Selection | `AnchorSelector` | Matérn type II: a site must beat everything within the suppression radius; ties broken by position hash |
| Engine terrain reads | `SectionReaderTerrainSample` | live, needs loaded chunks |
| Generator terrain reads | `GeneratorTerrainSample` | headless, **Earth only** — see its javadoc for why |

**Strength is a block volume, not a sample count**, and therefore invariant under `sampleStep`.
This was a real defect: a floor expressed as raw samples became 8× looser at step 4, which would
have let Fire site on an eighth of the lava its floor was measured against.

```bash
./gradlew build && python3 scripts/test-census.py | grep CENSUS_VERDICT
```

## 2. The live parameter vector

```bash
grep -n 'return new AnchorParameters' core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/AnchorParameters.java
```

As of this verification: `cellSize 128`, `jitterFraction 0.35`, `suppressionRadius 256`,
`measurementRadius 128`, `sampleStep 8`, `influenceRadius 8` (`w`), `crossingTolerance 8` (`D`).

Pinned by `AnchorParametersTest`, which also asserts `suppressionRadius / cellSize == 2` — the ratio
that keeps selection saturated, so the lattice proposes and terrain disposes rather than `cellSize`
quietly setting anchor density.

**Element floors**, stated as block volumes on each grounding:

```bash
grep -n 'VOLUME = ' mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/residue/HytaleDerivationRules.java
```

Fire's is **measured** — 15 fully-loaded candidates in a lava biome, seven holding 0 samples and the
weakest real river holding 44, so any threshold in 1..44 separates absence from presence. The
abundant elements' floor is **a frozen window fraction with no independent basis** (`ceil(0.01 ×
43,560)` samples at the default window), and `HytaleDerivationRules` says so at the constant. It is
the one number here that is arbitrary, and it has never been the binding constraint.

## 3. Which code is live, and which is retired

The repository contains **two parallel parameter records**, and this is the single most confusing
thing about reading the residue code:

| Record | Model | Status |
|---|---|---|
| `AnchorParameters` | volume / anchor-density | **live** |
| `LatticeParameters` + `CurrentParameters` | convergence-star, weighted-sum scoring | **retired** |

`AnchorParameters`' own javadoc states the split: the volume model does not use the other record's
scoring weights at all. Likewise:

| Class | Status |
|---|---|
| `VolumeStrength` | **live** — counting |
| `SiteScorer` | retired weighted-sum scoring; still driven by `/residueprobe` |
| `CurrentGeometry`, `GrandConvergence` | retired convergence star |
| `DefaultResidueNetwork` | built on the retired star; **zero production consumers** |

```bash
grep -rn 'new DefaultResidueNetwork' core/src quests harness mod | grep -v /test/
```

That returning nothing is the point: the retired star is reachable only from its own tests, so
building the real current model is **additive** and retiring the star is a separate, safe deletion.

**`verticalSpan` is a dial on the retired record, not an open item on the live one.** It appears in
`LatticeParameters` and is read only by `SiteScorer`. Its job — how far above and below a surface to
sample — was replaced by per-anchor y (§4 below). Reading it as an open parameter of the current
model is a mistake this document exists to prevent.

## 4. Decided, not built

Everything above anchors. Nothing here exists in code.

- **Currents** — per element, a **minimum spanning tree** over that element's anchors: `N−1` edges,
  no cycles, minimal total length. Each edge is a **least-cost path weighted by element affinity**,
  not a straight line, because crystal density makes the line's path observable and a straight line
  over a ridge reads as geometry rather than place. Rivers then emerge as water currents with no
  special case.
- **Anchors are 3D**, each carrying its own y from its element's rule — fluid surface for Fire and
  Water, the cave volume's level for Earth, above terrain for Wind, exposed surface for Lightning.
  This dissolves a measured defect rather than patching it: with one shared reference plane, the
  same candidate at the same seed scored `earth 2.0` in one run and nothing in the next, and one
  flipped on a **2-block** elevation change.
- **Crystals** mark currents, gated on earth proximity: a crystal may spawn where a current passes
  within `w` **of earth**. Current-first is load-bearing — phrased as "where rock meets void" it
  becomes a property of places and every rocky surface qualifies.
- **Geodes** form where two *different* elements' currents pass within `D` of each other, inside
  earth. Same-element lines never cross, because a tree has no cycles — so cross-element crossings
  are the only crossings there are.

## 5. Genuinely open

| Open | State |
|---|---|
| **Is an MST computable in an unbounded world?** | Found 2026-11-07 and **unaddressed by any spec**. An MST is a global structure; computing it per region makes edges near a boundary depend on where the boundary was drawn. This is the same shape as the failure that retired the convergence star. Blocks currents |
| `measurementRadius` | Earth **measured** 2026-11-07: at the current window 32% of contests are decided by gaps under 1%, which halves at radius 64. But the original argument for 128 was about *water saturation*, which runs the opposite way and is unmeasured |
| Element term in the anchor hash | `AnchorSelector.positionHash` is `mix(worldSeed, x, z)` with no element term, so two elements tying across the same candidates select identical anchors. The engine's own answer, found 2026-11-07, is `hash(seed, scopeId, ordinal)` |
| Earth's y source | Its modal level is `y=0` in every column measured, so §4's rule is degenerate for Earth |
| Lightning's measure | Has none, by any route. `VolumeStrength` throws for `Weather` groundings |
| Writing blocks through the chunk holder | Unproven |
| Crossing frequency | Unmeasured, and deliberately not gating — until something *required* is placed in a geode |

## 6. Settled — do not re-propose

Each of these was decided and at least one was re-proposed after being decided.

- **Residue is invisible.** No rendering, no overlay, no sight mode. Players read crystal density.
  This dissolved an operator question rather than answering it: "should residue acknowledge a
  striking formation" presupposed the player perceives residue.
- **The roster is the cosmological five** — Fire, Water, Lightning, Wind, Earth. `grep -oE '"(fire|water|lightning|wind|earth)"'` over `HytaleDerivationRules` confirms what is coded.
- **`elementId` stays an opaque `ResourceId`.** No enum, no list, no default, no validation. That
  openness is what makes a roster change a documentation edit instead of a breaking API change plus
  a migration, and the game ships every 2–6 weeks while still adding elements.
- **Void is an incursion, not a darkness.** Not native to Orbis, no reachable destination. "Void =
  night and underground dark" was considered and dropped.
- **A content count is evidence about what has been built, never about what the world is.** Fire has
  twenty droppers because Zone 4 got built; weighing that measures Hypixel's schedule.
- **Elemental Circles are out of scope**, in both roles.
- **The convergence star is retired** — measured 0.00 nexuses/km² beyond 3 km, and tuning is
  exhausted.

## 7. Provenance — one hop, not a diff

The 14 dated specs remain the record of what was believed and when. This table is how to reach the
right one without reading them all. **They are history; this document is the state.**

| For | Read |
|---|---|
| The current model, and why counting replaced weighted scoring | `../superpowers/specs/2026-10-21-residue-anchor-density-design.md` — but its §9 banner states a superseded suppression radius |
| Why derivation is pure `:core` behind a terrain port | `../superpowers/specs/2026-10-21-residue-terrain-derivation-adoption.md` — placement half superseded by the above |
| The parameter vector's derivation, and the π error that halved it | `../superpowers/specs/2026-10-23-residue-sizing-re-derivation.md` |
| The roster, and why a content count is not evidence | `../superpowers/specs/2026-10-21-residue-roster-decision.md` |
| The window-arbitrariness measurement | `../superpowers/specs/2026-11-07-residue-window-separation-measurement.md` |
| Engine facts — what worldgen actually does | `../integration/hytale-engine-api.md` |
| Lore — what things *are* | `../setting/hytale-orbis-setting-brief.md` |

The four `2026-08-*` specs describe a single-convergence seeded-walk model keyed to six essence
items. That direction is superseded; they carry banners saying so. What survives is their
measurements and their reasoning about why each choice was made.

---

## Keeping this document honest

Two failure modes, and the first is the likely one:

1. **This file goes stale and still reads as current.** Mitigated by §"The one rule": every number
   has its re-derivation command, so staleness is cheap to detect. It is not mitigated by hoping.
   **When a decision lands, updating this file is part of landing it** — not a follow-up.
2. **A 15th delta document gets written instead of editing this one.** The routing table in
   `../README.md` sends designs to a dated spec, which is right for a *decision*. The state of
   residue is not a decision, and it belongs here. A new spec that decides something should edit
   this file in the same change.

`../../CLAUDE.md` § "Residue network" points here rather than summarising, because summarising it is
what produced the fifteenth stale surface last time.

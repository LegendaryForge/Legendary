# Per-element current topology — spanning retired, and wind grounded in constriction

**Status: DECIDED 2026-11-07** (session 31) for the topology split; **MEASURED** for the wind-channel
figures, against game build `0.7.6`, headless, no client. Nothing here is built.

Supersedes the topology half of
[`2026-10-21-residue-anchor-density-design.md`](2026-10-21-residue-anchor-density-design.md) §
*"Topology: a minimum spanning tree per element"*. That section stands as the record of why a tree
beat a nearest-`k` mesh, which is still right. What it decided — one global MST per element — does
not survive the move to affinity-derived anchors.

---

## 1. What this answers

Two questions, and the second one arrived during the first.

**The blocking one.** [`architecture/residue.md`](../../architecture/residue.md) §5 carried
*"Is an MST computable in an unbounded world?"* as open and **unaddressed by any spec**, blocking
currents. An MST is a global structure: computing it per region makes edges near a boundary depend on
where the boundary was drawn, which is the shape of the failure that retired the convergence star.

**The one that reframed it.** The operator's reading of why: the network layer was designed when
nodes were *placed to make geometry work* — a tiled convergence star, a hex-ish grid — and nodes are
now *derived from an area's elemental affinity*. So the question is not how to window an MST. It is
what topology affinity-derived anchors imply.

## 2. Why spanning is a grid-era decision

Under a lattice of nodes, "connect them all minimally" is a sensible instruction: nodes are uniform
by construction, so every edge is short and local.

Affinity-derived anchors are not uniform. `AnchorSelector`'s own javadoc states it — *"a region with
no lava yields no Fire anchor, and nothing is tuned to say so"* — so each element's anchor set is
**patchy**, and for a `Physical` element the absence is *guaranteed* rather than likely: no lava
within the measurement window means strength below the floor means no anchor, by arithmetic.

**How large the gaps are is unmeasured**, and cannot be measured by the headless route —
`GeneratorTerrainSample` has no fluid data, so Fire's strength there is identically zero. What the
argument below needs is only that gaps exist and are unbounded in principle, which the floor
guarantees; it does not need a figure, and none is asserted here.

A spanning tree must connect every anchor, so it **must** emit an edge bridging two volcanic regions
across a snowfield. Least-cost pathing makes that edge look cheaper; it cannot decline to exist. That
is the exact defect terrain derivation was adopted to kill —
[`2026-10-20-residue-terrain-derivation-design.md`](2026-10-20-residue-terrain-derivation-design.md)
rejected the convergence lattice because *"it fixed the mechanism's reach while leaving residue an
invisible mathematical field that would happily site a Fire nexus in a snowfield."* Spanning
reimports it one layer up, at the edges, after the node layer was fixed.

The unbounded-world problem is the same fact seen from the other side: spanning is a global
instruction, so it admits no local certificate, so it has no halo. **The decision is to stop
spanning**, not to window it.

## 3. What the groundings already decide

`ElementGrounding` is a sealed hierarchy for a reason its javadoc records: *"the elements are not
grounded the same way, and a single rule shape is wrong for most of them."* Read one layer up, it
decides the topology too — via a property the type already carries, **whether the element can be
absent**.

```bash
grep -n 'record Physical\|record Geometric\|record Weather' \
  core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/ElementGrounding.java
```

| Element | Grounding | Can be absent? | Strength field | Topology |
|---|---|---|---|---|
| **Fire** | `Physical` — lava required | **yes**, no lava → no anchor | riverine lava volume | **contained** |
| **Lightning** | `Weather` — storm gate | **yes**, `minStormProbability` | **none — constant per environment** | **contained** |
| **Water** | `Physical` — water required | yes, but oceans connect globally | water volume | sprawling |
| **Earth** | `Geometric` SOLID | **no** — rock is everywhere | monotone ↑ with height | sprawling |
| **Wind** | `Geometric` CONSTRICTION (§5) | **no** — air is everywhere | constriction | sprawling |

**A contained network requires guaranteed absence around it**, and only `Physical` and `Weather`
provide it. `Geometric` cannot be absent, so Earth and Wind have no choice but to sprawl: cluster
them and you get one unbounded cluster, which is the problem this spec opened with.

**Lightning is contained for a reason, not by analogy.** Its signal is storm probability drawn from
the environment's authored `WeatherForecasts`, so it is **piecewise constant per environment** rather
than a per-coordinate field. Every candidate inside `Env_Zone4_Crucible` ties, the hash decides, and
there is no gradient to flow along even in principle. Containment needs only positions; drainage
needs a gradient. So lightning can have the one and not the other.

**Water is the one judgement call.** It is `Physical`, so containment is *available* to it — but its
qualifying regions connect across the world, so clustering yields one global cluster. Chosen
sprawling, not forced.

## 4. Two topology kinds, and a checkable invariant

Topology becomes a third thing an element states, beside its grounding and its floor — mirroring the
seam that already exists rather than adding a new one.

- **`Sprawling(reach)`** — every anchor links to the nearest **stronger** anchor within `reach`; an
  anchor with nothing stronger in reach is a source. Extends in many directions to practical
  infinity, never spans a gap, and is **acyclic by construction** because strength strictly decreases
  along every edge. Reuses the total order already shipped and tested, `AnchorSelector.beats` on
  `(strength, hash, coords)`. Its locality halo is exactly `reach` — structurally the margin
  `AnchorSelector` already documents for `suppressionRadius`, so the unbounded-world question stops
  existing rather than acquiring a bound.
- **`Contained(reach)`** — anchors grouped by single-linkage at `reach`, then a minimum spanning tree
  *within* each group. The MST was not wrong; it was wrongly universalised. It is right exactly where
  the element is bounded, and there the unbounded-world question never arises because the cluster is
  surrounded by absence the grounding **guarantees**.

> **`Contained` on a `Geometric` grounding is a construction error.** It cannot be satisfied, and
> saying so in the type system means nobody has to remember it — the same way `Physical` already
> rejects a grounding with no required fluid.

**What this gives up, and why both are cheap.** *Minimal total length* goes, so the `0.48 A/R`
constant needs re-measuring — already a named trigger in
[`2026-10-23-residue-sizing-re-derivation.md`](2026-10-23-residue-sizing-re-derivation.md). And
*non-crossing* goes — but it was already gone, and §5 of the anchor-density design was wrong about
why it held. A Euclidean MST is non-crossing by a geometric exchange argument on **straight
segments**, not because *"a tree has no cycles"*; §4 makes every edge a curved least-cost path, and
two curved edges sharing no endpoint can cross whatever the graph is. The geode rule is conditioned
on *different* elements, so a same-element braid produces nothing either way.

**What it gains, beyond locality.** Direction, which the MST never supplied: an *unrooted* tree has
no upstream, and the root the semantics were built on — *"The Grand Convergence is upstream"*,
[`2026-08-27-residue-network-design.md`](2026-08-27-residue-network-design.md) — is retired. Under a
strength hierarchy upstream is *toward stronger*, terminating at the most intense expression of that
element in that region. One unreachable global landmark becomes many local ones. It also restores the
grading that killed the star: the star died because `densityAt` measured a flat 1.0 everywhere, and a
forest grades intrinsically by subtree size or Strahler order — which is what makes rivers come out
as rivers rather than as uniform threads.

## 5. Wind: constriction is the grounding, not the topology

### What was measured

`/windchannels`, headless, five 1 km² regions tens of kilometres apart. Re-derive:

```bash
./gradlew :mod:hytale:shadowJar && bash scripts/live-probes/drive-windchannels.sh
```

For a column at height `h`, probe the eight neighbours at ring distance `d` as four opposite-pair
axes; an axis's wall is `min(h(a), h(b)) - h`. Every column is then exactly one of OPEN (no axis
walled), BOWL (all walled) or CHANNEL (some walled, some not) — exhaustive, because a count that does
not account for every column is not a count.

| scale | channel% | longest run | axis agreement |
|---|---|---|---|
| d=4 / rise=8 — narrow gorge | 0.16–1.18% | 28–54 b | 36–69% |
| d=8 / rise=16 — gorge | 0.19–1.15% | 22–64 b | 47–70% |
| d=32 / rise=8 — broad valley | 6.35–9.18% | 168–344 b | 36–42% |
| **d=64 / rise=16 — valley floor** | 3.43–5.69% | **142–460 b** | **62–74%** |

Ranges are across the five regions, every one of which had real relief (138–222 blocks), so none was
a degenerate flat patch. Chance is 25% with four axes. The trench control scored 1024 b at 100%.

**Three controls, and the positive one is load-bearing.** flat → 100% OPEN; a monotone slope → also
100% OPEN, which is what establishes the detector needs *constriction* and not merely relief; and a
synthetic 7-block gorge → **1539 channel columns against 1539 predicted**, one component, 1024 blocks
long, 100% axis agreement. A near-zero reading on real terrain is otherwise ambiguous between "the
terrain is smooth" and "the detector does not work".

### The cross-check that decides how to read it

Mean anchor spacing is **447 blocks**, per element:

```bash
grep -n -A6 'Quantity, per element' 2026-10-23-residue-sizing-re-derivation.md
grep -n 'suppressionRadius' ../../../core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/AnchorParameters.java
```

The sizing table gives **5.00 anchors/km²** at `suppressionRadius = 256`, which is the committed
value, so spacing is `1/sqrt(5.00)` km = 447 b. Cross-checks against Matérn hard-core packing:
`1/(pi * 256^2)` = 4.86/km².

> **Read the column, not the number.** That table's other column, `1.23` anchors/km², is the
> **R=512** figure — the superseded radius. Taking it yields a 900 b spacing, which this spec
> asserted in its first draft and which made the argument below twice as strong as it is. That is the
> `512`-outliving-`256` confusion [`architecture/residue.md`](../../architecture/residue.md) exists to
> prevent, reproduced inside a spec citing its rule, because this figure was the one transcribed
> without its re-derivation command attached.

Against 447 b, the coherent valley runs of **142–460 blocks** are **0.32x to 1.03x** one anchor
spacing. So in the best region of five, one valley is about one anchor gap; typically it is a third to
three quarters of one.

**That is still not a topology, and the reason is sharper than "too short".** A topology needs
*every* edge to be realisable. A feature that reaches the next anchor only in the most favourable
region cannot carry a connected network — the network would be defined where terrain happened to
cooperate and undefined elsewhere, which is the extent problem the lattice was introduced to solve. A
**path cost** has the opposite property: it uses whatever constriction is present over whatever
distance it runs, and needs no guarantee at all. Features at 0.3–1.0 anchor spacings are well suited
to the second role and unusable in the first.

### The decision

1. **Wind's grounding becomes constriction.** This fixes a defect that is in the code today:
   `Occupancy.OPEN` counts open space over the whole world column, which its own javadoc records as
   reading *"strongest over deep water"* — so wind anchors currently favour ocean floors and
   lowlands, the exact inverse of where wind reads. Constriction puts them at valley necks, saddles
   and passes. 6–9% of columns qualify at the broad scale, far more candidates than needed, because
   the anchor layer wants **one winner per suppression radius** and never needed a connected network.
   It also makes Wind headlessly measurable for the first time — `GeneratorTerrainSample` currently
   documents Wind as not measurable by this route, because `OPEN` needs fluid data it does not have,
   and a heightmap-only grounding removes that dependency.
2. **Wind's topology is `Sprawling`**, like Earth and Water. No third kind — the channels reach
   0.32x to 1.03x of an anchor spacing, so they are sometimes long enough and usually not, which is
   the one thing a topology cannot be.
3. **Constriction enters at the path layer**, which §4 already decided: a current is *"a least-cost
   path weighted by element affinity"*. Wind's affinity is constriction, so a current threads through
   the gaps because the gaps are cheap — not because the topology is built from them. A 142–460-block
   coherent feature is a good size for a path segment, over an edge averaging 447 b, and unusable as
   a topology for the reason above.

Note which figure matters under this reading. The weakest number above is axis agreement (62–74%),
and a **cost field does not need a consistent axis** — it only needs some columns to be cheap. The
number that would have had to be strong for a channel *topology* is the one that is not, and it stops
mattering once channels move to path cost.

### What a constriction grounding has to look like, and the one risk in it

Constriction is a property of a **column's neighbourhood**, and `VolumeStrength` counts **points**
satisfying a per-point predicate. Those fit, but only in one specific form:

> A sample point counts when it is **open air** *and* its column is **constricted** *and* it lies
> **below the wall height** of that constriction.

Each clause is load-bearing. Open air and a per-point count keep the result a block volume, so it
stays invariant under `sampleStep` and `measurementRadius` — the property
[`2026-10-23-residue-sizing-re-derivation.md`](2026-10-23-residue-sizing-re-derivation.md) exists to
protect. The column test is computed once per column and reused across that column's sampled levels,
so the ring probes cost ~8 extra reads **per column**, not per sample.

**The third clause is the risk, and without it this reproduces the defect it replaces.** A deep
valley has a tall open column above it, so "open air in a constricted column" would score a valley by
how much *sky* sits over it — which is `Occupancy.OPEN`'s ocean-floor problem wearing a different
hat. Counting only air below the wall height measures the channel rather than the space above it.
Unverified: whether that leaves enough volume to clear a floor at all, which is measurable headlessly
once written.

### What the measurement cannot say

- **Caves and tunnels are invisible, not absent.** `GeneratorTerrainSample` answers solidity as
  `y <= height`, so a hole under the surface is rock by construction. Wind through a tunnel is
  unreachable by generation at any effort, and a live voxel probe could only ever sample a few
  hand-picked places.
- **Five regions on one seed**, against the V1 generator (`HytaleWorldGenProvider`). V2 could differ;
  that is deferral #18's trigger.
- **The instrument was wrong once in a recorded way.** Its first version analysed only narrow scales,
  reported "no channels", and would have been believed. The valley result is invisible to a
  narrow-only probe, and the fix was adding `(32, 8)` and `(64, 16)` to the spatial analysis.

## 6. Open after this

| Open | State |
|---|---|
| `reach`, per element | Derived, not chosen: from where connectivity structure stops changing as reach grows, expressed as a ratio to an existing parameter and pinned the way `suppressionRadius / cellSize == 2` is. Unmeasured, but **bounded below by 256 blocks** — see below |
| Current length per km² | Replaces `0.48 A/R`, which was measured against the MST. With it, crystal coverage `2w/R` |
| Does the sprawling forest degenerate in a uniform field? | The ocean case. A near-flat strength field could yield one long arbitrary chain. Measurable headlessly for Earth; the risk to find before building |
| Whether constriction places Wind anchors sensibly | Now testable headlessly, which it was not before |
| Does any of it feel like wind | Needs a person in a valley |

**`reach` has a hard lower bound, and it is derivable rather than measurable.** No two anchors are
ever closer than `suppressionRadius` = **256 blocks**: `AnchorSelector.beats` is a *total* order, so
of any two candidates within the radius one strictly beats the other and the loser is not an anchor.
A `reach` at or below 256 therefore produces **no edge at all** — every anchor is an isolated source.
And because the nearest *stronger* anchor is farther than the nearest anchor, `reach` has to clear
the nearest-stronger distribution rather than the minimum separation, which at a mean spacing of 447
b puts the useful range comfortably above `suppressionRadius` and makes a ratio to it the natural
form. The measurement is of that distribution; the floor is arithmetic.
| Earth's y source | Unchanged by this spec; still degenerate at `y=0` |
| Lightning's measure | Unchanged; `VolumeStrength` still throws for `Weather`. Its *topology* is decided here, which is progress from nothing |

## 7. Shape of the work, if it is built

Two independent pieces, and the first does not depend on the second:

1. **The topology kinds in `:core`** — `Sprawling` and `Contained` behind a per-element declaration,
   pure arithmetic over anchors, unit-testable with synthetic anchor fields, plus the headless
   measurement of `reach` and the degeneracy check on a uniform field. No engine dependency.
2. **Wind's grounding** — a new occupancy shape per §5, its headless measurement, and the anchor
   placement it produces. Touches `ElementGrounding`, `HytaleDerivationRules` and
   `GeneratorTerrainSample`'s Wind restriction.

They share only the measurement that motivated both, so they should be planned and landed separately.

## 8. Corrections this spec makes to earlier documents

- **`2026-10-21-residue-anchor-density-design.md` §5** credits the MST with two properties it does
  not deliver here: direction (an unrooted tree has none, and its root is retired) and non-crossing
  (true of straight segments, not of curved least-cost paths). Both are reasons it was chosen.
- **`residue.md` §3's retired table omits `NullCoreResidueNetwork`** (`quests/stormseeker`), which is
  part of the retired star and is referenced by nothing but its own test — so the whole
  `ResidueNetwork` surface, `grandConvergence()` included, is test-only and a clean deletion.
  ```bash
  grep -rn "NullCoreResidueNetwork\|ResidueNetwork" core/src quests mod harness | grep -v /test/
  ```
- **`Hytale_Session_Status.md`** recorded *"Three options are written up with a recommendation"* for
  the MST question. They were not written anywhere — `git grep -i windowed` over tracked docs finds
  no write-up. They existed in session 30's chat only, and this spec derives the options fresh.

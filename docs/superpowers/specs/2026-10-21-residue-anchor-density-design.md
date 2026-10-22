# Residue anchors by volume — currents without nexuses

**Status: PROPOSED 2026-10-21.** Supersedes the placement half of
[`2026-10-21-residue-terrain-derivation-adoption.md`](2026-10-21-residue-terrain-derivation-adoption.md)
— that document's *"candidates come from a seed-hashed jittered lattice"* is replaced here. Everything
else it adopted stands: derivation is pure `:core` behind an injected terrain port, no residue is
authored into worldgen JSON, timing is a deployment choice, and the convergence star stays retired.

**The headline change: "nexus" stops being a placed entity.** Currents become the only generated
geometry. What were going to be nexuses split into two unrelated things that had been sharing a word —
invisible **anchors** that give a current its route, and **crossings**, the only places that mean
anything to a player.

---

## 1. Why this exists — a probe run that found nothing, correctly

`/residueprobe` was run on 2026-10-21 with a client attached, standing on a **lavafall pouring into
water**: lava and water meeting in one place, which any player would read instantly as a Fire-and-Water
site. The probe reported **no Fire anywhere**, and it was right to.

The nearest candidate to that position was `-712,465` at **`d=130b`** — read off the probe's own
distance column, not computed — and it scored `water 3.0` by finding `Water_Source` within 24 blocks
of *itself*. The lavafall sat **more than five times** the neighbourhood radius away from the only
candidate in range. Invisible by construction.

> Two figures were wrong in the discussion that produced this spec and are corrected here: the
> lavafall's own coordinates were never captured (the probe's `here:` line prints environment and
> fluid but **no position**), so any cell-bounds arithmetic from the player's *join* coordinate was
> unsupported; and 130 against a 24-block radius is 5.4×, not the 9× claimed.

Two diagnoses were wrong before this one, and both are recorded because each cost real work:

- **"Fire needs a finer `sampleStep`."** Measured false as the *explanation*: a finer step is
  affordable (§6) but changes nothing here. Lava was never *near* the candidate; it did not slip
  between sample points.
- **"Ask the engine where lava is."** Verified absent. `PointManager` is a store for points *we*
  register, not a feature oracle, and worldgen's own feature knowledge lives in
  `builtin/hytalegenerator`, which `HytaleWorldGenProvider` — the live generator — references **zero**
  times (confirmed by disassembly of both its classes). `SectionReader` is the only path there is.

The real defect is that the model derives a *score* from terrain while taking its *position* from a
lattice. A lattice point cannot find a feature it does not happen to land on.

---

## 2. Strength is volume

**A site's strength for an element is how much of that element is in its measurement window** — a
count of sample points satisfying the element's own predicate. Quantity, not intensity and not
contrast.

| Grounding | Predicate counted | Strong means |
|---|---|---|
| `Physical` — Fire, Water | the point holds a required fluid | a lake, an ocean, a caldera — not a 2×2 puddle |
| `Geometric` — Earth | the point is rock | a massif, a mountain's bulk — not a thin crust |
| `Geometric` — Wind | the point is open air in a matching environment, sky light in band | a high plateau or ridge system — not a lone crag |
| `Weather` — Lightning | storm probability over the gate, and exposed | a broad exposed storm belt — not one open ledge |

One rule, four predicates, no per-element special cases; the sealed `ElementGrounding` hierarchy
already carries the distinction, so this is counting per variant rather than inventing per element.

**Today's scoring cannot express it.** `scorePhysical` returns on the first matching fluid, so a
trickle and a caldera are indistinguishable — which is exactly why "strong" had nothing to grip. The
scan already walks the whole window; count hits instead of returning early.

### Two measures were proposed and rejected

Both were mine, and both were rejected by the operator for the same reason — they confuse *quantity*
with *interest*, and quantity is what strength means:

- **Water as shoreline** (fluid↔air interface rather than volume). Rejected: *"Why would a shore be
  stronger than the ocean, doesn't make sense."* It doesn't. The open ocean is the strongest water
  place there is, and a measure that ranks a beach above it is wrong about the fiction before it is
  wrong about anything else. The lore agrees — essence accumulates *in living things*, and
  `Ingredient_Water_Essence` reads *"Even fish have a natural affinity for magic. As water filters
  through their gills, the concentration grows."* Open water is charged, not inert.
- **Earth as cave chamber** (void enclosed by stone). Rejected as **inverted**: a cave is the
  *absence* of earth. A concentration of earth is a lot of rock.

Recorded because the reasoning that produced them was plausible and wrong: both were attempts to fix
§3's saturation problem by changing the *measure*, when the fix belongs in the *spacing*.

---

## 3. Saturation, and why it does not need a cleverer measure

The obvious objection to volume: **there are chunks upon chunks of 100% water, 100% rock and 100%
air.** Across an ocean every sample point is water, so the count sits at its ceiling over enormous
regions. That is a plateau, and it is the same defect as N6 one level up — N6 measured `densityAt` at
exactly 1.0 everywhere on the network and concluded nothing distinguished a nexus.

It is resolved by how anchors are *selected*, not by what strength measures.

**A site is an anchor when nothing within its suppression radius beats it on `(strength, hash)`** —
strength first, then a seed-hash of the position as a total tie-break. Three regimes fall out:

| Region | Behaviour |
|---|---|
| Uniform ocean, deep rock, open sky | strengths tie, so the hash decides: roughly **one anchor per suppression radius**, evenly spread, deterministic and identical for every player |
| Varied terrain | real strength differences dominate the hash — the lake beats the puddle, the caldera beats the trickle |
| No qualifying terrain | **no anchor at all**, which is the honest answer and is §9 item 1's empty case |

So abundance decides *where* an anchor lands inside each radius; it has **no say in how many exist**.
An ocean the size of a continent yields the same anchor count as varied coastline of equal area. The
tie-break is not a detail — without it, a tie means nothing is *strictly* stronger and every point in
the ocean qualifies.

### Two radii, because one was hiding a bug

The operator's framing — *"why an anchor on a 2×2×1 puddle of water when there's a lake 3 chunks
away"* — separates two parameters `neighbourhoodRadius` had been conflating:

- **Measurement radius.** The window the count is taken over. A lake and a puddle only differ if it
  is wide enough to contain the lake.
- **Suppression radius.** The window a site must win to become an anchor. **This is the one the
  puddle-versus-lake case is about** — at today's 24 blocks, a puddle and a lake 48 blocks apart never
  compete, so both qualify.

### Sizing: one ratio sets how much of the world is currented

Anchors pack at about one per `R²` for suppression radius `R`, so over area `A` there are `A/R²` of
them. A minimum spanning tree (§5) gives one edge each, each roughly `R` long, so total current length
is about `A/R`. Crystals grow within influence radius `w`, making the crystal-bearing band `2wA/R`.
The covered fraction is therefore:

> **`2w / R`** — independent of world size, of anchor count, and of how abundant the element is.

At `w = 16` blocks:

| suppression radius `R` | per element | all five, worst case |
|---|---|---|
| 256 b | 12.5% | 62% |
| 512 b | 6.3% | 31% |
| 1,024 b | 3.1% | 16% |
| 2,048 b | 1.6% | 8% |

**So `R` is kilometre-scale, not tens of blocks** — at 256 blocks most of the world is near a current.
This is the single number that answers *"won't there be currents zigzagging everywhere in the ocean"*:
no, and the reason is geometric rather than elemental.

### Suppression radius is per-element, and Earth and Wind are why

Earth and Wind have qualifying terrain nearly everywhere — rock below, open air above — while Fire is
confined to volcanic regions. A single global radius would **saturate Earth and Wind while starving
Fire**. So the radius is per-element, and the five values are expected to need iteration against real
terrain rather than derivation. It is the parameter most likely to be wrong first try, which is what
the probe is for.

---

## 4. Anchors are 3D, and that retires a known defect

Candidates are `WorldPoint2d` today — x and z only — and every candidate in a run is scored against
one shared `referencePlaneY`: the player's. The consequence was measured twice on 2026-10-21.
Candidate `-382,410` scored `earth 2.0` in one run and **nothing** in the next — same seed, same
candidate, the player's elevation the only difference (the second run is recorded at `y=111`; the
first run's y was not printed). Session 22 measured the same mechanism with both elevations captured:
`-583,89` scored `water 3.0` at `y=117` and `no element qualified` at `y=165`, and `-907,420` flipped
on a **2-block** change because `verticalSpan=16` is ±8 around y.

Anchors therefore carry their own y, chosen by the element's rule:

| Element | y comes from |
|---|---|
| Fire, Water | the fluid surface in the window |
| Earth | below ground — the cave volume's own level |
| Wind | above terrain — the exposed ridge or plateau surface |
| Lightning | the exposed surface |

The shared-reference-plane defect then cannot occur, because no anchor is scored at another anchor's
elevation. It is dissolved by construction rather than patched.

---

## 5. Currents follow terrain, and nothing else would be legible

Currents are **invisible** — this is settled, not reopened:
[`2026-08-27-residue-network-design.md`](2026-08-27-residue-network-design.md) §4, *"The player
perceives the network only through what it produces. There is no residue rendering, no overlay, no
sight mode."* Players read **crystal density**, which grows near currents.

Because density is `distance-to-line`, **the line's path is observable**. A straight line between two
water anchors crossing a mountain ridge puts a band of crystals over the ridge, and a player reading
that is reading geometry rather than place — which contradicts D3 of the literacy design, *"residue
literacy is a reading of place."*

So a current is a **least-cost path weighted by element affinity**: water prefers water and low
ground, fire prefers lava and volcanic terrain, and so on. Rivers then emerge as water currents with
no special case — which is what a river *is*, and what made the operator ask for linking "like in
rivers" in the first place.

### Topology: a minimum spanning tree per element

Which anchors a current threads was open; this decides it. Per element, a **minimum spanning tree**
over that element's anchors — `N−1` edges for `N` anchors, **no cycles**, minimal total length.

The alternative considered was nearest-`k` neighbours, which produces a mesh: every anchor joined to
several others, lines crossing constantly. In a uniform ocean that is the worst case, and it is
exactly the *"currents zigzagging everywhere"* the operator asked to avoid. A tree threads a region;
a mesh webs it.

A tree also makes *"follow the flow upstream"* coherent, which a mesh does not — §4 of the network
design needs direction to mean something, and a graph with cycles has no consistent upstream.

**One consequence worth stating: same-element lines never cross**, because a tree has no cycles. So
**cross-element crossings are the only crossings there are**, which is what §7's mixed sites want.
How often different elements' trees interleave is unmeasured — §9 item 3.

### What currents produce: crystals, and geodes where they cross

[`2026-08-27-residue-network-design.md`](2026-08-27-residue-network-design.md) N6 records a
measurement: `densityAt` is *"exactly 1.0 at every point on the network — verified at a Circle and at
a plain segment."* A plateau. Nothing distinguished a nexus, so §4's *"walk uphill to a maximum"* was
never implemented and could not be. N6 offers three ways out and **this spec takes the second** —
crystals mark currents rather than peaking at nexuses — because it is the only one its own
measurement supports. Literacy becomes *find a line, then follow it*, with direction from the needle
exactly as §5 of that document intends.

**Crystals do not mark a current uniformly, though. They need earth.**

> **A crystal may spawn where a current passes within `w` of earth.**

The current is the subject and earth proximity is a gate on its path — **not** a terrain predicate.
That distinction is load-bearing and was got wrong once in drafting: phrased as "crystals spawn where
rock meets void" it becomes a property of places, independent of any current, and then every rocky
surface in the world is a candidate. Phrased current-first, the crystal-bearing set is
`(band along currents) ∩ (near earth)`, and intersecting a bounded band with a gate can only shrink
it. §3's `2w/R` therefore remains an upper bound on crystal coverage, not a figure the gate can
exceed.

What the gate is for:

| Current | Near earth? | Crystals |
|---|---|---|
| water current through open ocean, well above the seabed | no | **none** |
| wind current high in open sky | no | **none** |
| water current along a seabed or shore | yes | yes |
| wind current close over a ridge | yes | yes |
| fire current in volcanic rock | always | yes |
| earth current through rock | always | yes |

So it removes exactly the two cases that motivated it — kilometres of open water and open air seeding
crystals — and touches nothing else. **Literacy is not meaningfully harmed**: a current under a
meadow is still near earth and still crystallises, so the only unreadable places are open water and
open air, where there is no ground to read.

### Geodes: a crossing in earth makes its own cavity

> **Where two different elements' currents pass within `D` of each other, inside earth, a geode
> forms** — a cavity lined with crystals of both.

This is what makes a crossing *consequential*, which was otherwise undefined: crossings were
nominated as "places that mean something" with nothing attached to them.

**The cavity is created by the crossing, not found in the terrain.** That is the difference between
this and an earlier draft of the crystal rule, which tried to make terrain supply a void for crystals
to grow into and drifted into terrain-as-subject as a result. A geode needs no pre-existing void
because it *is* one.

Hytale already understands geodes, which keeps this in the engine's idiom rather than inventing:
`Rocks/Geodes/` ships **ten colour families** (Blue, Cyan, Green, Pink, Pink_S, Purple, Purple_S,
Red, White, Yellow) at 5–6 variants each; `Rocks/Gems/Geodes/` adds Diamond, Emerald, Ruby, Sapphire,
Topaz, **Voidstone** and **Zephyr**; `Geode_Floating/` is composable from `Roof`/`Wall`/`Spawner`
parts in Base, Forest, Volcanic and Crystal themes; and `Zone4_Tier5/PrefabPatterns/Jungle_Crystal_Geodes.json`
shows they are placed by zone-and-tier prefab patterns. [asset, 0.7.3]

Ten colours and seven gems is ample for pairs drawn from five elements, so an element-pair → geode
mapping is available without new art. Which pair gets which is content design and is not decided here.

**A crossing outside earth produces nothing.** Two currents meeting in open ocean or high air have
nowhere to crystallise, and they take the same gate as ordinary crystals rather than a special case.
Wasteful of a rare event; cheaper than a second rule.

**Anything we ship needs our own prefix.** `checkAssetPackIntegrity` requires a Stormseeker-scoped
prefix on every asset in this mod, so our geodes are named variants of our own and cannot override
`Rocks/Geodes/*`. The guard enforces it; that is what it is for.

## 6. Cost, measured

Measured 2026-10-21 against the live 0.7.3 world with a client connected, via `/fluidscancost`,
anchored on storage confirmed resident (`signalprobe` reported `0=in 32=in 128=in`, and the ±24 window
sat entirely inside it):

| `sampleStep` | reads per candidate | measured ms/candidate | ms per 25-candidate run |
|---|---|---|---|
| 8 (today) | 245 | 0.078 | 2.0 |
| 4 | 1,521 | 0.150 | 3.7 |
| 2 | 10,625 | 0.379 | 9.5 |
| 1 | 79,233 | 1.390 | 34.8 |

**≈36 ns per `SectionReader` read.** Extrapolating from that — *extrapolation, not measurement* — a
96-block measurement radius at step 2 is ~160k reads ≈ 5.7 ms per candidate.

**So cost does not constrain this design.** Every question in §2–§4 is a modelling question, and the
earlier framing of a cost-versus-coverage fork was an artefact of a read-count ratio nobody had
priced. The honest caveat: a headless server holds **no loaded region at all** — verified, every
offset from 0 to 4,096 reported `out` — so these numbers require a connected client and cannot be
reproduced in CI.

---

## 7. What a player meets, end to end

```
terrain ──▶ per-element VOLUME counts ──▶ (strength, hash) suppression
                                         ──▶ ANCHORS (invisible, 3D, ~1 per R)
                                               │
                                               ▼
              terrain-following CURRENTS — one minimum spanning
                      tree per element, no cycles
                                               │
                        ┌──────────────────────┴───────────────────────┐
                        ▼                                              ▼
         current passes near EARTH                    two elements' currents cross,
              ──▶ crystals                                 in earth  ──▶ GEODE
         (nothing in open water or sky)                   (mixed-element, a find)
                        │                                              │
                        └────▶ literacy: find a line, follow it ◀─────┘
```

**Nexuses are gone as a placed entity.** Anchors are invisible geometry with no gameplay meaning;
geodes are the places that mean something, and they are derived rather than authored — which
satisfies R4's *"not authored individually"* for the one thing still derived.

**Circles are gone too** (§8), so nothing in this design is authored except the prefab variants a
geode is built from.

---

## 8. Out of scope: Elemental Circles, in both roles

The base game **does** ship Elemental Circle monuments —
`Server/Prefabs/Monuments/Unique/Elemental_Circles/` holds **Earth** (Druid Circles), **Fire**
(Firelands StoneCircle), **Frost**, **Poison** and **Sand**, each composed from
`Circles`/`Middle`/`Pillars` parts across three tiers, with nine matching NPC spawn beacons under
`Server/NPC/Spawn/Beacons/Zone{1,2,3}/Zone{N}_Elemental_Circle_Tier{1,2,3}.json`. [asset, 0.7.3]
There is **no Storm circle**, which earlier drafts treated as the gap this questline fills — now
confirmed from the assets rather than asserted.

They were considered in two roles and **both are out, by operator decision**:

- **As anchors.** Declined on roster grounds: that set is Earth, Fire, Frost, Poison, Sand, while the
  residue roster decided in
  [`2026-10-21-residue-roster-decision.md`](2026-10-21-residue-roster-decision.md) is the
  cosmological five — Fire, Water, Lightning, Wind, Earth. **Two rows overlap.** Anchoring there
  supplies Fire and Earth, drags in three elements the framework does not model, and leaves Water,
  Lightning and Wind with nothing.
- **As content we author.** A Storm Circle as the sixth monument was drafted and then removed:
  *"Forget the circles. We're not using them for now."*

**Where the needle's recipe lives is therefore undecided, and deliberately left so.** The network
design's circularity still binds — the needle is what reaches the workshop, so its recipe cannot be
*in* the workshop, and must sit somewhere findable by literacy alone. A geode satisfies that, and so
would other things. It is **out of scope here** rather than answered, because nothing in this spec
depends on the answer.

> **Deferral, with a real trigger.** Revisit if Hytale ships Elemental Circle prefabs for **Water,
> Lightning or Wind**. The observation point is one command against the asset tree, and the game
> ships every 2–6 weeks while still adding elements. Until then this design depends on nothing
> Hypixel has not shipped, and authors no monument of its own.

**A correction worth keeping.** This section first claimed the Circles *do not ship*, twice. The first
search read `WorldStructureAsset`s — the wrong asset type. The second searched prefabs but ended in
`| head -20`, and `Server/Prefabs/Cave/…` sorts before `Server/Prefabs/Monuments/…`, so the answer was
truncated out of view. The terrain-derivation design records that exact failure mode — *"an absence
produced by a pipe"* — making this the fifth clean-zero-from-a-surface-that-cannot-hold-the-answer in
a single session, and the only self-inflicted one.

---

## 9. Open numbers, named rather than guessed

1. **Per-element suppression radii.** Five values, kilometre-scale per §3. Earth and Wind saturate
   first, Fire starves first. Expected to need iteration against real terrain; §3's `2w/R` table is
   the starting frame, not an answer. The **empty case** lives here too: an element with no qualifying
   terrain in a region gets no anchor, and whether that is acceptable depends on the element —
   tolerable for Fire, probably not for Lightning, which Stormseeker needs.
2. **Measurement radius.** Wide enough that a lake outweighs a puddle — a judgement about what "an
   area" means to a player, not a derivable quantity. Independent of the suppression radius.
3. **Crossing frequency.** Unmeasured. It sets how rare geodes are, and since the recipe's home is
   out of scope (§8) nothing gates on it — too few means geodes are rare, which is acceptable. **It
   becomes a gating number the moment anything required is placed in a geode**, so measure it before
   that happens rather than after. The old model's N1 asked this of *self*-crossings; trees have no
   cycles, so the question moves entirely to cross-element crossings, which the old model explicitly
   did not model.
4. **Crossing tolerance.** Two polylines crossing *exactly* in 3D is measure-zero, so "cross" has to
   mean "pass within `D` of one another." `D` is undecided.
5. **Influence radius `w`.** Appears in §3's sizing ratio and sets how wide a crystal band reads on
   the ground. Taken as 16 blocks for the table; not chosen.

**Decided here, previously open:** strength is volume (§2); saturation is handled by
`(strength, hash)` suppression rather than a cleverer measure (§3); anchors carry their own y (§4);
topology is a per-element minimum spanning tree (§5); crystals mark currents **where a current passes
near earth**, taking N6's second option but gated rather than uniform (§5); a crossing inside earth
makes a geode, the cavity created by the crossing rather than found in terrain (§5); nexuses are not a
placed entity (§7); Elemental Circles are out of scope in both roles (§8).

**Deliberately not decided:** where the needle's recipe lives (§8), and which element pair maps to
which geode colour (§5). Neither is depended on by anything above.

---

## 10. What would change this decision

- **Crossings turn out to be vanishingly rare or everywhere.** Neither breaks anything while the
  recipe's home is out of scope — rare geodes are acceptable and common ones are merely dull — but
  both would reopen whether consequence should be derived from crossings at all, and either would
  become urgent the moment something required is placed in a geode.
- **Per-element suppression cannot be tuned to satisfy both Earth and Fire.** Then volume is
  insufficient alone and needs a per-element normalisation term — the first thing to try before
  abandoning the approach, and cheaper than the interface/prominence measures §2 rejected.
- **Hash-selected anchors read as arbitrary on the ground.** §3's uniform-region behaviour is
  deterministic but not *motivated* — in mid-ocean the winning anchor is chosen by a hash, and a
  player who reaches one may find nothing there that explains why. Unknown until walked to. If it
  reads badly, the fix is not a cleverer strength measure but giving uniform regions **no** anchors
  at all, which trades coverage for meaning.
- **Hytale ships Circles covering the cosmological five.** §8's deferral fires and authored anchors
  become available, which would make terrain-derived anchors a fallback rather than the mechanism.
- **The generator handover happens.** Workspace deferral #18. `EnvironmentProvider.evaluate` becomes
  reachable, arbitrary-position environment queries become possible, and derivation stops being bound
  to loaded chunks — which changes §6's constraint, not §2's model.

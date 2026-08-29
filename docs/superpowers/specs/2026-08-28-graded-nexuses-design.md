# Graded nexuses — vertical currents, nexus quality, and element identity

**Status: structure decided, numbers provisional.** This extends
`2026-08-27-residue-network-design.md`. It does not supersede it: §4's two-mechanism navigation
split, the no-cores decision, cultivable crystals, and the authored Grand Convergence all stand
unchanged. It adds a third dimension to the currents, gives Circles a quality gradient, closes the
element-identity gap, and sets N1's first target.

`2026-08-28-residue-density-circle-peak-design.md` (N6) is unaffected and remains decided.

---

## 1. What this changes

| Prior | Now |
|---|---|
| Currents are 2D polylines; everything sits at one implied height | Currents have a **height profile**. Topology stays 2D so crossings still exist; height is carried alongside. |
| A Circle is a crossing, full stop | A Circle is a crossing **graded by the vertical separation of the two currents there**. |
| Grand Convergence is a `WorldPoint2d` | It gains a height, and is understood as the **zero-separation limit** of a Circle. |
| `ResidueNetwork(worldSeed, parameters)` | Element identity becomes a **first-class constructor argument**. |
| N1 = "how many Circles per world", untargeted | N1 has a first target and a hard constraint (§7). |

---

## 2. Why the currents cannot simply be 3D

The obvious reading of "currents snake through the world in three axes" is to make the polylines
three-dimensional. That does not work, and the reason is geometric rather than a matter of effort.

**Two one-dimensional curves in three-dimensional space generically do not intersect.** In the
plane, two wandering curves cross as a matter of course; lifted into space they pass over and under
each other instead, and intersection becomes a measure-zero event. Making the currents genuinely 3D
does not make Circles rarer — it makes the count **zero in every world**, and forces a Circle to be
redefined as "closest approach within some ε", which no longer means *the world folded back on
itself*.

We already have the evidence in the easy case: measured over 300 seeds in pure 2D at the shipped
placeholder parameters, **14–17% of worlds have no crossing at all**. Crossings are scarce even
where they are generic.

**The resolution is to separate where a current runs from where it crosses.** Topology stays in the
plane, which guarantees crossings exist and keeps `densityAt` a horizontal read — which is how a
player navigates anyway. Height rides along the arc length. Currents visibly dive underground and
arc into the sky; the crossing mathematics is untouched.

---

## 3. The height profile

Each arm carries a height derived alongside its plan-view walk, in the same style as the existing
heading walk: a correlated random walk on **pitch**, with a mild restoring pull toward the datum so
the current stays in a band rather than wandering off indefinitely.

```
pitch += signed(seed, HEIGHT_DOMAIN + arm, step) · pitchJitter − restore · (y / heightBand)
pitch  = clamp(pitch, −maxPitch, +maxPitch)
y     += sin(pitch) · stepLength
```

`CurrentParameters` gains `pitchJitter` and `heightBand`. Measured across 300 seeds at
`pitchJitter = 0.15`, `heightBand = 96`, current heights at crossings span roughly **−155 to +158**
about the datum (p5–p95) — genuinely subterranean to airborne without anything being authored.

### The datum constraint, and why it is not `:core`'s problem to solve

`ResidueNetwork` is a pure function of `(worldSeed, elementId, parameters, position)` with **no
world access**. That purity is what lets it exist while deferral #18 blocks worldgen, and it is not
negotiable here.

Therefore a current **cannot follow the terrain**. Its height is relative to a fixed datum, not to
the local surface, so the same current at `y = −40` is buried under a hill and floating over a
canyon. This is correct and expected. Sub-project 3 has world access and adapts at expression time:
`:core` supplies a datum-relative height, the expression layer decides what to draw against the
actual ground.

> **Do not resolve this by giving `:core` terrain access.** It would reintroduce the worldgen
> dependency the entire approach is shaped to avoid.

---

## 4. Nexus quality

A Circle is a plan-view crossing. The two currents each have a height there, and the **vertical
separation `|Δy|` between them is the Circle's quality** — small is better.

This is not a filter. Every crossing is a usable nexus; separation sets **efficiency**, not
eligibility. A player is never blocked by a poor world, only slowed — which is required, because
worlds vary by roughly 20× in how good their best nexus is, and a gate on quality would let seed
luck decide whether the questline is completable.

Where the two currents are far apart vertically, the Circle is a **column** spanning between them
rather than a point — one current passing overhead, the other running deep. The awkward case
becomes the dramatic one.

### The Grand Convergence is the limit case

Every arm begins at the convergence, so there the separation is **exactly zero**. The convergence is
not a different kind of object from a Circle; it is the perfect one. Every Circle is graded by how
near it comes to that ideal, and *"the best nexus in this world"* means **"the one most like the
Grand Convergence."**

This is why the player cannot harness it: the ancients already did, and the workshop stands on it.
The player's answer is to assemble a **network of imperfect nexuses** substituting for the one
perfect site they cannot have. The hunt teaches what the destination is before it can be reached,
using nothing but the material.

The convergence also gains a height. `GrandConvergence.locate` currently returns a `WorldPoint2d`,
so "ground level" was never a decision — it was an absence. It stays a pure function of the world
seed alone; the existing Javadoc prohibition on per-element variation is unaffected.

---

## 5. Element identity — closing a silent gap

`DefaultResidueNetwork(long worldSeed, CurrentParameters parameters)` has **no element identity**.
Verified by construction: two notionally different elements sharing a seed and tuning produce the
same convergence, the same Circles, and identical density at 100 probe points. Worse,
`CurrentGeometry.buildArm` sets the initial heading to `2π · armIndex / armCount`, so any two
elements sharing an `armCount` start their arms in **identical directions from the identical
point**, and the jitter domain `0x41726D73` carries no element term.

Six elements would be six copies of one star unless every consumer independently remembers to pass a
different seed — a convention nothing enforces and no test would fail on.

**Fix: element identity becomes a first-class constructor argument**, mixed into the arm-jitter
domain and into a per-element heading offset derived from `(worldSeed, elementId)`. Two elements
then cannot accidentally coincide, structurally rather than by convention.

Note the symmetry this restores. `GrandConvergence` is explicitly guarded *against* per-element
variation, because elements must agree on the shared anchor. The inverse hazard — elements silently
agreeing where they must differ — had no guard at all.

### Cross-element crossings — open, and now more pressing

The prior spec excludes cross-element crossings *"until someone can name what happens at one"*, and
leans on that exclusion for the claim that the six-current web grows linearly. With six elements
sharing one convergence, crossings between different elements would be **common near the centre**,
not exotic.

The graded-nexus mechanic supplies an obvious candidate answer — a crossing of two *different*
elements is the natural shape of a nexus worth more than an ordinary one. **Left open deliberately.**
Adopting it reintroduces the quadratic term the exclusion was written to remove.

---

## 6. Scale — how much of the world is covered

The concern this answers: with six elemental networks eventually sharing one convergence, does the
world end up with crystals everywhere and no legible signal?

Measured by grid-sampling the disc around the convergence and counting points inside at least one
element's influence radius:

| Network | Influence radius | 1 element | 3 elements | 6 elements |
|---|---|---|---|---|
| 4×160 | 16 | 2.18% | 5.03% | 6.66% |
| 4×160 | 24 | 3.24% | 7.49% | **9.79%** |
| 4×160 | 32 | 4.26% | 9.85% | 12.72% |
| 6×240 | 16 | 2.17% | 6.15% | 10.63% |
| 6×240 | 24 | 3.20% | 9.03% | **15.23%** |
| 6×240 | 32 | 4.22% | 11.77% | 19.42% |
| 8×320 | 16 | 2.15% | 5.98% | 11.03% |
| 8×320 | 24 | 3.15% | 8.66% | **15.51%** |

**Coverage is scale-invariant.** The 1-element column barely moves across all three network sizes
(3.24% / 3.20% / 3.15% at radius 24), and the 6-element figure is likewise flat (9.79% / 15.23% /
15.51%).
Growing the network grows the region it occupies in proportion, so density is unchanged. Analytically:

```
coverage ≈ armCount · 2 · influenceRadius / (π · stepsPerArm · stepLength · 0.72)
```

Coverage depends on the **ratio** `armCount / stepsPerArm` and on `influenceRadius` — not on how big
the network is. So the saturation worry has two levers and neither is network size:

- Raising `armCount` alone increases both crossings **and** coverage.
- Raising `stepsPerArm` alone increases crossings and **reduces** coverage.
- `influenceRadius` scales coverage linearly and does not affect crossings at all.

At six elements and `influenceRadius = 24`, coverage is about **15%** — crystals across roughly one
seventh of the charged region. That is a legible signal, not saturation. There is comfortable
headroom before the world reads as uniformly crystalline.

---

## 7. N1 — first target and the one hard constraint

Measured at `pitchJitter = 0.15`, `heightBand = 96`, 100 seeds per configuration:

| Config | Crossings / world | Zero-crossing worlds | Best `\|Δy\|` (median) | Construction |
|---|---|---|---|---|
| **4×160 — shipped placeholder** | 2.2 | **17%** | 43.2 | 3 ms |
| **6×240** | 10.5 | **0%** | 12.0 | 4 ms |
| 8×320 | 27.7 | 0% | 3.5 | 14 ms |
| 8×480 | 43.6 | 0% | 2.3 | 29 ms |
| 12×480 | 102.6 | 0% | 1.0 | 66 ms |

**The one hard constraint is N8: a world with zero crossings has no harvest site and no recoverable
needle recipe, so it is unplayable rather than merely poor.** Everything else on this table is taste,
because every crossing is usable (§4).

**Target: 6×240.** It eliminates zero-crossing worlds across 100 seeds, yields about ten usable
nexuses with one clearly best at around 12 units, and costs 4 ms to construct. Go higher only for a
denser prospecting loop, not for quality.

Provisional, as N1 always was — the real figure comes from play data.

### N8 is downgraded, not closed

Zero in 100 seeds is not proof of impossibility. N8 moves from *"must be solved structurally in the
geometry"* to *"tune, then measure the residual risk and decide whether it is acceptable."* That is a
much cheaper problem and it removes the need for a forced-crossing mechanism in `CurrentGeometry`.

**Before N1 is settled, run the zero-crossing scan at the chosen parameters over a large seed sample
and record the rate.** An unmeasured residual is the same defect in a new place.

---

## 8. Where the payoff lives — direction, not design

Nexus quality needs a reward proportionate to the effort of prospecting. A consumable needle is a
running cost, not a payoff.

**The direction:** harvesting contraptions built at nexuses yield a resource whose rate scales with
nexus quality; that resource funds the **restoration of the ancient workshop** at the Grand
Convergence. The player assembles a network of imperfect nexuses to substitute for the perfect one
already taken.

Recorded as a direction with two binding constraints, not as a design:

1. **State lives in placed objects, never in the field.** The network is computed, never stored — a
   pure function of seed and parameters, which is what avoids the worldgen dependency. A harvester or
   a tuning fork is an ordinary persisted block that *reads* the network; it must never mutate it.
   "Move the currents so they converge" crosses this line and would turn a query into a simulation.
2. **Pacing stays material, not temporal.** The prior spec decided *danger is the pacing mechanism,
   so no time gates are authored on top.* "A better nexus makes restoration quicker" is a time gate
   if restoration accrues on a clock. Restoration must cost **quantities** of a harvested resource,
   with nexus quality setting yield per trip — so a better nexus means fewer dangerous journeys, not
   fewer idle hours.

The economy itself — costs, yields, the fork's effect size — belongs in its own spec.

---

## 9. World scale, verified externally

Checked because the design's spatial assumptions depended on it:

- Hytale worlds are **effectively infinite** and procedurally generated. No world border has been
  found by the community; a technical bound is understood to exist very far out. Infinite generation
  is confirmed for Exploration and Creative modes; Adventure mode is unconfirmed.
- **The central 2 × 2 km is guaranteed to be Zone 1 (Emerald Wilds)**, with Howling Sands (Zone 2)
  immediately south and Whisperfrost Frontiers (Zone 3) immediately north.

Two consequences.

**Spatial budget is not a constraint.** There is no room to run out of, which is why §6 measures
*density* rather than extent.

**But the network is a regional feature, not a global one.** At 6×240 an arm reaches roughly 3.3 km,
and the convergence sits 512–2048 m from origin — so a network spans Zone 1 and crosses into Zones 2
and 3, and beyond its footprint there is no residue anywhere in an unbounded world. This has never
been stated as a decision and should be: residue is a *charged region* of the world, not a property
of the world.

It also makes **N4** — does storm frequency bias where currents run? — considerably more interesting.
Storms are a property of place, and the 0.6.1 table spans about 46× between `Env_Zone3_Glacial_Henges`
and `Env_Zone1_Plains`. A storm current reaching north into Whisperfrost crosses genuinely stormier
ground.

Sources: [Are Hytale Worlds Infinite? — Game8](https://game8.co/games/Hytale/archives/575699) ·
[Are Hytale Worlds Infinite and Randomly Generated? — Hytale.game](https://hytale.game/en/hytale-infinite-worlds-random-generation/) ·
[The Future of World Generation — Hytale](https://hytale.com/news/2026/1/the-future-of-world-generation) ·
[Hytale Zones & Biomes — GPORTAL Wiki](https://www.g-portal.com/wiki/en/hytale-zones/) ·
[Zones — Hytale Wiki](https://hytale.fandom.com/wiki/Zones)

---

## 10. Consequences for the N6 spec

`2026-08-28-residue-density-circle-peak-design.md` §5 justified computing crossings eagerly at
construction using 204,480 pairs / 3 ms — figures from the 4×160 placeholder regime this document
replaces. At the 6×240 target it is 1,036,080 pairs / 4 ms, and with six elements about 24 ms of
server start. Still paid once, still acceptable.

The deferred spatial index keeps its trigger — *build it when network construction appears in a
profile, or when a consumer requires per-region construction* — and simply became more likely to
fire. The N6 spec's figures are updated to match rather than left describing a regime nobody uses.

---

## 11. Open

- **N1** — final crossing count, against play data. First target 6×240 (§7).
- **N4** — does storm frequency bias where currents run? More attractive now (§9).
- **N7** — the Z-filter regression hole. Untouched by this document.
- **N8** — residual zero-crossing rate at the chosen parameters. Downgraded, not closed (§7).
- **N9** — what happens at a cross-element crossing (§5).
- **N10** — is residue a regional feature by decision, and how far does a network reach (§9)?
- **N11** — the harvest and restoration economy (§8).
- **N2, N5** — unchanged from the prior spec.

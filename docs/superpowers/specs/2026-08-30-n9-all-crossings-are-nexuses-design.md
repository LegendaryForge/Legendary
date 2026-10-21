# N9 — all crossings are nexuses

> ## ⚠️ SUPERSEDED IN DIRECTION — read `2026-10-20-residue-terrain-derivation-design.md` first
>
> **Added 2026-10-21.** The status line below reads as current and is not. The direction changed in
> discussion on 2026-10-20 and **nothing was retired**, so this document's own framing was the only
> thing a reader saw. That is the exact failure this repo has recorded twice before: a stale
> orientation doc front-loads a wrong model before anything can contradict it.
>
> **Specifically superseded:** §1's crossing-as-nexus definition is demoted — nexuses are now placed at terrain sites and lines drawn between them, so a crossing is not what makes a nexus. §8's supply arithmetic and §10's wild-availability problem are both moot, since extent is no longer bounded. Its supply premise was already refuted by its own calibration.
>
> **What is still good here:** the measurements, and the reasoning about why each choice was made.
> Those are a record of how the design got where it is. The *conclusions* are superseded.
>
> It is kept rather than deleted because it is the record of what was believed and when. Do not build
> on it without reading the newer document's §11, which lists exactly what survives.

**Status: decided 2026-08-30.** Closes N9, open since `2026-08-27-residue-network-design.md` §2
excluded cross-element crossings *"until someone can name what happens at one."*

Every figure below was measured against the shipped `:core` by `scripts/residue-scans/N9Scan.java`
(committed with its two output files, so every figure here can be re-derived)
(6 elements x 6 arms x 240 steps, 40 seeds, both published height calibrations). Where a figure is
calibration-dependent it is given for both; where a claim is unmeasured it says so.

---

## 1. The decision

**A nexus is any crossing of two currents, not only a current with itself.** Six elements give
fifteen unordered pairs, so Fire×Ice and Fire×Lightning are different nexuses rather than both
being "a nexus".

This was already named as the obvious candidate in `2026-08-28-graded-nexuses-design.md` §5 —
*"a crossing of two different elements is the natural shape of a nexus worth more than an ordinary
one"* — and left open because *"adopting it reintroduces the quadratic term the exclusion was
written to remove."* That cost is accepted.

### Why now

Session 13's N10 work died repeatedly on one constraint: an element's own currents ran **parallel**,
and parallel lines never cross. One element per direction, one per axis, antipodal face pairing —
all failed there, and the responses built to survive it (three axes x a two-colouring, per-cell
rotation of the axis→element map) were all patches at the assignment layer.

N9 dissolves the constraint outright. Lightning may run perfectly parallel to itself and still gain
nexuses by crossing Fire and Ice. The elaborate assignment schemes stop being load-bearing and do
not need building. Recorded as
`[[Hytale_Observations#2026-08-29 WRONG-LAYER: every dead end this session was worked around at the assignment layer, when the real lever is the definition of a nexus]]`.

---

## 2. What the original exclusion actually said, and which half survives

§2 of the residue-network spec gave **two** reasons, and they have different fates.

1. *"the combinatorics are large, and no gameplay has been designed for them"* — **answered** by §5
   below.
2. *"each current is tunable **independently**: questline #2 sets frost's spacing without knowing
   storm's, and the finished web grows linearly"* — **not answerable.** Under N9 storm's nexus count
   is a function of frost's `headingJitter`. This is a real loss of module independence, and §3
   pays for it rather than pretending otherwise.

---

## 3. Ownership — world structure moves into `:core`

**The element roster and all six currents' `CurrentParameters` live in `:core`.** They are world
structure: present from first login, identical for every player, unchanged by which questlines are
installed. Questlines own **content only** — crystals, quests, and per-element overrides such as
Lightning's null core.

This is forced by §2's second reason. If a questline owned its element's tuning, installing
questline #2 would silently reshape Stormseeker's nexus map in an existing world, which contradicts
**R1** (*"Residue is world structure, not a reward. Present from first login, identical for every
player, gated on no quest state"*). Fixing the roster and the tuning in `:core` restores the
guarantee that N9 would otherwise have broken.

> **This supersedes** the note on `CurrentParameters.defaults()` stating that the real per-element
> figures are *"set against play data, per element, in the questline module."* Tuning is no longer
> a questline's to set.

### `ResidueField` — a world-level owner

A nexus belongs to a **pair** of elements, so it cannot be owned by a per-element object.
`DefaultResidueNetwork` computes its nexuses from its own arms via `geometry.crossings()` and cannot
see another element's geometry at all.

- **New `:core` type `ResidueField`** holds all six currents for a world, computes the crossing set
  once, and owns `nexusesWithin`. Each nexus carries its element pair.
- **`ResidueNetwork` keeps `densityAt` and `flowAt`, and loses `nexusesWithin`.**

One nexus is one object. The pair type has a home, quality grading has a single owner, and the
quadratic scan runs once per world instead of once per element.

Rejected: leaving `nexusesWithin` on `ResidueNetwork` as a filtered per-element view. It is the
smaller API change, but it makes one nexus into two objects that must agree — the shape that let
the local-maximum claim in `2026-08-29-six-element-residue-framework-design.md` §6.3 go unreviewed
until it was found false.

---

## 4. Eligibility is 3D

**A crossing is a nexus only if the two currents are within `influenceRadius` of each other in
three dimensions.** A Fire arm at `y = 50` and an Ice arm at `y = 150` cross in plan view and are
not a meeting.

This needs **no new parameter**: measure distance in 3D rather than plan view and the gate is
already there — an arm 100 blocks up is simply beyond the influence radius and contributes nothing.

### It contradicts a recorded decision, and the contradiction is resolved by measurement

`2026-08-28-graded-nexuses-design.md` §4 explicitly refuses this filter:

> *"This is not a filter. Every crossing is a usable nexus; separation sets **efficiency**, not
> eligibility… a gate on quality would let seed luck decide whether the questline is completable."*

That reasoning was sound when nexuses were scarce. **N9's abundance pays for the gate**, and the
completability risk it guarded against does not materialise:

| Supply per world | calib A | calib B |
|---|---|---|
| same-element only (today) | 63 | 63 |
| all pairs, plan view (ungated) | 503 | 503 |
| all pairs, `\|Δy\|` < 12 | 82 | 62 |
| **all pairs, `\|Δy\|` < 24 (= influenceRadius)** | **140** | **98** |
| all pairs, `\|Δy\|` < 48 | 239 | 157 |

**Zero worlds of 40 were left without a nexus at any gate value, in either calibration.** Ungated,
N9 is an 8x avalanche; gated at the influence radius it is 1.6–2.2x today's supply.

The two decisions only work together: N9 without the gate floods the world with vertically absurd
nexuses, and the gate without N9 would strand worlds.

---

## 5. What happens at a cross-element crossing

**Both elements' crystals form in one geode**, because both fields peak there. Each element's
current passes through the crossing, so each accumulates its own local maximum at that point (§6) —
Fire peaks and Lightning peaks, and both crystals spawn. At a Lightning self-crossing only Lightning
runs, so only yellow.

"Worth more than an ordinary one" therefore means **two harvestable colours for one journey** — a
payoff already legible in the literacy the player spends two acts learning (*more crystals, more
power*). It needs no new resource, no alloy, and no new asset family against the nine shipped
crystal families.

Quality grading is unchanged in form: `|Δy|` at the crossing, measuring Fire's height against
Lightning's instead of one element's two arms.

**Rejected: a pair-specific yield.** Fifteen pairs each unlocking something obtainable nowhere else
would pull the harvest economy — explicitly its own spec per graded-nexus §8 — into this one, and
would need fifteen asset families.

### Density peaks only where the element runs

An element's density peaks **only at crossings its own current participates in.** Lightning does not
peak at a Fire×Ice crossing: no Lightning current passes there, `currentTerm` is ~0, and a yellow
peak would mean yellow crystal where no yellow current runs.

Stated as a decision because nothing in the prior specs says it, and the opposite — every field
peaking at every nexus — is equally constructible and would break the fiction.

**Where the wound shows.** Absorption is world-level (§7), so there is no band in which a point is
a Fire nexus but not a Lightning one — inside the radius it is nobody's nexus. Lightning's wound is
legible in the **ordinary current** instead: within `nullRadius` the ground carries five colours and
no yellow. That is the original design intent, and it does not need the nexus set to differ per
element.

---

## 6. The density model — accumulate, do not take the nearest

**`densityAt` sums the contribution of every nearby arm of that element instead of taking the
nearest one.**

Today density is *distance to the nearest arm*, plus a bolted-on nexus term whose only purpose is to
make a crossing read as a peak — `nexusWeight` and `nexusRadius` exist solely to author that peak.
Accumulation derives it instead:

| accumulated sum | calib A | calib B |
|---|---|---|
| on an ordinary arm | 2.32 | 2.32 |
| at a gated nexus | 4.51 | 4.66 |
| at the Grand Convergence | 47.9 | 47.9 |

- The **nexus peak is emergent** — two arms pass through the point, so it is twice an ordinary arm.
- The **convergence is the global maximum** — every arm starts there, ~20x an ordinary arm — which
  is what graded-nexus §4 already asserts it is (*"not a different kind of object from a Circle; it
  is the perfect one"*), now by construction rather than by assertion.

**`nexusWeight` and `nexusRadius` are deleted.** This is a net reduction in the tuning surface.

### Attenuation is by path length, not by distance

**A current weakens with how far it has travelled from the convergence**, not with how far it is
from one.

This is not a tuning preference; **Euclidean falloff cannot work, structurally.** Both arms of a
crossing pass through the same point, so they are the same distance from the convergence and receive
the same factor. Measurement confirms it to three decimals:

| composition — dominant of the two crossing elements | p10 | median | p90 | ≥2:1 |
|---|---|---|---|---|
| accumulation, no attenuation | 0.500 | 0.506 | 0.527 | 1.0% |
| + attenuate by **Euclidean** distance | 0.500 | 0.506 | 0.527 | 1.0% |
| + attenuate by **path length** (calib A) | 0.500 | 0.517 | 0.751 | **18.2%** |
| + attenuate by **path length** (calib B) | 0.500 | 0.512 | 0.699 | **13.9%** |

Path length is the **only** mechanism found that breaks the symmetry between two crossing arms.

It also matches what residue is supposed to be. An arm leaves a convergence strong and tapers until
it reaches the next one; in the N10 lattice, arms from adjacent hubs overlap so the field stays high
at both ends and dips between them, with nothing authored. It gives residue a centre, which the
current model conspicuously lacks — an arm is uniform along its entire 3.8 km today.

> **This inverts a measured finding.** The six-element scan concluded *"the world's best nexus is
> usually far out anyway"*, because there are ~10x more crossings out there. Under path-length
> attenuation far crossings are weak. Every figure in that table needs re-measuring, including the
> ones that chose `nullRadius = 200`. See §9.

### An explicitly rejected claim

An earlier draft of this design argued that accumulation *by itself* would produce composition
ratios, because a second arm of one element passing nearby would tilt the geode. **Measured and
false: 93.6% of geodes land within 45–55%, effectively 1:1.** The two crossing arms are symmetric
and accumulation does not break the symmetry. Recorded because the claim is plausible, was believed,
and survives nowhere except here as a warning.

---

## 7. Absorption is world-level, and at least 150

**Crossings within the absorption radius of a convergence merge into it** rather than standing as
their own nexuses. Already implemented as `CurrentParameters.nexusAbsorptionRadius`
(`01448b1`), currently element-neutral with default 0 and used only by Lightning's null core.

**It moves from `CurrentParameters` to `ResidueField`.** A nexus belongs to a pair, so a per-element
absorption radius has no defined meaning for a Fire×Lightning crossing — Fire's 0 or Lightning's 200?
World-level is both simpler and the only coherent reading.

### Why a radius is needed at all

Without one, every element threads the same geode near the hub, which makes the pair typing
meaningless exactly where the network is richest:

| distance from convergence | median elements | max | ≥4 elements |
|---|---|---|---|
| 0–100 m | **5** | 6 | 80.3% |
| 100–200 m | 2 | 6 | 9.9% |
| 200–300 m | 2 | 4 | 1.6% |
| 1000 m+ | 2 | 3 | 0.0% |

The degeneracy is confined to a small disc. Beyond 200 m a geode is two elements, occasionally
three, and six never occurs beyond 150 m.

| absorption radius | supply, calib A | supply, calib B | max elements |
|---|---|---|---|
| 0 | 2.21x today | 1.57x today | 6 |
| 100 | 1.65x | 1.02x | 6 |
| **150** | **1.49x** | **0.87x** | **4** |
| 200 | 1.37x | 0.77x | 4 |
| 300 | 1.20x | 0.64x | 4 (A) / 3 (B) |

**150 is the smallest radius that eliminates six-element geodes, and it does so in both
calibrations.** 200 is available and aligns with the `nullRadius` already chosen for Lightning on
independent grounds (harvest competition and N8). The final value waits on §8.

### Invariant: absorption radius ≥ every element's null radius

A nexus that survives absorption must not sit inside any element's suppressed core, or that element
fails to peak there and the nexus is not a local maximum of its field — the defect measured and
fixed on 2026-08-30 (`7965b49`), returning in a new place. With absorption at 150 and Lightning's
`nullRadius` at 200, crossings between 150 m and 200 m would reintroduce it exactly.

So either absorption is at least 200, or Lightning's null radius comes down to meet it. The
precondition already shipped on `NullCoreResidueNetwork` — which refuses a delegate whose core is
unabsorbed — enforces this once absorption moves to `ResidueField`, and must move with it.

---

## 8. Blocker — RESOLVED 2026-10-20, and the answer changes two conclusions

The height calibration is **`restore = 0.012`, `maxPitch = 0.44`**, fixed by decision because it
proved unrecoverable. Full reasoning in `2026-08-28-graded-nexuses-design.md` §3; evidence in
`scripts/residue-scans/out-chosen.txt` (300 seeds). The short version: the span is one observable
against two trading-off parameters, six pairs tie at the same error, and this document's own bracket
figures do not reproduce against the committed harness.

The provisional table below is kept because the *measured* answer falls between its two columns and
is more interesting than either:

| absorption 150 | calib A (0.020 / 0.35) | calib B (0.010 / 0.50) | **chosen (0.012 / 0.44)** |
|---|---|---|---|
| supply vs today | 1.49x | 0.87x | **1.03x** |

**N9 at absorption 150 is a wash.** Not half again as many nexuses, not slightly fewer — 64.1 per
world against today's 62, which is inside run-to-run noise. The `|dy|` gate and world-level
absorption together consume the entire gain that the ungated plan-view figure (494) suggests. §1's
framing of "abundance is what pays for the gate" does not survive this: at absorption 150 there is
no surplus left to pay with.

**The wild-density decay is worse than §7 states**, and this is the more serious correction:

| band | §7 reported | measured at chosen calibration |
|---|---|---|
| 0–200 m | 8.2x | 8.15x |
| 200–500 m | 1.8x | **1.41x** |
| 1–2 km | 1.4x | **0.85x** |

Beyond roughly 500 m N9 is **not a gain but a loss** — 0.88x at 500–1000 m and 0.85x at 1–2 km.
The N10 "wild availability" problem is therefore strictly harder than this document claimed, and the
claim that the 1.4x figure was a *lower bound* was wrong in the wrong direction.

> **What this unblocks and what it does not.** Numeric tuning may now proceed, in the stated order:
> height profile implemented with these constants, then `ResidueField`, then parameters. What it does
> **not** do is rescue the supply story — a world-level absorption radius of 150 and a 24-unit `|dy|`
> gate cannot both stand if N9 is meant to increase supply. That is a design decision, not a tuning
> one, and it is now the open question at the top of this document rather than the calibration.
>
> The structural results are unaffected and still hold: which mechanisms work, where the pile-up
> lives, and that Euclidean attenuation cannot break the symmetry between two crossing arms
> (confirmed again in `out-chosen.txt` §3 — Euclidean is identical to no attenuation to three
> decimals, 0.506 median in both).

---

## 9. What this breaks

1. **N6's global reading, again.** Under attenuation a nexus far from the convergence no longer
   reads `1.0`. *"A nexus is a local maximum"* survives and is what the walk-uphill mechanic
   actually uses — the peak is ~12 m wide against a gradient spread over kilometres. *"Density 1.0
   identifies a nexus"* does not survive, having been restored only on 2026-08-30 by absorption
   (`7965b49`). Recorded as a deliberate trade, not a casualty.
2. **The N8 table must be re-measured.** `nullRadius = 200` was chosen from a scan whose *"worlds
   left with no nexus"* column assumed no attenuation and same-element-only nexuses. Both premises
   are now false.
3. **`CurrentParameters` churns again** — `nexusWeight` and `nexusRadius` out, path-length
   attenuation scale in. Third change to that record in one week. Still cheap: the framework is
   placed but not adopted, and this is the last window before questline #2.
4. **The shipped threshold guard stops meaning anything.**
   `absorptionReachesPastTheThresholdWhereSuppressionBreaksThePeak` asserts
   `nullRadius ≥ nexusRadius / nexusWeight`, and §6 deletes both terms on the right. The property it
   protects — that no surviving nexus sits in a band where suppression beats the peak — still
   matters and is now covered by the §7 invariant. The test must be re-derived under accumulation,
   not deleted.
5. **Module independence is lost**, per §2. Paid for by §3.

---

## 10. Open — deliberately not answered here

- **Wild availability.** N9 does **not** solve it. Measured in the radial model, N9's gain decays
  with distance — 8.2x at 0–200 m, 1.8x at 200–500 m, and only **1.4x at 1–2 km** — because N9
  multiplies *pairs*, and pairs need two arms already near each other. Fifteen pairs buy nothing in
  a neighbourhood containing one arm.

  That measurement is a **lower bound**: the far-field decay is largely an artifact of a single
  convergence, whose six arms are kilometres apart by the time they are kilometres out. A lattice
  has arms arriving from several hubs, and session 13 measured the difference in the same direction
  (39 nexuses free-wandering vs 217 spanning convergence-to-convergence).

  So the second-line-family question — whether the inner hexagons are still needed, or whether an
  element **turning** at each convergence generates that family for free — **stays open and belongs
  to N10.** It needs a lattice probe this document does not have.
- **The `[0,1]` normalisation** for accumulated density. Sums are unbounded (2.32 on an arm, 47.9 at
  the convergence). Normalising by arm count guts the interval's meaning; a soft cap compresses the
  contrast. Pick it against the measured distribution when the height profile lands, not before.
- **The harvest economy.** Untouched, per graded-nexus §8. This document says what a cross-element
  nexus *is*, not what it pays.

---

## 11. Consequences for other documents

| Document | Change |
|---|---|
| `2026-08-27-residue-network-design.md` §2 | Cross-element exclusion **reversed**; the linear-growth and independent-tunability claims that rest on it no longer hold |
| `2026-08-28-graded-nexuses-design.md` §4 | The refusal of a `\|Δy\|` eligibility gate is **reversed**, on measured completability grounds |
| `2026-08-28-graded-nexuses-design.md` §5 | Its open cross-element question is **closed** by §5 here |
| `2026-08-29-six-element-residue-framework-design.md` §6 | N8's `nullRadius` table needs re-measuring under attenuation (§9) |
| `CurrentParameters` Javadoc | Per-element tuning is no longer the questline's to set (§3) |

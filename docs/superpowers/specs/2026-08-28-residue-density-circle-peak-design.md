# Making a Circle a peak — resolving N6

**Status: decided, ready to plan.** This resolves **N6** of
`2026-08-27-residue-network-design.md` and amends that spec's §4 by making its central navigation
claim true in code rather than only in prose. Nothing else about the residue network changes.

---

## 1. The defect

Spec §4 states, as load-bearing:

> Because a Circle is a **local maximum** of density, it is reachable by literacy alone — walk
> uphill. No sense of direction is required. **Circles are therefore findable on day one, by
> anyone, with no tool.**

The shipped `DefaultResidueNetwork.densityAt` is `1 − dCurrent / influenceRadius` against the
nearest segment. On the network `dCurrent` is 0, so density is **exactly 1.0 at every point on
every current** — verified at a Circle and at sixteen arbitrary on-current vertices. It is a
plateau. A Circle is not a maximum of it, local or otherwise.

### Why this is a progression gap, not a documentation error

Literacy is the **only** navigation the player has before the needle, and the needle's recipe is
recoverable **only at a Circle** (§5). So the intended chain is: read density → reach a Circle →
recover the needle → follow flow upstream → Grand Convergence.

With a plateau, the chain breaks at its first link. A player walking uphill arrives at a current,
the signal goes flat, and there is no second mechanism — the needle is on the far side of the step
that just failed. The design does not degrade gracefully here; it stops.

### Why no review caught it

Recorded in `[[Hytale_Observations#2026-08-27 SMELL: a design property that spans sub-projects has
no reviewer, and one shipped contradicted]]`. The plan specified the formula, the implementation
matched it verbatim, and eight task-scoped reviews passed — because §7's task list never itemised
the local-maximum property and no single task owned it. §6 of this document is the direct response.

---

## 2. Decision

**Density gains a Circle-proximity term, inside `densityAt`, in `:core`.**

The alternative of leaving `densityAt` alone and having sub-project 3 thicken crystals near Circles
itself was rejected. It produces the same world, but it puts §4's claim in no single place and
gives it no owning test in `:core` — which is precisely the failure mode above, repeated inside its
own fix. It also obliges every future current to re-implement the bump, with nothing detecting
drift between them.

A third option — deriving the peak from geometry alone, using the two nearest *non-adjacent*
segment distances so a crossing falls out with no new parameters — was genuinely attractive and is
recorded here so it is not re-proposed without new reasoning. It was rejected because the peak's
height and radius would become consequences of `headingJitter` and `stepLength`, so tuning N1 or N2
would move the navigation signal as a side effect; and because a subtle geometric predicate is the
exact shape of defect this module has already shipped once, caught only by mutation testing.

---

## 3. The density function

```
dCurrent    = distance to the nearest segment
dCircle     = distance to the nearest Circle
currentTerm = clamp01(1 − dCurrent / influenceRadius)
circleTerm  = clamp01(1 − dCircle  / circleRadius)

density     = (1 − circleWeight) · currentTerm + circleWeight · circleTerm
```

| Position | Density |
|---|---|
| At a Circle | **1.0** |
| On ordinary current, no Circle within `circleRadius` | `1 − circleWeight` |
| Beyond both radii | 0 |

At a Circle both terms are 1. Moving along the current holds `currentTerm` at 1 while `circleTerm`
falls; moving off the current lowers both. The maximum is therefore **strict**, not a shoulder, and
the gradient §4 relies on exists and is monotone along the approach.

### The published contract changes

`ResidueNetwork.densityAt` is currently documented as *"1 on a current, falling to 0 at the
influence radius."* It becomes:

> Residue density in `[0,1]`. **1.0 only at a Circle**; `1 − circleWeight` along ordinary current;
> 0 beyond the influence radius.

The range is unchanged, which is deliberate — an additive bump would have pushed density above 1
and broken the documented interval. Making 1.0 mean *exactly one thing* also converts §4's claim
into a single assertion, which is what it lacked.

---

## 4. Parameters

`CurrentParameters` gains two fields, appended so the existing order is untouched:

| Field | Meaning | Validation |
|---|---|---|
| `circleRadius` | world units; beyond this a Circle contributes nothing | positive, finite |
| `circleWeight` | share of density owned by Circle proximity | `0 ≤ circleWeight < 1` |

`circleWeight` excludes 1.0 deliberately: at 1.0 the current term vanishes and ordinary current
reads as empty ground, which destroys the literacy the whole design rests on. 0.0 **is** permitted
and reproduces present behaviour exactly, which makes it useful as a regression anchor and as the
setting for a current that deliberately has no Circles.

`defaults()` becomes `(4, 160, 16.0, 0.35, 24.0, 12.0, 0.3)`.

`circleRadius: 12.0` is not invented. The shipped game's Elemental Circle spawn beacons use
`BeaconRadius: 10` and `SpawnRadius: 12`, against our default `influenceRadius` of 24 — so a
Circle's zone of influence is about half the current's, on the game's own convention. These remain
placeholder values in every other respect; N1 and N2 set real ones against play data.

This is a breaking change to a public record's canonical constructor. Its only current callers are
`defaults()` and tests, because sub-project 2 does not exist yet. That is the argument for landing
it now: sub-project 2 is the first consumer of this tuning surface, and settling it afterwards
would mean reopening what sub-project 2 had just fixed.

---

## 5. Crossings are computed once

`DefaultResidueNetwork.circlesWithin` currently recomputes the full pairwise crossing scan on every
call — 204,480 intersection tests at the shipped placeholder parameters — although the crossing set is a
pure function of `(worldSeed, parameters)` and does not depend on the query bounds at all.

The scan moves into `CurrentGeometry` as a **final field built in the constructor**, beside `arms`.
`circlesWithin` becomes bounds validation plus a filter over that list; `densityAt` reads the same
list for `dCircle`.

Lazy memoisation was considered and rejected. `densityAt` now needs the crossings, so every
consumer pays for them regardless — laziness would buy nothing except a thread-safety problem on a
multithreaded server. An eager final field has neither.

### Lifecycle, stated because the code already assumes it

Construction becomes O(segments²). Measured: 204,480 pairs / 3 ms at the shipped placeholder
`4×160`, and 1,036,080 pairs / 4 ms at the `6×240` target that
`2026-08-28-graded-nexuses-design.md` §7 sets — about 24 ms of server start once six elements exist.
A `ResidueNetwork` is therefore **built once per (world, element) and held** — not constructed per
tick, per region, or per query. The existing design
already relies on this, since `arms` is built in the constructor; this change raises the cost of
violating it from wasteful to serious, so it is written down rather than implied.

This also gives §7's deferred spatial index an honest proactive trigger in place of a vague one:
**build it when network construction appears in a profile, or when a consumer genuinely requires
per-region construction.** Until one of those occurs, the one-time scan is sufficient.

---

## 6. Tests — the property gets an owner

§1 records why this defect survived eight reviews: no task owned the property. The response is that
the property's test is a **deliverable of this work, not a side effect of it**.

1. **Density at a Circle is strictly greater than at any other point on the same current.** This
   test owns §4's claim. It is the reason the work exists.
2. `1.0` is returned at a Circle and nowhere else.
3. On ordinary current, away from any Circle, density equals `1 − circleWeight`.
4. `circleWeight = 0` reproduces pre-change densities exactly — regression anchor.
5. A network whose parameters produce **no** crossings caps at `1 − circleWeight` everywhere on
   current. The test pins the behaviour; it does **not** bless it. See §7.

### Two constraints on how test 1 is written

**It must assert the crossing count is non-zero before comparing densities.** Last session's review
Finding 1 was a `circlesWithin` test that would have passed against an unconditionally empty list.
Measured, not assumed: at `defaults()`, seed 1 yields **three** crossings, the first at
`(371.1565825821818, 260.6532697218974)`. The 2026-08-27 spec's "the crossing is `c=(371.16,
260.65)`" is that same point at the parameters `ResidueNetworkTest` actually uses
(`stepsPerArm` 80), where seed 1 yields **two**. Neither is "exactly one". A test that hard-codes a
count must state which parameters it means.

**It must be mutation-checked.** Delete the `circleTerm` contribution and re-run: test 1 must fail.
If the suite stays green, the test does not cover the property and the task is not done. This is
the check that found the `u`-range defect twelve tests and three review passes had missed.

---

## 7. N8 — a world with no Circle is unplayable, and 14% of them have none

Found while verifying §6 by executing the network rather than reading it.

| Parameters | Seeds scanned | Zero-crossing worlds | Mean Circles |
|---|---|---|---|
| `defaults()` — `stepsPerArm` 160 | 200 | **28 (14.0%)** | 2.49 |
| `ResidueNetworkTest` — `stepsPerArm` 80 | 200 | **77 (38.5%)** | 0.98 |

Under this design a Circle is the only local maximum, so a world with no crossing has no maximum
and §4's *"findable on day one, by anyone, with no tool"* is simply false there. Worse, the needle's
recipe is recoverable **only at a Circle** (§5), so in those worlds the questline is not degraded —
it is **unreachable**. At present placeholder values that is one world in seven.

This changes what N1 is. N1 was framed as "how many Circles per world" — a flavour and pacing
figure. It carries a **hard floor**: parameters must guarantee **at least one Circle for every
seed**, not merely a good average. Tuning can make zero-crossing worlds rare; it cannot make them
impossible, because the crossings are an emergent property of a correlated random walk.

**Open — N8: how is at least one Circle guaranteed?** Not resolved here, because it is a geometry
question rather than a density question and resolving it inside N6 would widen this work past its
subject. Three directions, unranked and unexplored:

- Tune `headingJitter` / `stepsPerArm` until the zero rate is acceptably small, and accept a
  residual failure rate. Cheapest; leaves a real, if rare, unplayable world.
- Guarantee a crossing structurally in `CurrentGeometry` — force the walk to close at least once.
  Removes the failure; changes the geometry's character and needs its own review.
- Give the Grand Convergence a day-one role when no Circle exists. Note this collides with the
  progression: the convergence is the *gated* destination, and every arm terminates there, so
  making it reachable by literacy alone would make the needle skippable.

**This must be settled before sub-project 2 sets N1**, since N1's target is meaningless without
knowing whether it has a floor.

---

## 8. Out of scope

- **`flowAt`** — unchanged. Direction remains strictly the needle's job; §4's two-mechanism split is
  preserved, not softened.
- **`circlesWithin` semantics** — identical results, lower cost.
- **`grandConvergence`** — unchanged.
- No worldgen, no placement, no persistence. This remains a pure query.
- **N1** (how many Circles per world) stays open, but is no longer purely a tuning figure — see
  §7 for the floor it must clear.
- **N8** (guaranteeing at least one Circle) is raised here and deliberately left open.
- **N7** (the Z-filter regression hole) stays open. The refactor in §5 moves where crossings are
  computed but leaves the bounds filter N7 concerns untouched, so it neither fixes nor invalidates
  it.

---

## 9. A finding recorded on the way, deliberately not acted on

While looking for a shipped frequency to calibrate N1 against, the Elemental Circle monument family
was examined in the 0.6.1 assets. There is no frequency to calibrate against, because **none of it
is placed**:

- 175 monument prefabs ship — Earth 79 (prefabs named `Druid_Circles`), Sand 37, Frost 31, Poison
  14, Fire 14.
- The only reference to `Elemental_Circles` anywhere under `Server/World/` is
  `Monuments.Unique.Elemental_Circles.Fire.Pillar_Forward.*`, used four times as **cave decoration**
  in Zone4_Tier5 Volcano Wastes. No monument placement exists.
- Nine spawn beacons ship (`Zone{1,2,3}_Elemental_Circle_Tier{1,2,3}`). No prefab in the game
  references any spawn beacon — not merely these, but any of the 85 — and all nine have
  `"Environments": []`, placing them with the `Tests/` fixtures and event-driven boss beacons rather
  than with the 56 environment-driven ones.
- `HytaleServer.jar` contains zero occurrences of the string.

**Consequence:** `Spirit_Thunder`'s only spawn path is the three Zone2 Elemental Circle beacons
(verified — only three files under `NPC/Spawn/` name it), and it is the only source of
`Ingredient_Lightning_Essence`. Lightning Essence is therefore unobtainable in the shipped game.
This is a larger version of the hole `docs/setting/hytale-orbis-setting-brief.md` §6 recorded: the
family is not merely missing a storm variant, it is inert, and storm is the one element whose beacon
ships fully wired to a real NPC while having no monument to sit in.

**Not acted on here.** Making our Circles the missing Storm Elemental Circles is attractive and out
of scope for N6. It requires placing monuments, which needs either worldgen — **deferral #18**, not
fired, V1-only `WorldGenModifier` on the generator being retired — or runtime prefab placement,
which remains unproven. It belongs in its own spec, with those two blockers as its first question.

One thing from the dig **is** used here: the beacons' `BeaconRadius: 10` / `SpawnRadius: 12` are the
source of `circleRadius: 12.0` in §4.

---

## 10. Provenance

Design conversation of 2026-08-28, resolving N6 of the 2026-08-27 residue network spec. Asset facts
in §9 were read from the shipped `Assets.zip` and `HytaleServer.jar` of the installed 0.6.1 build,
not from documentation. The density formula and plateau behaviour in §1 were read from
`core/src/main/java/io/github/legendaryforge/legendary/core/internal/residue/DefaultResidueNetwork.java`
at `494d81e`, and both the plateau and the crossing-count figures in §7 were confirmed by executing
that code against 200 seeds, not by reading it.

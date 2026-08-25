# What adopting Hytale's native objectives would cost

**Status:** decision input, **not a decision**. Written 2026-08-25, before the gameplay play test.
Read §5 before relying on any number here.

Companion to `docs/integration/hytale-asset-packs.md` (the mechanism, verified by running) and
`docs/architecture/questline-framework-adoption.md` (which is blocked on this decision).

---

## The question this answers

Hytale ships an asset-driven objective system a mod can contribute to as JSON. Adopting it for
Stormseeker's content spine is attractive, but the honest cost is **the number of Stormseeker verbs
with no built-in task type** — each of those is a custom `ObjectiveTaskAsset` we register and
maintain. That count is below.

The built-in vocabulary, taken from the loader's own discriminator (see `hytale-asset-packs.md` §6):

- **10 task types** — `Bounty` `Craft` `Gather` `KillNPC` `KillSpawnBeacon` `KillSpawnMarker`
  `ReachLocation` `TreasureMap` `UseBlock` `UseEntity`
- **1 task condition** — `SoloInventory`
- **2 trigger conditions** — `HourRange` `Weather`
- **3 completions** — `ClearObjectiveItems` `GiveItems` `Reputation`

---

## 1. Phase-by-phase mapping

Phases per `docs/stormseeker/stormseeker-canonical.md`.

| Phase | Verb the objective system would track | Built-in fit | Gap |
|---|---|---|---|
| **1** The Mark | approach a hovering elemental (~10 blocks) | `ReachLocation` is a fixed marker; `UseEntity` requires interaction, and the design is explicit that the elemental never interacts | **proximity-to-moving-entity** |
| **2** The Trek | arrive at the Resonator | `ReachLocation` | — |
| **3** The Waking | stand on a plate through a 30 s ritual, interruptible on step-off | nothing sustains, times, or interrupts | **timed channel / sustained presence** |
| **4** Trials I (Flowing) | hold movement alignment against the storm gradient, evaluated per tick | nothing continuous | **continuous-alignment evaluation** |
| **4** Trials II (Anchored) | repeatedly reinforce an anchor against progressive decay | `UseEntity` covers the discrete interactions, not the decay loop | **sustained stabilization** |
| **5** The Frame | gather three material classes, then craft the frame | `Gather` + `Craft` | — |
| **6** The Forging | kill the boss; temper bound to encounter progression | `KillNPC` / `KillSpawnBeacon` / `KillSpawnMarker` | **encounter-bound progression** (may reduce to sequential task sets) |

**Four certain custom task types, a fifth likely.** Each is one
`ObjectiveTaskAsset.CODEC.register(...)` call plus a subclass — the same shape
`ObjectiveReputationPlugin` uses to register `Reputation` from a separate module.

Structural fits worth noting: task **sets** are sequential while tasks **within** a set are not,
which matches Phase 4's "trials may be completed in either order" exactly. And `Weather` +
`HourRange` trigger conditions cover storm gating directly — the mechanic the v3.0 design treats as
central.

---

## 2. The finding that inverts the usual intuition

**The gaps cluster precisely where code already exists. The clean fits are where nothing is built.**

- Phases 4's two trials are marked ✅ implemented in the canonical doc (`FlowingTrialEvaluator`,
  `AnchoredTrialSession`); Phase 3's ritual state machine exists as
  `StormseekerAttunementService`. So four of the five custom task types are **wrappers around logic
  we already wrote** — re-housing, not new mechanics.
- Phases 2 and 5, the two that map natively with **zero** gaps, are the ones **not yet
  implemented**. Phase 5's systems are all recorded as "not yet implemented"; Phase 2 is trail
  placement and proximity.

So adoption is cheapest exactly where nothing has been spent, and the expensive-looking half is
mostly moving existing code behind a different interface. The natural assumption is the opposite,
which is why this is worth writing down.

---

## 3. What would actually change hands

The native system would replace **tracking, chaining, markers, UI, and persistence**. It would not
replace **mechanics**. Storm gradients, anchor decay, ritual timing and elemental behaviour stay
ours under either decision.

Concretely, what we would stop maintaining:

- the phase state machine and `StormseekerPhase` as the progress model
- `PropertiesProgressStore` / the on-disk progress store — `ObjectiveHistoryComponent` is a
  persisted per-player ECS component covering both objective and objective-line history. We have
  already had to fix our store once (the unreadable-save quarantine)
- objective tracking and any UI we would otherwise build — the game ships an objective panel,
  markers, and an admin panel

Phase 1's elemental spawn / hover / bolt / trail work is custom **either way**: it is world
behaviour, not objective tracking, so it should not count against either side of the decision.

---

## 4. What it would cost us

Stated fairly, because §2 and §3 read as advocacy:

- **Engine-agnosticism.** `core` is deliberately engine-agnostic and `quests/stormseeker` depends
  only on `core`. Authoring the questline as Hytale assets makes it Hytale-specific. That was the
  point of the module boundary. Counterweight: there is exactly one target engine, `QuestlineModule`
  has zero adopters outside its own tests, and this repo's observations already record three
  designed seams with zero callers.
- **Coupling to an Early Access asset schema** that ships every 2–6 weeks. Counterweight, and it
  runs the other way to intuition: a broken asset fails **loudly at boot** with the offending key
  named — observed directly during the probe — whereas the `Vector3d` package move went unnoticed
  for roughly six months. Loud beats silent.
- **A vocabulary we do not control.** If Hypixel changes a task type's fields, our JSON breaks. The
  `--generate-asset-schema` command makes that detectable in one command, but it is still churn we
  do not own.

---

## 5. Why these numbers are provisional

Two named weaknesses. Do not treat the count as settled until both are closed:

1. **Gameplay is unproven.** Everything above assumes the objective system works as documented at
   runtime. `hytale-asset-packs.md` §8 is explicit that only loading, attribution, override and
   validation have been demonstrated — the probe ran `--bare` with no world. The spike harness
   exists for exactly this; run it before trusting the mapping.
2. **The verbs were read from the design doc, not the implementations.** The Flowing and Anchored
   trials are marked ✅ implemented, but if `quests/stormseeker` diverges from how
   `stormseeker-canonical.md` describes them, the gap count moves. Reading those two
   implementations would firm it up, and is best done *after* the play test confirms the exercise is
   real.

Also unverified: whether Phase 5's "must hold both sigils" gate fits `SoloInventory` (the only task
condition). The sigils are items, so it plausibly does — but that is an assumption, not a finding.

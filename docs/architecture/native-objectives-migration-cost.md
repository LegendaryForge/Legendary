# Native objectives — the decision, and what it cost

**Status: DECIDED 2026-08-25.** Adopt Hytale's native objectives for the Stormseeker **content
spine**. This document was written the same day as a decision *input* and is now the decision
*record*; §1 has been remapped against the v4.0 act structure, which did not exist when the original
estimate was made.

Companions: `docs/integration/hytale-asset-packs.md` (the mechanism and the gameplay proof),
`docs/stormseeker/stormseeker-canonical.md` v4.0 (what is being built),
`docs/architecture/questline-framework-adoption.md` (superseded by this decision).

The built-in vocabulary, from the loader's own discriminator (see `hytale-asset-packs.md` §6):

- **10 task types** — `Bounty` `Craft` `Gather` `KillNPC` `KillSpawnBeacon` `KillSpawnMarker`
  `ReachLocation` `TreasureMap` `UseBlock` `UseEntity`
- **1 task condition** — `SoloInventory`
- **2 trigger conditions** — `HourRange` `Weather`
- **3 completions** — `ClearObjectiveItems` `GiveItems` `Reputation`

---

## The decision

**Native:** questline structure, objective chaining, task tracking, player-facing objective text,
and per-player progress and history.

**Ours, unchanged:** every participation rule — access levels, spectators, participation roles,
visibility mode, loot rights. See §4; this is the part most at risk of being misread as replaced.

**Ours, registered into Hytale:** the custom mechanics — roughly one task condition, one completion,
and the trials — through the public `CodecMapCodec.register(...)` seams.

**Discarded:** preconditions P1–P3 in `questline-framework-adoption.md`. They scaffold a hand-rolled
content spine that will no longer exist, so executing them would mean building the thing being
replaced.

---

## 1. Cost, remapped against v4.0

The original estimate mapped the **v3.1 six-phase** design and found *four certain custom task types,
a fifth likely*. v4.0 replaced that structure. Remapped, counting only verbs the objective system
would actually track:

| Act | Verb tracked | Fit |
|---|---|---|
| **I** The Mark | *none* — zero words, no objective entry; the line has not started | — |
| **II** The Trace | arrive at the ruin; read inscriptions | `ReachLocation`, `UseBlock` |
| **III** The Listening | gather storm materials | `Gather` |
| **IV** The Raising | found the Circle; gather tier materials; raise a tier | `UseBlock`, `Gather` |
| **V** The Answer | the provings; forge at the gate | *trials* + `Craft` |

**Custom task types required: zero**, plus whatever the trials become.

Two things caused the collapse from four-plus:

**Act I stopped being tracked.** Because it has no words and no objective entry, the questline line
does not begin until Act II. The elemental's spawn, hover and bolt are custom world code — but they
are custom under *either* decision, so they are no longer a task-tracking gap. v3.1 had this as
Phase 1 with a proximity-to-moving-entity task.

**v4.0's verbs are ordinary verbs made interesting by conditions.** Gather, reach, use-block, craft
— all native. v3.1's were continuous-alignment scoring and timed channels, which have no native
analog. This is the `No neutral verbs` principle paying an unintended dividend: conditioning
ordinary actions on world state is exactly what `WeatherTriggerCondition` and `HourRange` exist for.

### What remains custom

- **~1 task condition** — "this site qualifies" / "a storm is active" at task level.
  `TaskConditionAsset` ships only `SoloInventory`, so this is one `CODEC.register(...)`.
- **~1 completion** — granting Essence of Thunder. Same shape as `Reputation`, which Hypixel
  registers this way from a separate module.
- **The trials** — count unknown, under active development, and **custom under either decision**, so
  they do not differentiate.

`SoloInventoryCondition` is more useful than its name suggests: it checks *this player's* inventory
for N of an item, with `consumeOnCompletion` and `holdInHand` flags. That covers holding Keystones
and consuming them as they become tiers of the Circle.

---

## 2. Why this, beyond the cost

- **v4.0's delivery model depends on it.** Objective titles and descriptions are one of the four
  channels the story reaches players through. Native gives that surface free; hand-rolled means
  building an objective UI to put in-fiction text into.
- **Persistence is the thing already fixed once.** `ObjectiveHistoryComponent` is a persisted
  per-player component, verified working. `PropertiesProgressStore` needed a save-quarantine fix.
- **Verified at runtime, not inferred.** Gather tracking, auto-completion, line chaining, craft
  tracking, reward delivery and persistence were all demonstrated by playing a JSON questline on a
  real server (`hytale-asset-packs.md` §8).
- **The family.** If Stormseeker is the template for a per-element family, questline #2 is JSON plus
  a couple of registrations. Asset `Parent` inheritance makes a shared base practical.
- **Our questline framework has one non-test implementer and zero adopters.**

---

## 3. What changes hands, and what does not

The native system replaces **tracking, chaining, markers, UI and persistence**. It does not replace
**mechanics**. Storm gradients, anchor decay, ritual timing, elemental behaviour, residue currents,
Circle raising — all remain ours under either decision.

Concretely, what we stop maintaining: the phase state machine as the progress model,
`PropertiesProgressStore`, objective tracking, and any objective UI we would otherwise build. The
game ships an objective panel, markers, an admin command suite, and completion history.

---

## 4. The participation rules are a different layer — and they survive

Worth stating plainly, because it is the easiest thing here to get wrong.

**Hytale's objectives system tracks one player's progress.** `ObjectiveHistoryComponent` hangs off a
player; `ActiveObjectiveUUIDs` is per-player. That is the whole of what it does.

**`core`'s encounter rules decide something else entirely** — who may take part in a shared event,
who may only watch, who is refused, and who has a claim on the outcome:

| Type | Values |
|---|---|
| `LegendaryAccessLevel` | `PARTICIPATE` / `SPECTATE` / `DENY` |
| `SpectatorPolicy` | `ALLOW_VIEW_ONLY` / `DISALLOW` |
| `ParticipationRole` | `PARTICIPANT` / `SPECTATOR` |
| `LegendaryVisibilityMode` | `WORLD_VISIBLE` / `INSTANCE_VISIBLE` |

**Hytale ships nothing at that layer.** Checked 2026-08-25: no party system, no group concept, no
encounter membership, no spectator model, no loot-rights model. Every `Party` class in the jar
belongs to BouncyCastle's cryptography library.

So the two stack rather than compete: **the encounter decides who is allowed in; objectives track
each person who is.** Adopting native objectives touches the anti-griefing and
anti-trivialisation rules not at all.

### The two frameworks in `core` have opposite fates

- The **questline** framework has a native competitor that is shipped, better and battle-tested.
  **Superseded.**
- The **encounter** framework has **no native competitor at all**. That gap is real, and it is
  arguably what `core` is for.

Caveat worth keeping honest: the encounter framework is also currently unadopted — `mod/hytale`
references encounters zero times, exactly as it never used the questline SPI. But its design is not
superseded; it is merely unwired. Different problem, different fix.

---

## 5. What it costs us

Stated fairly, because the rest reads as advocacy.

- **Engine-agnosticism, for questline content.** `core` is deliberately engine-agnostic and
  `quests/stormseeker` depends only on `core`. Authoring the questline as Hytale assets makes that
  content Hytale-specific. Counterweight: there is exactly one target engine, `QuestlineModule` has
  zero adopters outside its own tests, and this repository's observations already record three
  designed seams with zero callers. Note the encounter framework keeps its engine-agnosticism — this
  cost applies to content, not to `core` as a whole.
- **Coupling to an Early Access asset schema** that ships every 2–6 weeks. Counterweight, and it runs
  against intuition: a broken asset **fails loudly at boot with the offending key named** — observed
  directly — whereas the `Vector3d` package move went unnoticed for roughly six months. Loud beats
  silent.
- **A vocabulary we do not control.** If Hypixel changes a task type's fields, our JSON breaks.
  `--generate-asset-schema` makes that detectable in one command, but it is still churn we do not own.

---

## 6. Consequences to carry out

- `questline-framework-adoption.md` P1–P3 are **discarded, not pending**.
- `core`'s questline SPI shrinks to "what Java does a questline register" — custom task types,
  conditions, completions and ECS systems. Re-specifying it is open work; deleting it outright was
  considered and not chosen.
- `StormseekerPhase` as the progress model is superseded by objective and line history. The enum
  rename already outstanding from v4.0 should be considered alongside whether the enum survives.
- `mod/hytale`'s `manifest.json` needs `"IncludesAssetPack": true`, and the module needs a
  `Server/Objective/...` asset tree.

---

## 7. Still open

- **The trials' mechanics**, and therefore their custom task-type count.
- Whether `core`'s questline SPI is re-specified or retired.
- Whether `StormseekerPhase` survives in any form.

---

## Superseded by this decision

The original §5 named two weaknesses. Both are now closed, and the record is kept because the second
one is why the cost estimate moved:

1. *"Gameplay is unproven."* Closed by the play test — see `hytale-asset-packs.md` §8.
2. *"The verbs were read from the design doc, not the implementations."* Closed, and it mattered.
   Reading them found `FlowingTrialEvaluator` scores self-coherence with **no storm gradient and no
   convergence point**, and `AnchoredTrialSession` is `REQUIRED_STATIONARY_TICKS = 40` — both
   scaffolding, both self-described as such. The original claim that four custom task types would
   "wrap logic already written" was therefore too generous: there is very little implemented
   mechanic to preserve. That made the decision easier rather than harder.

# Stormseeker × Orbis — Alignment Recommendations

> Depends on `../integration/hytale-capability-audit.md` (what the engine can do, build 0.5.9) and
> `../setting/hytale-orbis-setting-brief.md` (what the world contains). Read either of those for
> evidence; this document only draws conclusions from them.
>
> Written 2026-08-24, before any code or lore changes. Nothing here has been implemented.

---

## Verdict

**Canon-native is achievable, and it is cheaper than staying canon-adjacent.**

That was not the expected answer. The premise going in was that Stormseeker had invented its own
cosmology — leylines, a Resonator, sigils, attunement — and that fitting it to Orbis would mean
rework. The rework is real but small, because the questline's *substance* already matches Hytale's
storm content almost item for item. What does not match is its **vocabulary**, and vocabulary is
the cheapest thing in a design to change.

The strongest signal is not any single asset. It is that Hytale's stated narrative method —
"archaeology", learn by exploring, explicitly *not* "a linear campaign with constant quest text and
cutscenes" — is the same principle Stormseeker wrote down independently as *"No quest UI required.
The world teaches the player through feel, not markers."* We are not bending a design to fit a
setting. We are deleting a seam between two designs that already agree.

---

## 1. The mapping

Every row on the right is shipped content in build 0.5.9.

| Stormseeker invention | Canon equivalent | Note |
|---|---|---|
| "air/electrical elemental" that watches the player | **`Spirit_Thunder`** | Modelled, textured, has a drop list and a bestiary entry. Zone 2 only. |
| Class B storm-bound material | **`Ingredient_Lightning_Essence`** ("Essence of Lightning") | `Spirit_Thunder` is its **only** source in the whole game. |
| — (no equivalent proposed) | **Storm Hide → Storm Leather → armour**, **Stormsilk**, **Storm Thistle** | An entire storm material chain already exists and is currently ungated by any story. |
| "Resonator (Ancient Air Leyline Calibration Station)" | **an Air/Storm `Elemental_Circle`** | The monument family exists for Earth, Fire, Frost, Poison, Sand. **Storm is missing.** |
| Resonator architecture | **`Temple_Wind` block set** + **"Statue of a Silent Deity"** (Gaia) | A complete wind-temple kit, already art-finished. |
| "leylines", "leyline sight" | *nothing* — the word does not exist in Orbis | Needs replacing, not translating. See §4. |
| "sigil" | *nothing* — no sigils, no runes | The *concept* (a binary earned proof) is fine; the word is foreign. |
| "attunement" | *nothing* | Same. |
| "Ancient Forge" crafting locus | **Arcanist's Workbench** (`Bench_Arcane`) | Already crafts Ancient Gateways and Portal Fragments. |
| Milestone/progression tokens | **Memories** — "The Heart of Orbis" | Capacity/level system that already gates recipes. |
| Storm gating | **`Zone2_Thunder_Storm`** | The only thunder storm in the game. |

Nine of eleven rows resolve to something that already exists. **Two require invention, and one of
those is a gap in Hytale's own content rather than in ours.**

---

## 2. The biggest decision: Hytale has a quest framework

`builtin.adventure.objectives` ships `ObjectiveLineAsset` (an id, a list of objectives, and
`nextObjectiveLineIds[]` so lines chain), `ObjectiveAsset` (task sets + completion handlers),
twelve task types, three trigger conditions including **`WeatherTriggerCondition`**, location
markers with entry/exit volumes, reward handlers, persistent history, transactional spawn records,
NPC hooks, and an admin panel. Both asset classes expose `getAssetStore()`, which is exactly the
argument `PluginBase.getAssetRegistry().register(...)` takes.

**A questline can be shipped as JSON.** Stormseeker currently hand-rolls the equivalent in Java:
`StormseekerObjectives`, `StormseekerObjectiveSnapshotService`, `StormseekerQuestSteps`,
`StormseekerQuestStepMapper`, gate registration, milestone emission, progress persistence.

This is not a call to delete that code, and it would be wrong to frame it as one. Be precise about
what the native system does and does not cover:

**What it would replace well** — questline structure and chaining, per-task progress, storm gating
(`WeatherTriggerCondition` takes `weatherIds[]` directly), reach-a-location objectives, gather and
craft objectives for Phase 3 materials, kill objectives against `Spirit_Thunder`, reward delivery,
completion history, and the objective panel UI. All of Phase 3's "materials + validation gates",
most of Phases 0–1, and all of the progress bookkeeping.

**What it cannot express** — the two trials. `FlowingTrialEvaluator` scores continuous alignment
against a storm gradient every tick; `AnchoredTrialSession` evaluates a sustained stationary streak
with progressive instability. There is no native task type for "maintain a continuous property over
time". Those remain ECS systems we write, and they are the most original thing in the design.

**Recommendation:** adopt the native framework as the *spine* — objective lines, gating, rewards,
persistence — and keep the trials as custom ECS systems that a native objective's completion
condition consults. That is roughly the split the design already has between
`quests/stormseeker` (trial logic) and the quest-infrastructure classes (bookkeeping); it just
moves the bookkeeping half onto a platform that maintains it for us, and gets the objective UI,
history and admin tooling for free.

**Do not act on this yet.** It is the largest structural decision on the table and it deserves its
own brainstorm. Flagging it here because every hour spent extending the hand-rolled quest
infrastructure before that decision is made is an hour spent on a possible duplicate.

---

## 3. What the February audit blocked that is now open

Four capabilities the v3.0 design was written *around* are available. Phase 0 and Phase 1 were
shaped by their absence:

- **Pathfinding.** February planned "not full A\* pathfinding — more like smart straight line with
  structure avoidance" because navigation was "not found". `server.npc.navigation` has a complete
  A\* implementation with `AStarWithTarget`, `AStarEvaluator` and `PathFollower`. **The elemental
  can genuinely pathfind**, and "the elemental knows the player can't fly" stops being a heuristic
  we implement and becomes an evaluator we configure.
- **Particles.** Recorded as "Not found → fallback: DynamicLight or entity-attached emitters". A
  full particle asset system exists and 1,743 particle spawners ship with the game. The Flowing
  Trial's gradient feedback — "storm effects cohere and intensify" — is directly expressible.
- **Audio.** Recorded as "Unknown — may need asset pipeline". `AudioComponent`, sound events and
  audio categories all exist; 4,243 `.ogg` files ship, including three thunder emitters.
- **Camera shake.** Confirmed as a first-class asset with `ShakeIntensity` and an interaction type.

And one that is *partly* open: **movement control**. There is still no speed multiplier, so v2.0's
soft "directional resistance" remains impossible. But `MovementEffects` exposes per-direction
disable flags (`disableForward`/`disableBackward`/`disableLeft`/`disableRight`/`disableSprint`/
`disableJump`) and Trigger Volumes have a `SetVelocityEffect`. **The storm can hold you in place or
push you; it cannot make walking merely feel heavy.**

Separately, **Trigger Volumes** (Update 5) mean any "when the player enters this region during a
storm, do these things" beat needs no plugin code at all.

---

## 4. What has to change

**Replace the leyline layer.** It is the only invented concept with no canon anchor and real design
weight — Phase 1.5's entire reward is "Leyline Sight". Two candidate directions, both canon-native:

- *Storm-residue sight.* Lightning Essence lore says elementals are "born from [storm magic's]
  lingering traces". A perception that reveals **residue** — where storm magic has pooled, where
  elementals will form — is derived directly from shipped text, and it makes Class C's "spatial
  discovery" role work without leylines.
- *Arcanist instrumentation.* The Arcanist's Workbench, Ancient Gateways and Portal Fragments
  establish that mortals built devices to perceive and channel power. A recovered arcane instrument
  is a canon-shaped reason to see something others cannot.

The second fits the "hubris of advanced civilisations" theme that runs through Orbis' flavour text
and gives the Resonator a canon reason to exist as a *ruin*.

**Fix the reward-that-rewards-nothing.** Whatever replaces Leyline Sight must change something at
the moment it is granted. The current design grants a perception whose only stated payoff (finding
Class C crystals) explicitly works without it.

**Rename sigils and attunement**, or accept them as mod-local jargon. Lower priority than the
leyline question — they are labels on sound mechanics, not load-bearing world claims.

**Fix `HytaleWeatherReader`.** Two defects, both verified against shipped assets:
1. It matches weather by **ID substring**. Weather assets carry a `Tags` map, which is the intended
   mechanism, and tag placement is inconsistent across zones — `Zone1_Storm` puts the storm marker
   under `Rain`, `Zone2_Sand_Storm` puts it under `Zone2`. Read tags, handle both shapes.
2. Its javadoc claims storms are IDs containing `"storm"` **or `"thunder"`**; `isStorm()` checks
   only `"storm"`. `isThunderStorm()` does match `Zone2_Thunder_Storm`, so it works — **but only in
   Zone 2**, which is a design constraint nobody wrote down, not a bug.

**Re-examine the skip path against storm rarity.** `Zone1_Storm` carries `Weight: 1` against
`Zone1_Sunny`'s `52`. Storms are ~1.5% of forecast rolls. A design where Phases 0→1→1.5 must
complete "within a single storm" is gated on a rare event, and the skip path — walk onto a plate
during any storm and attune — becomes the *likely* first contact rather than the exception. That
was already the sharpest open design question; the weights make it sharper.

---

## 5. What should not change

- **The Thunderfury parallel survives contact with canon, and improves.** Thunderfury's bindings
  were RNG boss drops; Stormseeker replaced them with deterministic trials, which was the right
  inversion. Canon adds something better than a reskin: **Essence of Lightning drops only from
  `Spirit_Thunder`**, so a material grind against storm elementals is already the game's design,
  not ours. Elementium ore ↔ Lightning Essence maps cleanly.
- **The two trials.** Move-with-the-storm versus hold-the-storm, reading the same `MotionSample`
  through inverted predicates, is genuinely original and the native objectives system cannot
  express it. This is the part of the design worth protecting.
- **The philosophy.** No quest UI, world teaches through feel, deterministic and testable, storm as
  gate rather than enemy. It matches the studio's stated approach. Keep it and cite it.

---

## 6. Risks

- **The curse is announced and empty.** The Cursebreaker arc will fill story space that is currently
  vacant. A questline that answers *"what is the true source of storm magic"* — which shipped lore
  explicitly marks unknown — is building on ground the studio has reserved. Prefer designs that
  **use** that unknown as atmosphere over designs that **resolve** it.
- **Everything in the capability audit is one launcher update from being wrong.** It has happened
  twice already: `Vector3d` moved packages and went unnoticed for six months, and a Java 25 jar
  broke the build project-wide on 2026-08-17. Re-run the audit per update.
- **Zone 2 is a narrow home.** Thunder storms and thunder spirits exist only there. Committing
  Stormseeker to Zone 2 is coherent and canon-native, but it makes the questline a regional story.
  If it should be world-spanning, it has to key on generic storms (`Rain: ["Storm"]`, present in
  every zone) and give up thunder specificity.
- **We do not know the zones' names.** Shipped assets identify them only by number and tier. Writing
  lore that names Zone 2 requires a source we do not have.

---

## 7. Suggested order

1. **Decide fidelity** — canon-native or canon-compatible — now that the evidence is in. §1
   suggests canon-native costs less than expected.
2. **Resolve the leyline question** (§4). It is upstream of Phase 1.5's reward, Class C materials,
   and the Resonator's identity, and nothing else can be settled around it.
3. **Settle the skip path and storm rarity** (§4). It determines whether Phases 0–1 are real content.
4. **Brainstorm the native-objectives adoption separately** (§2). Largest structural call; do not
   fold it into a lore pass.
5. Only then return to code. The weather-reader fix is small enough to do at any point.

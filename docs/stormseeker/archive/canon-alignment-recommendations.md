# Stormseeker × Orbis — Alignment Recommendations

> **ARCHIVED 2026-08-25 — superseded, not withdrawn.** This document was explicitly
> *recommends, not decides*. It has now been decided, and its conclusions are absorbed into
> `../stormseeker-canonical.md` v4.0 and the design record at
> `../../superpowers/specs/2026-08-25-stormseeker-narrative-redesign-design.md`.
>
> What it got right and what carried forward: residue replacing leylines as a **rename** rather than
> a replacement; the Elemental Circles as the load-bearing canon hook; the storm-rarity problem;
> canon-native costing less than assumed.
>
> Where the decision went further than this document proposed: the Circle is **raised by the
> player**, not found or claimed; the questline was rewritten from canon rather than realigned; and
> the invented vocabulary was dropped entirely rather than renamed.
>
> Kept for provenance. Do not treat it as current.


> Depends on `../integration/hytale-capability-audit.md` (what the engine can do, build 0.5.9) and
> `../setting/hytale-orbis-setting-brief.md` (what the world contains). Read either of those for
> evidence; this document only draws conclusions from them.
>
> Written 2026-08-24, before any code or lore changes. Nothing here has been implemented.
>
> **Revised 2026-08-24** — phase numbers updated to the renumbered scheme (`UNTOUCHED`,
> Phases 1–6, `COMPLETE`); the leyline recommendation corrected from *replace* to *rename*;
> the "reward that rewards nothing" claim retracted; weather-prediction and shader limits
> recorded. Corrections are marked in place rather than silently edited.

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
| "leylines", "leyline sight" | **residue currents**, derived from shipped Lightning Essence text | Rename the substance; keep the currents and their intersections. See §4. |
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
craft objectives for Phase 5 (The Frame) materials, kill objectives against `Spirit_Thunder`, reward delivery,
completion history, and the objective panel UI. All of Phase 5's "materials + validation gates",
most of Phases 1–2, and all of the progress bookkeeping.

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

Four capabilities the v3.0 design was written *around* are available. Phases 1 and 2 were
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

**Rename the leyline layer; keep the architecture.** An earlier revision of this document said the
leyline concept should be *replaced*, on the grounds that it was a generic fantasy import with no
canon anchor. That was too narrow a reading of what it is for. The intended architecture — recorded
here because it is not written down anywhere else — is:

> **Every element has its own residue currents flowing through the world. Currents intersect at
> nexus points. The nexus points are the Elemental Circles.**

That is a better fit for shipped content than the storm-only version, and it explains something the
assets otherwise leave unexplained: Hytale has circle prefabs for **five** different elements and
spawn beacons in three zones hosting **three different spirits** (`Spirit_Root` in Zone 1,
`Spirit_Thunder` in Zone 2, `Spirit_Frost` in Zone 3). If circles are nexus points, the elemental
that haunts one is the dominant current there. Nothing in the game says this — but nothing
contradicts it either, and the circles are the largest piece of unexplained authored content found
in the audit.

So the change is **vocabulary, not structure**. "Leyline" has zero occurrences in Orbis; *residue*
is derived directly from shipped text — `Ingredient_Lightning_Essence` says elementals are "born
from [storm magic's] **lingering traces**". Rename the substance, keep the currents, keep the
intersections.

Stormseeker is then the **first** of a per-element family, not a bespoke questline: lightning
residue, the storm nexus, `Spirit_Thunder`, `Essence of Lightning`. Frost is the next most complete
(spirit + essence + dedicated tiered circle art + `Zone3_Snow_Storm` + a whole zone).

**Longer-term, and recorded now so the framework is built for it:** an *Ancient Master Forge* at the
rare nexus where **all** elemental currents intersect — the shared endgame locus for the whole
legendary family rather than a Stormseeker-specific "Ancient Forge". Canon offers a lineage for it:
the **Arcanist's Workbench** already crafts Ancient Gateways and Portal Fragments, so mortals
building an instrument to work at a confluence of power is established behaviour, and the
"hubris of advanced civilisations" theme gives it a reason to be a ruin. Future work — noted here
only so that per-element questline #1 does not hard-code assumptions that questline #2 has to undo.

**On "Leyline Sight" being a reward that rewards nothing.** An earlier revision of this document
made that claim, on the grounds that Class C crystals are "visible even before attunement". **That
was wrong, or at least far too strong.** Visibility is not the point: the sight tells you *where to
look*, not *what a crystal looks like*. Currents → intersections → nexus → concentrations. It is a
search tool in a large world, not a reveal tool, and that is a real power. The reward stands as
designed; what it needs is a name that exists in Orbis.

**Know what the perception can actually be.** `PerceptionToggleHandler` is a stub — one boolean and
a `println` — and its comment says *"Logic for post-processing shaders will be implemented in Phase
D."* Per the capability audit, screen-space shaders are almost certainly not available to a mod: the
client is a closed native binary. The vision has to be built **in the world**, from particles and
`DynamicLight`, not as a lens over the camera. That is a constraint on what it can look like, not on
what it can do — currents, intersections and concentrations are all expressible as world effects.

Two defects in that stub while it is being replaced: it holds a **single** `leylineSightActive`
field with no player keying (one instance, one flag, every player), and it is not wired to anything.

**Storm timing cannot be sensed, only read.** There is no forecasting API — see the capability
audit. A perception cannot tell the player a storm is coming. It *can* make a storm far more
legible while it is happening: residue surges, and an attuned player sees which nexus is live and
where concentrations are, right now. That does not fix storm rarity, but it changes what rarity
costs — the unattuned wait out the rain, the attuned can read it. For the finale specifically,
`setForcedWeather` exists and a climax should probably not be hostage to a ~1.5% roll; the cost is
that it changes the sky for everyone in that environment.

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
`Zone1_Sunny`'s `52`. Storms are ~1.5% of forecast rolls. A design where Phases 1→2→3 must
complete "within a single storm" is gated on a rare event, and the skip path — walk onto a plate
during any storm and attune — becomes the *likely* first contact rather than the exception. That
was already the sharpest open design question; the weights make it sharper.

---

## 4b. Proposal: the nexus is dangerous during a storm

Hytale's storms are **purely ambient** today — sky, clouds, particles, audio. No damage, and no
lightning entity at all (zero matching classes in the jar). Nothing about weather can hurt you.

That is a problem for the intended finale, which asks the player to expose the frame to the storm
and let it be struck. Inverting a feared mechanic is the whole trick, and there is no fear to
invert. But making *storms* dangerous globally is a heavy imposition — every player on the server
gets a changed base game so that one questline's climax can land.

**Narrower and better: the danger is not the storm, it is a nexus during a storm.** Currents surge
when a storm passes over; a nexus is where currents intersect; a circle in a storm is therefore a
charged, hazardous place. This keeps everything the broad version bought:

- **Local.** Weather is unmodified everywhere. A hundred blocks away it is just rain.
- **Diegetic, and ours.** Residue is a layer this mod owns entirely, so no engine weather behaviour
  changes and `setForcedWeather` is not needed for the ambient case.
- **It explains the elementals.** `Spirit_Thunder` spawns at circle beacons and is, per shipped item
  text, "born from [storm magic's] lingering traces". A storm surging the nexus is *why* they appear.
- **The inversion works, because we taught the fear.** The questline spends hours establishing that
  a circle in a storm is somewhere you do not linger. The finale then requires standing there.

### Feasibility — the whole chain exists

| Step | Mechanism | Status |
|---|---|---|
| Know a circle is near | `SpawnBeacon extends Entity`, `getSpawnConfigId()` → `"Zone2_Elemental_Circle_Tier2"` | verified |
| Know a storm is overhead | `WeatherResource.getWeatherIndexForEnvironment(int)` + the weather `Tags` map | verified; `WeatherTriggerCondition` pairs exactly these two |
| Hurt the player | `new Damage(Damage.Source, DamageCause, float)` | verified, constructible |
| Make it *felt* | `Damage` meta keys: `IMPACT_PARTICLES`, `IMPACT_SOUND_EFFECT`, `CAMERA_EFFECT`, `KNOCKBACK_COMPONENT`, `HIT_LOCATION` | verified — a strike is one object |
| Author it without code | `DamageEntityEffect` is a Trigger Volume effect | verified |

**And the damage vocabulary has a storm-shaped hole in it.** `DamageCause` is a registrable asset
(`JsonAssetWithMap` + `AssetBuilderCodec`). What ships in `Server/Entity/Damage/`:

```jsonc
// Elemental.json
{ "$Comment": "This damage type exists to facilitate sub types" }

// Fire.json                    // Ice.json
{ "Parent": "Elemental",        { "Parent": "Elemental",
  "Inherits": "Elemental" }       "Inherits": "Elemental" }
```

Hytale built an `Elemental` damage parent, gave it `Fire` and `Ice`, and stopped. **There is no
Lightning.** Adding `Lightning.json` is a three-line asset inheriting an existing parent — the third
instance of the same pattern this audit keeps finding, alongside the missing storm circle and the
missing air essence.

### Prefer `EntityEffect` over raw damage

`asset/type/entityeffect` is a complete, asset-driven status-effect framework — `EntityEffect`,
`ApplicationEffects`, `AbilityEffects`, `ModelOverride`, `OverlapBehavior`, `RemovalBehavior`,
`ActiveEntityEffect`, `LivingEntityEffectSystem`, its own `PacketGenerator` and command. **Exactly
one asset ships against it (`Test_Apply_EntityEffect`)** — the framework is built and effectively
unused, and it is mod-registrable.

A *storm-charged* entity effect is a better primitive than repeated raw damage: it stacks properly
(`OverlapBehavior`), it clears properly (`RemovalBehavior`), it can change the player's appearance
(`ModelOverride`), and it gives the hazard a readable state rather than a series of unexplained
hits. Damage becomes one consequence of the effect rather than the whole mechanic.

### Open knobs — the actual design questions

- Continuous drain, or discrete strikes? Discrete is more Onyxia-shaped and more readable.
- Does it scale with circle tier, storm type, or proximity to the nexus centre?
- Does it hit everyone, or only the attuned? **Recommend everyone** — a danger only you can perceive
  is not one the world taught you, and the whole point is a shared, learned fear.
- Is the real danger the elementals rousing at the nexus, with the effect as atmosphere? That
  version needs no damage cause at all and uses `SpawnBeacon.manualTrigger(...)`.


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
2. **Resolve the residue naming** (§4). It is upstream of Phase 3's reward, Class C materials,
   and the Resonator's identity, and nothing else can be settled around it.
3. **Settle the skip path and storm rarity** (§4). It determines whether Phases 1–2 are real content.
4. **Brainstorm the native-objectives adoption separately** (§2). Largest structural call; do not
   fold it into a lore pass.
5. Only then return to code. The weather-reader fix is small enough to do at any point.

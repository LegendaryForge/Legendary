# Stormseeker — Canonical Questline Document

> **Version:** 4.0 (narrative rewritten from canon)
> **Last Updated:** 2026-08-25
> **Status:** Premise, acts, materials and lore delivery agreed. Implementation partial and largely
> unstarted against this structure — **except Act II, which ships and has been played** — see
> *Implementation Status*.
> **Lineage:** Thunderfury, Blessed Blade of the Windseeker (World of Warcraft) — see *Lineage*.

This document is the **single source of truth** for the Stormseeker questline. Any contradiction
between this document and the codebase resolves in favour of this document.

Design record: `docs/superpowers/specs/2026-08-25-stormseeker-narrative-redesign-design.md`.

---

## What v4.0 changed, and why

Three problems, found by reading the documentation against the game as it ships today.

**Two documents described two different questlines.** `archive/narrative.md` (v1.2) made the
revelation that the world has hidden geometry a **third-act payoff**; this document made it a
**first-act reward**. Both claimed authority; neither was reconciled.

**Narrative coverage was inverted.** Only 4 of 7 phases carried a `### Narrative` section. The
entire opening act had none, described purely mechanically, while the Epilogue — which almost nobody
reaches — was the most fully realised writing in the document.

**Nothing delivered the story.** No dialogue, inscriptions, journals, tooltips or description keys
appeared anywhere in the Stormseeker documents. The prose in `archive/narrative.md` was
**unreachable by the player**. The Core Principle *"No quest UI required"* is right in spirit and had
been applied as a ban on all text — stricter than the tradition it imitates. Hytale's own
"archaeology" method is not text-free; it is **text placed in objects instead of quest logs**.

The vocabulary was invented rather than canon-native. `leyline`, `resonator`, `sigil` and `attune`
have **zero occurrences in `server.lang`**; `storm` has 45, `essence` 17, `spirit` 5.

### Phases → Acts

Six phases became five acts. **This is not a renumbering** — the structure changed.

| v3.1 phase | v4.0 |
|---|---|
| `PHASE_1_THE_MARK` — The Watching Elemental | **Act I — The Mark** (survives nearly intact) |
| `PHASE_2_THE_TREK` — The Trek | **Act II — The Trace** |
| `PHASE_3_THE_WAKING` — Attunement | **Act III — The Listening** (repurposed: literacy, not a granted sight) |
| `PHASE_4_THE_TRIALS` — Dual Sigil Trials | **Act V, beat 1** |
| `PHASE_5_THE_FRAME` — Craft Frame | **Act IV — The Raising** |
| `PHASE_6_THE_FORGING` — Final Encounter | **Act V, beats 2–3** |
| `COMPLETE` — Epilogue | **Epilogue** |

`StormseekerPhase` still carries the v3.1 constant names. **Renaming the enum to acts is outstanding
work**, deliberately not bundled with this documentation change.

---

## Design Philosophy

### Core Principles

- **No neutral verbs.** Every action the player repeats should ask a question the story cares about.
  If a verb asks nothing, it is a chore — condition it on the world, or cut it. **But not every verb
  at full intensity:** ask a real question, then give the player somewhere to put their hands down.
- **No quest UI required.** The world teaches the player through feel, not markers. This does *not*
  mean no text — see *Lore Delivery*.
- **The storm is not an enemy — it is a gate.** Literally true in v4.0: there is a gate.
- **ECS systems are the ONLY place authoritative gameplay logic lives** (per `ecs-principles.md`).
- **NPC Meta must NEVER decide progression, rewards, or entitlement.**
- **Deterministic and testable.** All quest logic must be validatable in harnesses.
- **Single-player compatible; multiplayer encouraged.**
- **No RNG-only legendary progression.** Proofs are earned, never dropped.

**On "No neutral verbs".** This is the *mechanism* behind "the world teaches through feel" — a goal
that previously had no method attached. The test for any mechanic: *what question does this ask, and
what does the answer teach about the world?* The principle is reusable and should govern questline
#2 as much as this one.

| Verb | Normally | Here | The question |
|---|---|---|---|
| Gathering | Click rocks | Storm-timed, perishable, cultivable | *Will you take this place's future?* |
| Travel | Follow the marker | No markers; read residue, terrain, sky | *Can you read where you are?* |
| Waiting | Watch a cooldown | Storms are rare; prepare, observe, position | *Are you ready when it comes?* |
| Exploring | Reveal the map | Crystal density **is** the map | *Can you find what isn't marked?* |
| Building | Place blocks | Raise the Circle where you chose, permanently | *Where will you commit?* |
| Fighting | Kill for drops | The unhoused have nowhere to go | *Will you kill what can't leave?* |
| Crafting | Open menu, click | At your Circle, in a storm, at the gate | *Have you earned the conditions?* |

### Lineage

Stormseeker takes its *shape* from Thunderfury — a long, deterministic, non-RNG legendary weapon
questline whose parts are earned rather than dropped. It takes its *substance* from Orbis.

v3.1 carried a table mapping Thunderfury's components onto Stormseeker's one-for-one. That table is
retired. It quietly steered the design toward "Thunderfury, but Hytale," and its material rows are
now wrong. What is worth keeping from the parallel is the standard: **a legendary questline should
be long, visible to others, impossible to buy, and remembered.**

---

## The Premise

> **The storm has no Circle because the people who built one used it to open a door, and the door
> cost them everything — including the thing that used to answer.**

This reconciles three pieces of shipped canon that otherwise sit unconnected:

- *"The true source of such magic is nowhere to be found"* (`Ingredient_Lightning_Essence`) — it is
  not hidden. It stopped answering.
- *"The elementals born from its lingering traces still wander the lands"* — they wander because
  there is nowhere to gather. They are not free spirits; they are **unhoused**.
- **Storm alone has an essence and a spirit and no Elemental Circle.** Earth, Fire, Frost, Poison and
  Sand all have one. Not an oversight in the world: a wound in it.

It places Orbis's central theme — advanced civilisations, hubris, self-caused downfall — at the
centre rather than in the set dressing.

**The player arc:** get noticed by the residue → learn what was lost → raise a new Circle → make the
storm answer to it → stand where the builders stood, in front of the same door.

**The thematic tension:** the player does exactly what the builders did — same site, same materials,
same ambition. The final question is not "can you win" but "do you understand why they lost."
Stormseeker is proof you were **answered**, not proof you conquered.

**Windrider Valley** is what lies behind the door. The game ships a portal key for *"Fragment: Orbis
— Windrider Valley"*, a place named and unreachable. The fallen civilisation is who reached it.

---

## Act Overview

| Act | Name | Duration | Player knows | First words? |
|---|---|---|---|---|
| I | The Mark | One storm | Nothing — feels noticed | **None** |
| II | The Trace | A session | Someone was here; they failed | Inscriptions |
| III | The Listening | Several sessions | What the materials are | Item flavour |
| IV | The Raising | The long middle | They are rebuilding | Objective text |
| V | The Answer | Climax | Everything | All four channels |
| — | Epilogue | Closure | — | — |

Paced as **hook → mystery → competence → labour → payoff**.

---

## Act I — The Mark

### Narrative

A storm behaves wrongly. Something watches from the edge of it and does not approach. The world
reaches out and touches the player first, before they know there is anything to be part of.

### Behaviour

- During a thunderstorm, an elemental spawns near the player and **hovers**. It does not approach,
  does not attack, does not interact. It drifts to stay visible.
- On first approach (~10 blocks) it **leaves at speed**, scorching the ground beneath its path.
- It travels beyond sight and despawns. The trail persists until the storm ends.
- If ignored: it fades with the storm and returns next storm. No punishment, no lost progress.

### Player Experience

- **Zero words.** No text, no marker, no NPC, no objective entry.
- The player does not know a questline exists. They know they were noticed.
- The absence here is what makes Act II's first inscription land.

### Delivery
None, deliberately.

### Systems Required
Storm detection; elemental spawn near player during storm; hover/drift behaviour; approach
detection; bolt behaviour; scorched-earth trail placement, ground-snapped to natural blocks;
despawn beyond sight; trail cleanup on storm end.

---

## Act II — The Trace

### Narrative

The trail leads somewhere. A hall built for something that never arrived, and at its centre a statue
of a deity with no name, carved by people who were listening to something.

### Behaviour

- Following the trail leads to a ruin — **the first use of the `Temple_Wind` palette anywhere in the
  game**. The set ships complete (animated doors, chandeliers, candles) and is placed nowhere.
- At its centre, the Statue of a Silent Deity (`Furniture_Temple_Wind_Statue_Gaia`, which likewise
  appears in no shipped structure).
- The ruin is **not** quest-gated. Any player may find and explore it. What it *says* is what
  changes with progress.

### Player Experience

- The story speaks for the first time, and only through **inscriptions** — the builders' own record,
  never a quest-giver.
- What the player learns: there was going to be a Circle here, and something happened.

### Delivery
Structure inscriptions. Register:

> *"We raised five. The sixth would not stand."*
>
> *"The others gather where we set the stones. This one would not be housed."*
>
> *"It answered once. We asked again."*
>
> *"We carved it listening. It has not spoken since."* — at the statue

The first is literally true in the shipped game: Earth, Fire, Frost, Poison, Sand.

### Systems Required
Trail following; ruin placement in worldgen; readable inscriptions; discovery state.

---

## Act III — The Listening

### Narrative

You cannot rebuild what you do not understand. The storm has been saying the same thing all along.

### Behaviour

- The player learns to read **where residue gathers** — density, currents, the signs that presage a
  storm.
- The storm materials they have been carrying all along acquire meaning. Stormsilk, Storm Hide,
  Essence of Lightning and Storm Thistle all ship **ungated**, so most players will have handled
  them before ever meeting this questline.
- `Spirit_Thunder` matters here: it is the **only source of Essence of Lightning in the game**. The
  builders *harvested* the unhoused. The player needs another way to be given what they took.

**This act gates residue cores** (see *Materials*, Class C). Learning to read residue is what makes
a core visible, so the one irreversible act in the questline becomes available only to a player who
has already read the builders' record in Act II. Capability and cautionary tale arrive in that order
deliberately.

### Player Experience

- **Nothing is granted.** There is no sight toggle, no unlocked ability, no UI.
- The signs were always present; the player learns to see them. A veteran calls a storm from the way
  the thistle leans while a newcomer beside them sees weather.
- This replaces v3.1's "leyline sight." A granted toggle makes the *character* more perceptive. This
  makes the *player* more perceptive, which is the only version that survives being played twice.

### Delivery
Item flavour text, on our items only — see the never-override rule in *Lore Delivery*.

### Systems Required
Residue current representation; crystal density as a readable signal; pre-storm signs (crystal
luminance, thistle lean, elemental drift); core visibility gated on act progress.

---

## Act IV — The Raising

### Narrative

Five circles stand because someone built them and they endured. The sixth has to be made.

### Behaviour

- The player finds a site **the sky can see** — high, exposed, storm-prone — and far enough from any
  other Circle (see *Siting and Multiplayer*).
- The Circle is raised **tier by tier**, following the 01→07 convention the game already ships for
  Earth (7 tiers) and Frost (5).
- Some tiers may only be raised **during a storm**. This turns storm rarity from a problem into the
  pacing governor: there is work to do between storms, and storms become anticipated events rather
  than weather. **Not every tier should require one**, or it becomes a wait.
- Each **Keystone** earned is a tier. The token does not represent progress; it becomes the monument.

### Player Experience

- The signature visual: a monument assembling across sessions.
- Accomplishment accrues visibly, permanently, and in public. Other players walk past it.
- Site choice is permanent, which is what makes it a decision.

### Delivery
Objective titles and descriptions, in-fiction:

| Instead of | Write |
|---|---|
| Reach the location | *Follow what the storm burned* |
| Gather 3 Storm Thistle | *Take only what the storm has already given* |
| Craft the frame | *Shape something worth answering* |

### Systems Required
Site qualification (terrain: elevation, exposure, storm frequency); minimum-spacing check; tiered
prefab placement; storm-gated construction steps; Keystone → tier binding; persistence of Circle
state and ownership.

---

## Act V — The Answer

### Narrative

The Circle stands, and stays silent. Something has to call it.

### Beat 1 — The proving

Whatever the trials become, their **dramatic job is fixed**: demonstrate that the player can be
*answered* without being *consumed* — the exact thing the builders failed at. Each proving grants a
Keystone.

> The trials' mechanics are under active development and are **deliberately unconstrained by this
> document** beyond that function. The current implementations are scaffolding; see *Implementation
> Status*.

### Beat 2 — The answer

The storm responds. Elementals gather at the beacon for the first time in the world's history, and
the Circle joins the family. **Essence of Thunder** is given here, once — by a `Spirit_Thunder` that
came because there was finally somewhere to gather.

Residue begins collecting at the Circle. That is not a farming convenience: it is proof the Circle is
working, delivered with no UI, no number and no text.

### Beat 3 — The door

An answered Circle can finally read what the builders did — the way they opened, still there.
Stormseeker is forged in that moment, at the gate, during a storm. **The forging is the
confrontation**, not a crafting step that follows one.

### Player Experience

The player has done what the builders did, with the difference that everything asked of them along
the way was about restraint. The weapon is the proof they were answered.

### Delivery
All four channels converge. The single permitted NPC reaction lands here — someone who has seen the
sky do something it has not done in living memory. One reaction, late, expression only.

### Systems Required
Proving orchestration; spawn beacon activation; Essence of Thunder grant (once per player, durable
edge); gate reveal; forging encounter; storm-gated finale.

---

## Epilogue

### Narrative

Stormseeker does not end the storm — it changes the relationship. The world returns to its rhythms
but never becomes fully ordinary again: the edge of wind before rainfall, the distant roll of
thunder, the sense that calm is an agreement rather than a guarantee.

**Stormseeker is not the end of the story. It is the key that makes other stories possible.**

*(Register preserved from `archive/narrative.md`, which was right about this.)*

### Player Experience

- No objectives. This is closure.
- The Circle remains, produces, and is walked past by others.
- Storms remain storms. No permanent godmode.

### Systems Required
Post-completion ownership flags; ambient recognition that does not trivialise gameplay.

---

## Materials

> Generic materials build legendaries. Storm materials define Stormseeker. Crystals reveal space.
> Keystones become the monument.

### Class A — Generic Legendary Materials
Shared backbone across Legendary lines. World-generated, vanilla-plus rare, no storm or questline
dependency. Prevents every Legendary line reinventing base materials. Scope is `:core`.

### Class B — Skyglass
Fulgurite. Lightning striking exposed stone fuses it to glass. It exists **because** the storm
happened, on peaks and ridges, and **it does not keep** — harvest it in the storm that made it or
lose it.

*Asks: how much risk will you take?* Gathering means standing on an exposed peak in a thunderstorm
while lightning is still striking. `DamageCause` is an authorable asset type, so a real Lightning
cause is available to a pack, with camera shake, particles and sound behind it.

### Class C — Residue Crystal
Forms where residue gathers, density scaling with proximity to a current. Its purpose — *crystals
reveal space* — is literal: **crystal density is how the player finds a Circle site.** The material
is the map.

**Harvesting model — depletion, regrowth, and one irreversible choice.**

- **Clusters are finite and regrow.** Anyone may harvest; a stripped site recovers slowly from the
  current feeding it. A stripped shared site is a delay, never a blocked questline.
- **Each site also has a core.** Taking it **permanently ends that site**, and lets the player
  transplant production to their own Circle, where crystals then form renewably.
- **Cores are visible only from Act III onward.** A casual player cannot see one and therefore
  cannot destroy a site, deliberately or otherwise.

That gate is what makes this safe on a shared server and what gives it teeth: the irreversible option
is offered only to someone who has already read *"It answered once. We asked again."*

### Class D — Keystones
Deterministic proof tokens. Granted by systems only — never dropped, never NPC-decided, never RNG.
**Each Keystone is a tier of the Circle.** State truth, not loot.

### Class E — Essence of Thunder
Cannot be mined, killed for, or crafted. **Given**, once, when the Circle answers.

Lightning is the strike; thunder is the answer. The two essences carry the theme with no exposition:
**one is taken, one is given, and both go in the sword.**

### Stormseeker is forged from

| Class | Material | How obtained |
|---|---|---|
| A | Generic legendary backbone | Ordinary rare world materials |
| B | **Skyglass** | Peaks, during storms, perishable |
| C | **Residue Crystal** | Where currents settle — also how the site was found |
| — | **Essence of Lightning** | Taken. The shipped material; the builders' way |
| E | **Essence of Thunder** | Given. Only at an answered Circle |

Forged at the player's own Circle, in a storm, at the gate.

**Nothing here duplicates the shipped chain.** Storm Hide, Stormsilk and Storm Thistle remain the
mundane harvest anyone can do. Ours sit above them, and the fact that ordinary players already handle
storm materials is what makes the escalation legible.

Names are first-pass, deliberately plain-compound to match Hytale's convention (`Storm_Hide`,
`Ingredient_Bolt_Stormsilk`).

### Hard rules

- **Storms gate access, not progress state.**
- **No quest-state-dependent world spawning.** The world is the same world whether or not the player
  is on the questline — which is what makes finding it feel like discovery.
- No NPC-gated materials.
- No RNG-only legendary progression.
- No Stormseeker materials leaking into unrelated mods.

### Item loss recovery
If the frame is lost, recraft is permitted when the player holds no valid owner-bound frame.
Recraft requires full materials. One active frame per owner. Logic: `StormseekerRecraftRules`.

---

## Lore Delivery

Four channels, each with a distinct job. This replaces the previous atmosphere-only model, which
delivered nothing.

### Governing rule — never override shipped item text

Overriding base-game item descriptions is technically possible (proven 2026-08-25, see
`docs/integration/hytale-asset-packs.md`) and is **prohibited here.** It changes the game for every
player on a server including those not on the questline, and here it would destroy the effect,
because the shipped text is already load-bearing:

> *"Raging storms have proved attractive to many creatures over the centuries. Though the true source
> of such magic is nowhere to be found, the elementals born from its lingering traces still wander
> the lands."* — `Ingredient_Lightning_Essence`

That is **already Act III's revelation.** Every player who has picked one up has been carrying the
answer unread. **Recontextualise, never rewrite.** New text goes only on our own items.

### The four channels

| Channel | Job | Opens |
|---|---|---|
| Structure inscriptions | The builders speak, in their own voice | Act II |
| Item flavour text | Materials acquire meaning (our items only) | Act III |
| Objective titles/descriptions | In-fiction, never instructional | Act IV |
| NPC dialogue | Expression only, never entitlement — one reaction | Act V |

**Every channel opens later than expected.** Act I has no words. The world speaks only after it has
the player's attention. That inversion is what "no quest UI" was reaching for and could not achieve,
having no channels at all.

The questline's objective line is **"The Sixth Circle."**

---

## Siting and Multiplayer

**The player raises their own Circle.** There is no Storm Circle in the world — that is canon — so
the player does not claim a scarce landmark. They build one at a site qualifying on **terrain**
(high, exposed, storm-prone), subject to minimum spacing from other Circles.

Rejected alternatives, recorded so they are not re-proposed:

- **Instanced Circle** — available (Hytale ships `Server/Instances/` with `Dungeons`, `Portals`,
  `Persistent`, `ShortLived`, `Regions`). Rejected: nobody else would ever see the monument, which
  discards the entire payoff.
- **One shared Circle per server** — no contention, strong one-time event, but every player after the
  first gets a materially lesser questline.
- **Rare fixed sites** — better art control, reintroduces contention at scale.

Terrain-qualified siting **scales the right way**: a bigger world has more sites automatically, and on
a large server storm circles appear across the landscape over months, reading as the element
returning rather than as a queue. The failure mode of scale becomes a feature.

---

## Implementation Status

> **Dated 2026-08-25. This section rots faster than the rest of the document.** The previous version
> was dated 2026-02-10 and was wrong in at least two ways by the time it was read: it claimed the
> trials were a "full pipeline" and that progress was in-memory only.

**Exists and is live:** the plugin loads in a real Hytale server; `StormseekerTickSystem` calls
`StormseekerWiring.tick(host)` each tick; player connect/disconnect and position tracking work;
`PropertiesProgressStore` persists progress to disk, with unreadable saves quarantined rather than
destroyed; gate registration; item identity framework; extensive trial test coverage.

**Act II ships as native assets, and was verified by playing it.** `mod/hytale` is an asset pack
(`"IncludesAssetPack": true`) carrying `ObjectiveLine_Stormseeker_TheSixthCircle`, two objectives
(`Objective_Stormseeker_TheChamber`, `Objective_Stormseeker_TheTrace`), one reach-location marker,
four inscription blocks plus a shared base asset, and one `server.lang`. Reading the first
inscription starts the line; reading the builders' record completes the act. Confirmed on
2026-08-25 on a dedicated server with a connected client: each of the four inscriptions printed
**its own** message; three of the four (`Housed`, `Asked`, `Statue_Silent`) are also `UseBlock`
tasks tracked by `Objective_Stormseeker_TheTrace` and each ticked **its own** task — the fourth
(`Five`) starts the objective line rather than ticking a task of its own — and every title and
task line rendered as prose rather than as a raw localisation key.

One piece of Java was unavoidable. `StartObjective` is item-only — fired from a block it NPEs on
the absent held item and **disconnects the player** — so starting the line from an inscription
needed a custom `StormseekerStartLine` interaction registered through `Interaction.CODEC`. It is
about forty lines, written once, and every later questline that starts from a block reuses it.
Mechanism and evidence: `docs/integration/hytale-asset-packs.md` §7c.

**Line-level completion does not currently record.** After an honest play-through and a clean
disconnect, both objectives persist `TimesCompleted: 1` but the **line** persists
`TimesCompleted: 0`. Unresolved — traced to a caller-side gate, cause not found. Until it is,
nothing here may gate "this player finished the act" on the line's counter; use the per-objective
counts inside `ObjectiveLineHistory.Objectives[]`, which are recorded correctly. Detail in
`docs/integration/hytale-asset-packs.md` §8.

**The ruin is not generated.** The blocks are placed by hand and the chamber marker is sited in
game with `/objective reachLocationMarker add ReachLocationMarker_Stormseeker_RuinChamber`. A
`Temple_Wind` prefab in worldgen is separate work, still blocked on the open Zone-2 anchoring
question — and on whether a mod pack can contribute worldgen structures at all, which **remains
unproven**. Act II deliberately does not depend on the answer.

**Act I's trail is still the specced route in, and still does not exist.** Act II is reachable
without it because the ruin is deliberately not quest-gated; when Act I lands, its trail becomes a
second route to the same place rather than a prerequisite for it.

**Exists but is scaffolding, not the designed mechanic:**
- `FlowingTrialEvaluator` scores movement against **its own previous direction** — self-coherence.
  There is no storm gradient and no convergence point; its own comment reads
  `// --- Completion: consistency-based, no location ---`.
- `AnchoredTrialSession` is `REQUIRED_STATIONARY_TICKS = 40` — hold still. Its own comment calls it
  *"intentionally simple scaffolding that can be replaced by richer mechanics later."*

**Exists but is dormant:** `StormseekerAttunementService` implements the 5s/15s/5s ritual correctly,
but `StormseekerWiring.registerListeners` has no non-test callers, so it is never constructed at
runtime. Same for `StormseekerTrekSystem`.

**Does not exist:** everything in Acts I, III and IV as described here. Elemental spawn and
behaviour, trail placement, residue currents and crystals, Skyglass, site qualification, tiered
Circle raising, Keystones, Essence of Thunder, the gate, the forging encounter. *(This list read
"Acts I–IV" and included the ruin and inscriptions until 2026-08-25; Act II now ships — see above.
What is still missing from Act II is the **generated** ruin, not the content in it.)*

**Outstanding rename:** `StormseekerPhase` still carries v3.1 constant names, which no longer match
this structure — and following the 2026-08-25 native-objectives decision, whether the enum survives
at all is open, since objective and line history now own progress.

**Done for the native spine.** This section previously read *"`mod/hytale`'s `manifest.json` still
reads `"IncludesAssetPack": false`, and the module has no `Server/Objective/...` asset tree."*
**Both halves are now false** — the flag is `true` and the tree exists, shipped with Act II. What
remains open on the native spine is the line-completion defect above and worldgen, not the pack
itself.

---

## Canonical Integration Pattern

**The content spine is native.** Decided 2026-08-25: questline structure, objective chaining, task
tracking, the player-facing objective text of the delivery model, and per-player progress and
history are **Hytale asset-driven objectives**, shipped as JSON from `mod/hytale`'s own asset pack.
Custom mechanics register into Hytale through the public codec seams. Record and reasoning:
`docs/architecture/native-objectives-migration-cost.md`.

Two consequences for this document. `StormseekerPhase` is **no longer the progress model** —
objective and line history are — so the enum's future is open, not merely its naming. And
`core`'s participation rules (access levels, spectators, roles, visibility) are **unaffected**:
Hytale's objectives track one player's progress and ship nothing that decides who may join a shared
event. The two layers stack.

Engine integration for everything that is *not* the objective spine occurs via:

```java
StormseekerWiring.tick(host)
```

- Act loops must NOT be invoked directly by engine code.
- Trial participation is host-controlled.
- `resetForTesting()` exists for JVM test isolation only.
- Durable milestones are emitted on edges (at most once per player + milestone).
- Presentation hooks are default no-ops.

---

## Open Questions

- **Zone anchoring.** Thunder weather exists **only in Zone 2** of 87 shipped weather definitions,
  which would anchor the ruin and probably the Circle there. A real constraint, worth deciding
  deliberately rather than inheriting.
- **Tier count** for the Storm Circle. Earth ships 7, Frost 5.
- **Minimum spacing** between player Circles.
- **Regrowth rate** for wild crystal clusters — fast enough that a stripped shared site is a delay,
  slow enough that cultivating at a Circle is worth doing.
- **Class A materials** — named, not specified; should be designed for the elemental family rather
  than for Stormseeker alone.
- **The single NPC reaction** — who, and where.
- **Trial mechanics** — under active development, unconstrained here beyond their dramatic function.

---

## Document History

- **v4.0 (2026-08-25):** Narrative rewritten from Orbis canon. New premise — the storm has no Circle
  because the builders opened a door with it. Six phases became five acts (not a renumbering; the
  structure changed). Materials keep February's taxonomy with leyline crystals re-founded on residue,
  sigils becoming Keystones that are tiers of the monument, and a new class the premise requires,
  Essence of Thunder. Four lore-delivery channels adopted, replacing an atmosphere-only model that
  delivered nothing. `No neutral verbs` added to Core Principles. The Thunderfury mapping table
  retired to a lineage note. Invented vocabulary (`leyline`, `resonator`, `sigil`, `attune` — all 0
  occurrences in `server.lang`) dropped. Design record:
  `docs/superpowers/specs/2026-08-25-stormseeker-narrative-redesign-design.md`.

- **v3.1 (2026-08-24):** Phases renumbered — six numbered phases bracketed by `UNTOUCHED` and
  `COMPLETE`, removing the fractional Phase 1.5 and separating "has not started" from "is in the
  first phase". The code was renumbered in the same change. No design content was altered.

- **v1.0 (2026-02-04):** Original narrative, design, and quest-phases documents (now superseded).
- **v2.0 (2026-02-10):** Complete rewrite. Corrected phase structure, clarified sigil placement
  (Phase 2 only), added Thunderfury parallels, materials taxonomy, full code audit, documented open
  questions, consolidated into single source of truth.
- **v3.0 (2026-02-16):** Phase 0/1/1.5 redesign. Removed movement restriction mechanic (not exposed
  in Hytale API). Phase 0 is now a watching elemental that bolts on approach, leaving a scorched
  earth trail. Phase 1 is self-directed trail following to the Resonator. Phase 1.5 is a 30-second
  attunement ritual at the Resonator's plates. Added Resonator independent storm behavior, skip path,
  pre-flight ocean validation, Leave No Trace cleanup, structure avoidance.

Entries above v4.0 are deliberately left under their original numbering — they record what was
decided at the time, and renumbering them would falsify the record.

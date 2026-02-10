# Stormseeker — Canonical Questline Document

> **Version:** 2.0 (Rebuilt from scratch)
> **Last Updated:** 2026-02-10
> **Status:** Narrative locked. Materials locked. Mechanics in progress. Implementation partial.
> **Inspiration:** Thunderfury, Blessed Blade of the Windseeker (World of Warcraft)

This document is the **single source of truth** for the Stormseeker questline. It replaces all
previous narrative.md, design.md, and quest-phases.md documents. Any contradiction between this
document and the codebase should be resolved in favor of this document.

---

## Design Philosophy

Stormseeker is a legendary weapon questline inspired by World of Warcraft's Thunderfury.

### Thunderfury Parallel Map

| Thunderfury | Stormseeker | Notes |
|---|---|---|
| Left Binding of the Windseeker | Sigil A (Flowing Trial) | Deterministic trial, not RNG drop |
| Right Binding of the Windseeker | Sigil B (Anchored Trial) | Deterministic trial, not RNG drop |
| Elementium Ore (MC trash mobs) | Storm-bound elemental ore | Visible/harvestable during storms only |
| Arcane Crystals + Arcanite Bars | Leyline-bound crystals | Spawn within leyline influence radius |
| Fiery Core / Lava Core (MC bosses) | Generic legendary materials | Vanilla-plus rare, shared across mods |
| Enchanted Elementium Bars | Stormseeker Frame (Phase 3) | Crafted composite of all material classes |
| Summoning + defeating Thunderaan | Final Encounter (Phase 4) | Temper/energize frame into finished sword |
| Thunderfury | Stormseeker | The finished weapon |

### Core Principles

- **No quest UI required.** The world teaches the player through feel, not markers.
- **ECS systems are the ONLY place authoritative gameplay logic lives.** (per ecs-principles.md)
- **NPC Meta must NEVER decide progression, rewards, or entitlement.**
- **Deterministic and testable.** All quest logic must be validatable in dogfood harnesses.
- **Single-player compatible; multiplayer encouraged.** Late joiners may spectate but not affect outcomes.
- **The storm is not an enemy — it is a gate.**
- **No RNG-only legendary progression.** Sigils are earned, never dropped.

---

## Phase Overview

| Phase | Name | Purpose | Player Knows? |
|---|---|---|---|
| 0 | Storm Unease | Elemental invitation; first contact | No — feels like a strange storm event |
| 1 | Storm Trek | Follow the elemental toward convergence structure | Partially — player senses direction |
| 1.5 | Attunement | Perceptual unlock; leyline sight granted | Yes — world visibly changes |
| 2 | Dual Sigil Trials | Prove competence; earn Sigil A and Sigil B | Yes — explicit trials |
| 3 | Craft Frame | Construct the Stormseeker frame | Yes — gathering and crafting |
| 4 | Final Encounter | Boss fight + temper/energize into finished sword | Yes — climactic encounter |
| 5 | Epilogue | Aftermath and world acknowledgement | Yes — closure |

---

## Phase 0 — Storm Unease

### Narrative

The player is going about their business when a storm rolls in. During the storm, an air/electrical
elemental manifests near the player. This elemental is not hostile — it is a storm expression,
something that would occur whether the player was watching or not.

The elemental begins casting a dust-devil / mini-cyclone effect on the player. This effect subtly
restricts the player's movement based on direction:

- **Moving toward the elemental:** No restriction. Movement feels normal.
- **Moving away from the elemental:** Increasing resistance (degree TBD). Movement feels heavy.

The elemental visually and audibly conveys effort — when the player resists (moves away), the
elemental's particle effects intensify and its sound grows strained. When the player yields
(moves toward), the effects calm.

**The message is physical, not textual: the storm noticed you, and it's inviting you somewhere.**

### Player Experience

- No journal entry. No markers. No quest popup.
- The player feels something unusual happening during a storm.
- The movement restriction is a tactile hint, not a wall — the player CAN break away.
- If the player breaks away, the elemental dissipates. The opt-out is clean.

### Transition to Phase 1

If the player moves toward the elemental and continues following, the elemental begins moving
toward the nearest storm convergence structure. This movement marks the beginning of Phase 1.

### Open Design Questions

- **When does the elemental first appear?** (First storm? After several storms? Random chance
  after baseline playtime?)
- **If the player opts out, does the elemental return?** (Next storm? Increasing intervals?
  Fixed attempts?)
- **What is the maximum movement restriction?** (Percentage slowdown? Directional only?)

### Systems Required

- Storm weather detection (when is a storm active?)
- Elemental spawn logic (proximity to player during storm)
- Movement restriction system (directional, gradient-based)
- Elemental visual/audio feedback (effort scaling)
- Player approach/retreat detection
- Opt-out detection (player breaks away from restriction radius)

---

## Phase 1 — Storm Trek

### Narrative

The elemental moves through the world toward a storm convergence structure. Initially it guides
the direction, then **fades away**, leaving the player to find their own way using storm signals.

The trek is not an escort quest. Storm signals (wind intensity, thunder rhythm, lightning
direction) strengthen when the player is aligned with the correct path and weaken when they
diverge. If the player veers off course, the dust-devil resistance mechanic from Phase 0 returns
to nudge them back on track. The opt-out remains available — the player can break away at any time.

Along the way, the world itself reinforces the journey:

- Elevation and exposure affect signal strength.
- Storms have "centers" that the trek passes through or near.
- Terrain can amplify or dampen the storm's coherence.

### Player Experience

- The player is learning the storm's directional language through movement.
- There's no minimap arrow — just the feeling that "this direction is right."
- The trek ends at a **storm convergence structure**: a storm-scarred formation, ruin-adjacent
  anchor, or similar landmark where the storm gathers with unusual intensity.
- The convergence structure is NOT the Ancient Forge. It is NOT a crafting site. It is the place
  where the storm is loudest.

### Transition to Phase 1.5

Phase 1 ends when the player reaches a storm convergence structure during a sufficiently
coherent storm state. Nothing "completes" — the place just feels like the storm's focal point.

### Open Design Questions

- **How far is the trek?** (Minimum distance? Biome-spanning?)
- **What happens if the player loses the elemental before it fades?** (Storm signals guide
  without it? It respawns ahead?)
- **Can the trek span multiple storms / play sessions?** (Progress saved? Must be continuous?)
- **Anti-escort rule:** Nothing "waits" for the player. Signals strengthen/weaken naturally.
  How strictly do we enforce this?

### Systems Required

- Elemental pathfinding toward convergence structure (initial guidance)
- Elemental fade-out logic (after initial direction is established)
- Storm coherence evaluation (player alignment with intended direction)
- Dust-devil resistance mechanic (reused from Phase 0 for off-course correction)
- Signal feedback system (wind/thunder/lightning intensity scaling)
- Convergence structure detection (player arrived at destination)
- Trek progress tracking (if multi-session is supported)

---

## Phase 1.5 — Attunement

### Narrative

At the convergence structure, during the storm, something shifts. The storm recognizes the player
as capable of perceiving deeper structure. This is attunement — a permanent perceptual change.

The convergence structure itself doesn't transform. The player's perception does.

### Player Experience

- The player gains **leyline sight**: a toggled perception ability.
- Leylines always existed in the world. Now the player can see them on demand.
- Leylines are NOT quest arrows. They are world geometry — influence flows, energy paths beneath
  the terrain.
- Outside storms, cues may linger faintly, but storms remain the primary "loud" state.
- The player also gains persistent **storm literacy**: a subtle ongoing sensitivity to storm
  direction and intensity.

### What the Player Gains

- **Leyline-vision toggle:** Persistent ability (survives logout). Toggleable on/off.
- **Storm literacy:** Passive. Subtle environmental awareness even outside storms.

### Transition to Phase 2

Attunement is the pivot. Phase 2 becomes available immediately after.

### Systems Required

- Attunement event trigger (player at convergence structure during storm)
- Player capability component: "can perceive leylines"
- Leyline-vision toggle ability (persistent across sessions)
- Attunement milestone emission (durable edge, at most once per player)

---

## Phase 2 — Dual Sigil Trials

### Narrative

Awareness isn't enough. The player must prove restraint and competence under storm pressure.
Two trials test opposing aspects of storm mastery.

### Thunderfury Parallel

In WoW, Thunderfury requires both the Left Binding and the Right Binding of the Windseeker —
two halves of a whole, each dropped from a different boss. Stormseeker's two sigils serve the
same role: complementary proofs that together authorize the next phase. The critical difference:
sigils are earned through deterministic trials, not random drops.

### Trial I: Flowing Trial → Sigil A (`stormseeker:sigil_flowing`)

**Core invariant:** Storm mastery is proven by continuous alignment while in motion.

The storm defines an implicit directional flow field. The player is not finding a path — they
are staying aligned with a moving, shifting force. Mastery is demonstrated through ongoing
correctness, not arrival speed.

**Mechanical structure:**
- **No anchor.** No persistent world-fixed structure. The trial is purely player-centric.
- **Continuous evaluation:** ECS systems evaluate player movement vector against storm gradient
  each tick. Evaluation is host-authoritative.
- **Diegetic feedback (not UI):** Correct alignment causes storm effects to cohere and intensify
  (audio, visual density, motion feel). Misalignment causes effects to weaken and destabilize.
  Feedback is gradient-based, not binary.

**Failure semantics:**
- Failure is directional, not temporal — there is no timer and no accumulated progress.
- The storm simply rejects sustained misalignment.
- On failure: trial effects dissipate, attempt ends cleanly, no permanent penalty.

**Completion condition:**
- Player maintains sufficient alignment and reaches a valid convergence point.
- On success: Flowing Sigil granted, deterministic milestone emitted.

**Implementation:** Full pipeline exists — detection, evaluation, alignment scoring, hint
emission, sigil grant, completion.

### Trial II: Anchored Trial → Sigil B (`stormseeker:sigil_anchored`)

**Core invariant:** Storm mastery is proven by sustained stabilization of a fixed locus.

Where the Flowing Trial asks "Can you move with the storm?", the Anchored Trial asks "Can you
keep the storm contained?" This is not reflex-based and not a DPS check. It is a test of
control, awareness, and persistence under pressure.

**Mechanical structure:**
- **Persistent anchor entity.** A fixed world entity acts as the anchor. Trial state is
  world-centric, not player-centric. The anchor exists independently of the player.
- **Host-authoritative runtime loop:** ECS systems evaluate each tick — anchor stability, player
  interaction validity, environmental constraints (e.g., storm presence).
- **Player interaction:** The player must repeatedly reinforce or stabilize the anchor.
  Interactions are discrete but evaluated over time.

**Failure semantics:**
- Failure is progressive, not binary.
- Missed or incorrect interaction → instability increases.
- Continued neglect → anchor destabilizes. Full instability → anchor collapses.
- One mistake does NOT immediately fail the trial. Failure is readable and recoverable until
  collapse.
- On collapse: trial attempt ends, world resets only what is necessary, player is not
  permanently blocked.

**Completion condition:**
- Anchor remains stable for the full required duration with all host-side invariants holding.
- On success: Anchored Sigil granted, deterministic milestone emitted.

**Implementation:** Full pipeline exists — session tracking, stationary streak evaluation,
sigil grant, leave/cleanup.

### Design Rules

- Trials are **independent** — can be completed in any order (A then B, or B then A).
- Trials grant **binary proofs** (sigils) — you have it or you don't.
- Trials are **deterministic and testable** — no RNG in pass/fail.
- Trials are **spectator-safe** — late joiners can watch but not affect outcome.
- Failure resets local trial state but does NOT delete global progress.
- When both sigils are obtained: emit **DUAL_SIGILS_GRANTED** milestone edge.

### Trial Sites

- Two distinct trial sites in the world (separate from convergence structures).
- Trial logic is authoritative and server-side.

### Transition to Phase 3

Phase 2 ends once both sigils are obtained. This is the first truly "gated" step — but the gate
is enforced by systems, not NPC entitlement.

### Phase 2 Completion (Precise Statement)

Phase 2 is complete only when Flowing Sigil is granted AND Anchored Sigil is granted. Nothing
else. No NPC logic, no narrative authority, no implied attunement milestone. Downstream
unlocking is handled entirely by ECS systems based on sigil presence.

### Systems Required

- Flowing trial orchestration ✅ (implemented)
- Anchored trial orchestration ✅ (implemented)
- Sigil A / Sigil B grant logic ✅ (implemented)
- DUAL_SIGILS_GRANTED milestone emission ✅ (implemented)
- Trial participation management ✅ (implemented)
- Spectator support for late joiners (TBD)

---

## Phase 3 — Craft Frame

### Narrative

The player is now trusted enough to construct the Stormseeker frame — the vessel that can survive
what comes next. This is construction, not tempering. The frame is inert until Phase 4.

### Materials Required

Phase 3 crafting draws from three of the four material classes:

#### A. Generic Legendary Materials
- Shared backbone across all Legendary mods.
- Vanilla-plus rare, world-generated, broadly obtainable.
- Not tied to storms, leylines, or Stormseeker specifically.
- Purpose: prevents every Legendary line from reinventing the same base materials.

#### B. Storm-Bound Elemental Materials
- Stormseeker's elemental identity (storm / air / lightning).
- Storm-charged ores and elemental crafting reagents.
- **Only visible or harvestable during storms.**
- Location-based: peaks, exposed terrain, high elevations.
- NOT created by leylines. Leylines may amplify yield but never create them.
- **Hard rule: storms gate access, not progress state.**

#### C. Leyline-Bound Crystals
- Spatial discovery + progression texture.
- Always exist in the world. Always collectible.
- Spawn exclusively within leyline influence radius.
- Density scales with proximity to the leyline core.
- **Visible even before attunement** (leylines themselves are not visible until unlocked).

### Crafting Process

- Takes place at a dedicated crafting locus (the Ancient Forge in broader vision).
- May be multi-step, storm-timed, and/or location-relevant.
- The output is explicitly **the frame** (`stormseeker:frame_incomplete`) — not the finished sword.
- Validation gates: must have both sigils + material requirements.

### Item Loss Recovery

If the player loses the frame (death, destruction, etc.):
- Recraft is allowed if the player does not currently possess a valid owner-bound frame.
- Recraft requires full materials again.
- Uniqueness enforced: one active frame per owner.
- Logic: `StormseekerRecraftRules.canRecraftFrame()`

### Transition to Phase 4

Phase 3 ends when the player possesses the Stormseeker frame.

### Open Design Questions

- **Where is the crafting locus?** (Fixed world location? Player-discovered?)
- **How many crafting steps?** (Single assembly? Multi-stage?)
- **Storm timing requirement?** (Must craft during storm? Storm enhances but not required?)
- **Material quantities?** (TBD — intentionally deferred)
- **Exact naming of storm ores?** (TBD — intentionally deferred)

### Systems Required

- Frame assembly state machine (authoritative) — not yet implemented
- Material validation — not yet implemented
- Sigil requirement check — gate exists (`StormseekerQuestSteps.PHASE_3_INCOMPLETE_FORM`)
- Storm state check (if storm-timed) — not yet implemented
- Frame item creation — item ID exists (`stormseeker:frame_incomplete`)

---

## Phase 4 — Final Encounter

### Narrative

The storm's final refusal: "Prove this frame deserves to carry the charge." This is the
climactic encounter — a boss fight where the sword is tempered and energized. The energizing is
not a separate crafting action; it is bound to the encounter's progression.

### Thunderfury Parallel

In WoW, after gathering all materials and the Bindings, the player summons Thunderaan, Prince
of Air, and must defeat him to claim Thunderfury. Stormseeker's final encounter serves the same
narrative role — the storm itself (or its embodiment) tests the wielder one last time.

### Encounter Design

- Authoritative encounter orchestration with phases/waves/mechanics.
- Deterministic hooks consistent with overall determinism preference.
- Energizing/tempering progression tied to encounter milestones.
- The frame transforms into Stormseeker (`stormseeker:stormseeker`) upon encounter completion.

### Item Loss Recovery

If the player loses the finished Stormseeker:
- Recraft is allowed if the player does not currently possess a valid owner-bound Stormseeker.
- Recraft requires full frame materials + final ritual materials.
- Uniqueness enforced: one active Stormseeker per owner.
- Logic: `StormseekerRecraftRules.canRecraftStormseeker()`

### Transition to Phase 5

Phase 4 ends when the energizing completes and the weapon becomes Stormseeker (finished state).

### Open Design Questions

- **What is the encounter?** (Storm elemental boss? Environmental trial? Multi-phase?)
- **Solo or group?** (Soloable with group option? Mandatory group?)
- **Failure semantics?** (Full reset? Checkpoint?)
- **Energizing pacing?** (Tied to encounter phases? Gradual throughout?)

### Systems Required

- Encounter orchestration system — not yet implemented
- Energizing progression state machine — not yet implemented
- Frame → Stormseeker transformation — item IDs exist
- Encounter milestone emission — not yet implemented
- Failure/retry handling — not yet implemented

---

## Phase 5 — Epilogue

### Narrative

Stormseeker doesn't end the storm — it changes the relationship. The world returns to its usual
rhythms, but it never becomes fully ordinary again. The wielder feels the difference in subtle
ways: the edge of wind before rainfall, the distant roll of thunder beyond the horizon, the
sense that calm is an agreement rather than a guarantee.

**Stormseeker is not the end of the story. It is the key that makes other stories possible.**

### Player Experience

- No objectives. This is closure.
- Discoverable lore beats.
- Environmental quieting or "aftershock" tone shift.
- Optional NPC commentary as expression only (never entitlement/progression authority).
- The player may perceive storms differently, but storms remain storms — no permanent "godmode."

### Systems Required

- Post-completion flags for Stormseeker ownership — not yet implemented
- Optional ambient modifiers / cosmetic recognitions (must not trivialize gameplay)

---

## Material Taxonomy

### One-Line Mental Model

> Generic materials build legendaries.
> Storm materials define Stormseeker.
> Crystals reveal space.
> Sigils prove progress.

### Class A — Generic Legendary Materials

- **Scope:** Shared across all Legendary mods (LegendaryCore)
- **Generation:** World-generated, vanilla-plus rare, broadly obtainable
- **Dependencies:** None (no storm, no leyline, no questline)
- **Purpose:** Shared backbone — prevents every Legendary line from reinventing base materials
- **Examples:** Generic frames, reinforcement components, non-elemental upgrades

### Class B — Storm-Bound Elemental Materials

- **Scope:** Stormseeker-only
- **Generation:** Location-based (peaks, exposed terrain); visible/harvestable during storms only
- **Dependencies:** Active storm required for access
- **Leyline relationship:** Leylines may amplify yield but NEVER create them
- **Hard rule:** Storms gate access, not progress state
- **Examples:** Storm-charged ores, elemental crafting reagents
- **Naming:** TBD (intentionally deferred)

### Class C — Leyline-Bound Crystals

- **Scope:** System-level material class (potentially shared)
- **Generation:** Spawn within leyline influence radius; density scales with proximity
- **Dependencies:** Leyline proximity (always collectible; leyline visibility requires attunement)
- **Visibility:** Crystals visible before attunement; leylines are not
- **Purpose:** Spatial discovery + progression texture
- **Used for:** Attunement steps, forge interactions, multi-phase gating

### Class D — Sigils

- **Scope:** Stormseeker milestone tokens
- **Generation:** Granted by ECS systems only — never random drops, never NPC-decided
- **Purpose:** Deterministic progression tokens; proof of completion; gate keys
- **Items:** `stormseeker:sigil_flowing` (Sigil A), `stormseeker:sigil_anchored` (Sigil B)
- **Rule:** Sigils represent state truth, not loot.

### What We Explicitly Avoid

- ❌ NPC-gated materials
- ❌ Quest-state-dependent world spawning
- ❌ Leylines creating matter
- ❌ RNG-only legendary progression
- ❌ Stormseeker materials leaking into unrelated mods

### Intentionally Deferred

- Exact naming of storm ores
- Final material counts per phase
- Whether some storm materials later become shared
- Post-Stormseeker cross-element hybrids

---

## Implementation Status (Code Audit: 2026-02-10)

### What Exists and Works

**LegendaryCore:**
- Gate system (`GateService`, `GateDecision`) for activation gating
- Resource ID system for stable identifiers
- Activation lifecycle (session management, attempt results)

**Legendary — Phase 2 (Dual Sigil Trials):**
- `FlowingTrialSession` / `FlowingTrialEvaluator` / `FlowAlignmentEvaluationSystem` — full Flowing Trial pipeline
- `FlowingSigilGrantSystem` / `FlowingSigilIssuer` — Sigil A grant logic
- `FlowHintEmissionSystem` / `FlowHintIntent` — presentation hints during Flowing Trial
- `FlowingTrialDetectionSystem` / `FlowTrialCompletionSystem` — trial lifecycle
- `FlowingTrialRuntimeOrchestrator` — runtime coordination
- `AnchoredTrialSession` / `AnchoredTrialParticipation` — Anchored Trial pipeline
- `AnchoredSigilGrantSystem` / `AnchoredSigilIssuer` — Sigil B grant logic
- `AnchoredTrialRuntimeOrchestrator` — runtime coordination
- `StormseekerAnchoredTrialService` — entry/leave/cleanup control surface
- Extensive test coverage: abandonment, cleanup, dual sigil symmetry, step callbacks, etc.

**Legendary — Quest Infrastructure:**
- `StormseekerProgress` — phase tracking + sigil state
- `StormseekerPhase` enum — phase definitions (names need updating)
- `StormseekerCapabilities` — capability queries per phase
- `StormseekerQuestSteps` — gate step identifiers for Phase 3+
- `StormseekerQuestStepMapper` — maps progress to quest steps
- `StormseekerObjectives` / `StormseekerObjectiveSnapshotService` — objective tracking
- `StormseekerMilestoneOutcome` / `StormseekerPhaseMilestone` — milestone emission

**Legendary — Item System:**
- `StormseekerItemIds` — stable item IDs (sigils, frame, weapon)
- `StormseekerRecraftRules` — item loss recovery logic
- `LegendaryItemIdentity` / `LegendaryItemPolicy` / `LegendaryItemRole` — item framework

**Legendary — Wiring:**
- `StormseekerWiring.tick(host)` — canonical tick entry point
- `StormseekerWiring.enterAnchoredTrial()` / `leaveAnchoredTrial()` — trial participation
- Gate registration for activation gating
- `resetForTesting()` — test isolation seam

**LegendaryHytale:**
- Plugin loads and runs in live Hytale server
- `HytaleStormseekerHost` implements `StormseekerHostRuntime`
- `StormseekerTickSystem` registered in Hytale's ECS — calls `StormseekerWiring.tick(host)` each tick
- Player connect/disconnect tracking via `PlayerRef`
- Real-time position tracking via Hytale `TransformComponent`

### What Does NOT Exist Yet

- Phase 0: Elemental spawn, movement restriction, approach detection, dust-devil mechanic
- Phase 1: Elemental pathfinding, fade-out, storm coherence trek evaluation
- Phase 1.5: Attunement event trigger, leyline-vision toggle, capability component wiring
- Phase 3: Frame crafting system, material gathering, assembly state machine
- Phase 4: Final encounter orchestration, energizing progression
- Phase 5: Post-completion systems, epilogue flags
- Persistence: `StormseekerProgress` is in-memory only (no save/load across sessions)
- Storm weather integration: reading Hytale's weather system for storm detection

### Known Code/Doc Misalignments

These are naming issues in the codebase that reflect the old (incorrect) phase structure.
The code logic itself is generally correct — the names are wrong.

| Code | Current Name | Should Be | Reason |
|---|---|---|---|
| `StormseekerPhase1Loop` | "Phase 1 coordinator" | Phase 2 Flowing Trial coordinator | Drives Flowing Trial, which is Phase 2 |
| `StormseekerAttunementService` | "Phase 1 Attunement control surface" | Flowing Trial entry service | Controls Flowing Trial entry, not attunement |
| `StormseekerPhase.PHASE_1_ATTUNEMENT` | "Phase 1 Attunement" | `PHASE_1_STORM_TREK` | Phase 1 is the Trek; Attunement is 1.5 |
| `StormseekerPhase.PHASE_1_5_AFTERSHOCK` | "Phase 1.5 Aftershock" | `PHASE_1_5_ATTUNEMENT` | This phase IS attunement |
| `StormseekerPhase.PHASE_5_FINAL_TEMPERING` | "Phase 5 Final Tempering" | `PHASE_5_EPILOGUE` | Tempering is Phase 4; Phase 5 is epilogue |
| `StormseekerCapabilities.canForgeFinalizeStormseeker()` | Checks PHASE_5 | Should check PHASE_4 | Finalization happens in Phase 4 |

### Hytale Weather System (Future Integration)

Hytale has a weather system with classes for `Weather`, `WeatherForecast`, `WeatherParticle`,
`UpdateWeather` packets, fog, clouds, and time-of-day colors. This will be critical for:

- Phase 0: Storm detection for elemental spawn
- Phase 1: Storm coherence evaluation during trek
- Phase 1.5: Storm state check for attunement trigger
- Phase 3: Storm-timed crafting (if storm timing is required)

Weather integration has not been explored yet but the server-side classes exist.

---

## Canonical Integration Pattern

All engine integration occurs via:

```java
StormseekerWiring.tick(host)
```

- Phase loops must NOT be invoked directly by engine code.
- Trial participation is host-controlled.
- `resetForTesting()` exists for JVM test isolation only.
- Durable milestones are emitted on edges (at most once per player + milestone).
- Presentation hooks are default no-ops.

---

## Document History

- **v1.0 (2026-02-04):** Original narrative, design, and quest-phases documents (now superseded).
- **v2.0 (2026-02-10):** Complete rewrite. Corrected phase structure, clarified sigil placement
  (Phase 2 only), added Thunderfury parallels, materials taxonomy, full code audit, documented
  open questions, consolidated into single source of truth.

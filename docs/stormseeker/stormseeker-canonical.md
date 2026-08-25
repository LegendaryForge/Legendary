# Stormseeker — Canonical Questline Document

> **Version:** 3.1 (phases renumbered)
> **Last Updated:** 2026-08-24
> **Status:** Narrative locked. Materials locked. Implementation partial. The phase *bodies* below
> still describe the pre-revision design and are pending a pass against
> `canon-alignment-recommendations.md`.
> **Inspiration:** Thunderfury, Blessed Blade of the Windseeker (World of Warcraft)

This document is the **single source of truth** for the Stormseeker questline. It replaces all
previous narrative.md, design.md, and quest-phases.md documents. Any contradiction between this
document and the codebase should be resolved in favor of this document.

---

## Phase numbering (renumbered 2026-08-24)

The old scheme ran Phase 0 → 5 with a fractional **Phase 1.5** left over from the v3.0 redesign,
and had no way to say "has not started" — a new player was already in Phase 0. It is now six
numbered phases bracketed by two states.

| Was | Now | `StormseekerPhase` constant | Section below |
|---|---|---|---|
| — | — | `UNTOUCHED` | *(new — the questline has not begun)* |
| Phase 0 | **Phase 1** | `PHASE_1_THE_MARK` | Phase 1 — The Watching Elemental |
| Phase 1 | **Phase 2** | `PHASE_2_THE_TREK` | Phase 2 — The Trek |
| Phase 1.5 | **Phase 3** | `PHASE_3_THE_WAKING` | Phase 3 — Attunement |
| Phase 2 | **Phase 4** | `PHASE_4_THE_TRIALS` | Phase 4 — Dual Sigil Trials |
| Phase 3 | **Phase 5** | `PHASE_5_THE_FRAME` | Phase 5 — Craft Frame |
| Phase 4 | **Phase 6** | `PHASE_6_THE_FORGING` | Phase 6 — Final Encounter |
| Phase 5 | **Complete** | `COMPLETE` | Complete — Epilogue |

**Why the constant names and the section names differ.** The constants anticipate the revised
design in `canon-alignment-recommendations.md` (a mark, a waking, a forging); the section headings
still name what the bodies below actually describe. They converge when the bodies are revised.
The *numbering* is authoritative in both.

Entries in Document History are deliberately left under their original numbering — they record what
was decided at the time, and renumbering them would falsify the record.

No save migration was kept: progress persisted under the old constant names is unreadable by
design, a clean break taken while nothing in flight depended on it.

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
| Enchanted Elementium Bars | Stormseeker Frame (Phase 5) | Crafted composite of all material classes |
| Summoning + defeating Thunderaan | Final Encounter (Phase 6) | Temper/energize frame into finished sword |
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
| 1 | The Watching Elemental | Elemental appears, watches, bolts toward Resonator leaving a trail | No — feels like a strange storm event |
| 2 | The Trek | Follow scorched earth trail to the Resonator | Partially — player follows visible trail |
| 3 | Attunement | Step on Resonator plate; leyline sight granted | Yes — world visibly changes |
| 4 | Dual Sigil Trials | Prove competence; earn Sigil A and Sigil B | Yes — explicit trials |
| 5 | Craft Frame | Construct the Stormseeker frame | Yes — gathering and crafting |
| 6 | Final Encounter | Boss fight + temper/energize into finished sword | Yes — climactic encounter |
| Complete | Epilogue | Aftermath and world acknowledgement | Yes — closure |

---

## Phase 1 — The Watching Elemental

### Resonator Structure (Independent Behavior)

The Resonator (Ancient Air Leyline Calibration Station) exists in the world as a permanent
structure. **Regardless of any player's quest state**, it exhibits the following behavior:

- **During thunder storms:** The Resonator activates — emits strong DynamicLight, visually glows, becomes alive
- **Outside storms:** Dormant, unremarkable

This is not quest-gated. Any player exploring during a storm can see a glowing structure in the
distance. This is intentional.

**Skip path:** Any player who enters the Resonator's radius and steps on a plate during a thunder
storm triggers Phase 3 (Attunement), regardless of whether they've encountered the elemental or
followed a trail. Right place, right time — rewarded. Phases 1 and 2 are skipped.

### Trigger

- Player is Phase 1 (new to the questline)
- A thunder storm is active
- **Pre-validation passes:** A viable Resonator exists within range (~500 blocks) with no ocean
  crossing required. If no valid path exists, the elemental does not appear. The player never
  knows about a failed check.

### Behavior

1. An **air/electrical elemental** spawns near the player (~25 blocks away)
2. The elemental **does not approach, does not attack, does not interact** — it hovers and watches
3. It persists for the duration of the storm, drifting subtly to stay visible (e.g., repositioning
   if the player turns away)
4. **First approach:** When the player comes within ~10 blocks, the elemental reacts immediately:
   - It **streaks away** at high speed toward the Resonator
   - As it flies, it leaves **scorched earth / electrified ground patches** on the natural terrain below
   - After traveling a visible distance, it moves beyond the player's sight and **despawns**
   - The trail remains until the storm ends

### If the Player Ignores the Elemental

- The elemental fades when the storm ends
- Next thunder storm: the elemental appears again, same behavior
- No punishment, no lost progress, no conditioning to ignore it

### Key Design Decisions

- The elemental appears on the **first storm** of a Phase 1 player's experience (pending path validation)
- The elemental **bolts on the first approach** — no multi-storm watching phase, no training the player to ignore it
- Within a single storm, the watching period is brief (~1-2 minutes of hovering before the player approaches)
- The entire Phase 1 → 2 → 3 sequence is **completable within a single storm**

### Transition to Phase 2

When the player approaches the elemental (~10 blocks), the elemental bolts toward the Resonator,
leaving a trail of scorched earth. The trail marks the beginning of Phase 2.

### Systems Required

- Storm weather detection (when is a storm active?)
- Pre-flight validation (Resonator within ~500 blocks, no ocean crossing; ~50 block water cap)
- Elemental entity registration + spawn logic (proximity to player during storm)
- Elemental hover/drift behavior (stay visible, reposition subtly)
- Player proximity detection (~10 block approach trigger)
- Elemental bolt behavior (streak toward Resonator at high speed)
- Scorched earth trail placement (along bolt path, ground-snapped to highest natural block)
- Elemental despawn after traveling beyond player sight

---

## Phase 2 — The Trek

### Trigger

The elemental has bolted, leaving a trail of scorched earth toward the Resonator.

### Behavior

1. The player follows the trail of scorched/electrified ground patches toward the Resonator
2. The trail is **self-directed** — no escort, no guide, no timer pressure beyond the storm's duration
3. The Resonator is already **glowing in the distance** (independent storm behavior), serving as a
   long-range visual beacon
4. Distant lightning VFX may appear at the Resonator's location for additional atmospheric guidance

### Trail Characteristics

**Placement:**
- Scorched earth patches placed every ~20 blocks (15-25 range) along the elemental's flight path
- Placed on the **highest natural block** at each position (ground-snapped)
- Only placed on **natural blocks** (grass, dirt, sand, stone, snow, etc.)
- **Not placed on artificial/player-built blocks** — trail naturally skips over structures

**Obstacle Handling:**
- **Natural terrain (cliffs, ravines, rivers, small bodies of water):** The trail goes straight
  through. These are fair game — the player climbs, swims, bridges across. This is part of the adventure.
- **Artificial structures (player builds, walls, castles):** The elemental's flight path curves
  around these. The elemental is a sentient, natural entity — it wouldn't fly through a building.
  The trail reflects this intelligence.
- **Large gaps:** Where structures create a gap in the trail, the Resonator's storm glow and distant
  lightning serve as long-range direction confirmation.

**Trail Intelligence:**
The elemental knows the player can't fly. While it moves through the air, the trail it leaves is
designed to be followable on foot. It doesn't pathfind a perfectly walkable route (natural obstacles
are fair game), but it avoids leading the player into artificial dead ends.

Implementation approach: scan ahead along the flight vector, detect artificial blocks, nudge the
path laterally to go around them. Not full A* pathfinding — more like "smart straight line with
structure avoidance."

**Leave No Trace:**
- All scorched earth patches store the original block state before modification
- When the storm ends, **all patches are restored** to their original state
- The world returns to normal — no permanent modification

### If the Player Doesn't Reach the Resonator Before the Storm Ends

- Trail dissipates (blocks restored via Leave No Trace)
- No punishment, no lost progress
- Next thunder storm: Phase 1 resets — elemental appears again, same sequence
- Player gets unlimited attempts

### Transition to Phase 3

Phase 2 ends when the player reaches the Resonator during an active thunder storm.

### Worldgen Consideration

Resonator structures must be placed at a density that ensures:
- At least one Resonator is likely within ~500 blocks of any given player position
- The Resonator is reachable within the duration of a single thunder storm (accounting for terrain
  traversal, not straight-line distance)
- Storm duration and Resonator density are balanced so the Phase 1→2→3 sequence is completable
  in one storm without rushing

### Systems Required

- Scorched earth trail placement along elemental bolt path
- Natural vs. artificial block detection (trail skips player-built blocks)
- Structure avoidance (lateral nudge around artificial structures)
- Block state read/write (store original, place scorched, restore on cleanup)
- Leave No Trace cleanup (restore all patches when storm ends)
- Resonator DynamicLight activation during thunder storms (independent behavior)
- Player proximity detection (arrived at Resonator)

---

## Phase 3 — Attunement

### Trigger

- Player reaches the Resonator during a thunder storm
- Player **steps on one of the structure's plates**

### Behavior

The 30-second Attunement Ritual begins (per `StormseekerAttunementService.java`):

- **Spool Up (5s):** Plate activates, visuals and audio intensify
- **Active Lock (15s):** Player is rooted, full intensity, uninterruptible
- **Spool Down (5s):** Energy dissipates, ritual completes

On completion: `AttunementCompleteEvent` fires, Leyline Sight unlocks.

- Up to 6 plates can handle independent rituals simultaneously
- Stepping off during Spool Up interrupts the ritual (player can retry)

### What the Player Gains

- **Leyline Sight toggle** (`PerceptionToggleHandler`): Persistent ability (survives logout). Toggleable on/off.
- Leylines always existed in the world. Now the player can see them on demand.
- Leylines are NOT quest arrows. They are world geometry — influence flows, energy paths beneath
  the terrain.

### Post-Attunement

- Player advances to Phase 4 (Dual Sigil Trials)
- The Flowing and Anchored Trials become available
- Leyline Sight is unlocked but keybind not yet wired

### Transition to Phase 4

Attunement is the pivot. Phase 4 becomes available immediately after.

### Systems Required

- Attunement ritual state machine (Spool Up → Active Lock → Spool Down)
- Plate interaction detection (player steps on/off plate)
- Player rooting during Active Lock phase
- `AttunementCompleteEvent` emission
- Multi-plate support (up to 6 simultaneous rituals)
- Player capability component: "can perceive leylines"
- Leyline Sight toggle ability (persistent across sessions)
- Attunement milestone emission (durable edge, at most once per player)

---

## Phase 4 — Dual Sigil Trials

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

### Transition to Phase 5

Phase 4 ends once both sigils are obtained. This is the first truly "gated" step — but the gate
is enforced by systems, not NPC entitlement.

### Phase 4 Completion (Precise Statement)

Phase 4 is complete only when Flowing Sigil is granted AND Anchored Sigil is granted. Nothing
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

## Phase 5 — Craft Frame

### Narrative

The player is now trusted enough to construct the Stormseeker frame — the vessel that can survive
what comes next. This is construction, not tempering. The frame is inert until Phase 6.

### Materials Required

Phase 5 crafting draws from three of the four material classes:

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

### Transition to Phase 6

Phase 5 ends when the player possesses the Stormseeker frame.

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

## Phase 6 — Final Encounter

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

### Transition to Complete

Phase 6 ends when the energizing completes and the weapon becomes Stormseeker (finished state).

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

## Complete — Epilogue

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

**Legendary — Phase 4 (Dual Sigil Trials):**
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
- `StormseekerPhase` enum — phase definitions
- `StormseekerCapabilities` — capability queries per phase
- `StormseekerQuestSteps` — gate step identifiers for Phase 5+
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

- Phase 1: Pre-flight validation (ocean check), elemental entity registration + spawn, hover/drift behavior, approach detection, bolt behavior, scorched earth trail placement
- Phase 2: Natural vs. artificial block detection, structure avoidance (lateral nudge), block state read/write, Leave No Trace cleanup, Resonator DynamicLight storm activation
- Phase 3: Attunement ritual state machine (spool up/active lock/spool down), plate interaction detection, player rooting, `AttunementCompleteEvent`, multi-plate support, leyline-vision toggle, capability component wiring
- Phase 5: Frame crafting system, material gathering, assembly state machine
- Phase 6: Final encounter orchestration, energizing progression
- Complete: Post-completion systems, epilogue flags
- Persistence: `StormseekerProgress` is in-memory only (no save/load across sessions)
- Storm weather integration: reading Hytale's weather system for storm detection

### Known Code/Doc Misalignments

These are naming issues in the codebase that reflect the old (incorrect) phase structure.
The code logic itself is generally correct — the names are wrong.

| Code | Current Name | Should Be | Reason |
|---|---|---|---|
| `StormseekerAttunementService` | "Phase 2 Attunement control surface" | Phase 3 Attunement ritual service | Now correctly refers to attunement (Phase 3 redesign); name is accurate but phase label needs updating |

Resolved in v3.0:
- ~~`StormseekerPhase1Loop`~~ → Fixed: now `StormseekerFlowingTrialLoop` (Phase 4 Flowing Trial coordinator)
- ~~`StormseekerPhase1Outcome`~~ → Fixed: now `StormseekerFlowingTrialOutcome`
- ~~`StormseekerPhase1TickView`~~ → Fixed: now `StormseekerFlowingTrialTickView`
- ~~`emitPhase1TickView()` / `emitPhase1Outcome()`~~ → Fixed: now `emitFlowingTrialTickView()` / `emitFlowingTrialOutcome()`
- ~~`phase1Attunement()`~~ → Fixed: now `phase2FlowingTrial()`
- ~~`StormseekerObjectiveSnapshotService` Flowing Trial mapped to Phase 2~~ → Fixed: now mapped to Phase 4
- ~~`StormseekerPhase.PHASE_1_ATTUNEMENT`~~ → Fixed: now `PHASE_1_STORM_TREK`
- ~~`StormseekerPhase.PHASE_1_5_AFTERSHOCK`~~ → Fixed: now `PHASE_1_5_ATTUNEMENT`
- ~~`StormseekerPhase.PHASE_5_FINAL_TEMPERING`~~ → Fixed: now `PHASE_5_EPILOGUE`
- ~~`StormseekerCapabilities.canForgeFinalizeStormseeker()` checks PHASE_5~~ → Fixed: now checks `PHASE_4_STORMS_ANSWER`
- ~~`StormseekerPhase.PHASE_0_UNEASE`~~ → Fixed: now `PHASE_0_WATCHING_ELEMENTAL`
- ~~`StormseekerQuestSteps.PHASE_5_EPILOGUE` string value "final_tempering"~~ → Fixed: now `"stormseeker.phase5.epilogue"`

### Hytale Weather System (Future Integration)

Hytale has a weather system with classes for `Weather`, `WeatherForecast`, `WeatherParticle`,
`UpdateWeather` packets, fog, clouds, and time-of-day colors. This will be critical for:

- Phase 1: Storm detection for elemental spawn + pre-flight validation
- Phase 2: Storm duration tracking (trail lifetime, Leave No Trace cleanup trigger)
- Phase 3: Storm state check for attunement trigger at Resonator
- Phase 5: Storm-timed crafting (if storm timing is required)

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

- **v3.1 (2026-08-24):** Phases renumbered — six numbered phases bracketed by `UNTOUCHED` and
  `COMPLETE`, removing the fractional Phase 1.5 and separating "has not started" from "is in the
  first phase". Mapping table at the head of this document. The code was renumbered in the same
  change. No design content was altered; the phase bodies still describe v3.0.

- **v1.0 (2026-02-04):** Original narrative, design, and quest-phases documents (now superseded).
- **v2.0 (2026-02-10):** Complete rewrite. Corrected phase structure, clarified sigil placement
  (Phase 2 only), added Thunderfury parallels, materials taxonomy, full code audit, documented
  open questions, consolidated into single source of truth.
- **v3.0 (2026-02-16):** Phase 0/1/1.5 redesign. Removed movement restriction mechanic (not
  exposed in Hytale API). Phase 0 is now a watching elemental that bolts on approach, leaving a
  scorched earth trail. Phase 1 is self-directed trail following to the Resonator. Phase 1.5 is
  a 30-second attunement ritual at the Resonator's plates. Added Resonator independent storm
  behavior, skip path, pre-flight ocean validation, Leave No Trace cleanup, structure avoidance.
  See `phase-0-1-redesign-final.md` for full rationale.

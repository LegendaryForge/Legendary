# Stormseeker — Canonical Design

> **Source:** Stormseeker Canonical Design v1.2
> **Last Updated:** 2026-02-04

This document defines the **locked gameplay mechanics** for Stormseeker. These are design commitments that should not be changed without explicit discussion.

---

## Questline Phases (Locked Decisions)

| Phase | Name | Purpose | Status |
|-------|------|---------|--------|
| 0 | Storm Unease | Introduce storms + subtle guidance effects | **Locked** |
| 1 | Storm Attunement | Elemental-guided trek; convergence structure; unlock leyline-vision toggle | **Locked** |
| 1.5 | Aftershock | Cooldown and narrative beat after attunement | **Locked** |
| 2 | Dual Sigil Trials | Flowing + Anchored trials award Sigil A and Sigil B | **Locked** (implementation in progress) |
| 3+ | Later phases | Storms answer, final tempering, endgame forge beats | **Pending design** |

---

## Leylines, Crystals, Ores (Locked)

### Leylines
- Always exist in the world
- Only visible via a **toggled ability** unlocked after attunement
- Cannot be turned on/off by default; must be earned

### Elementally Charged Crystals
- Always visible/collectable
- Spawn **exclusively within leyline influence radius**
- Density scales with proximity to leyline convergence points

### Storm-Bound Elemental Ore
- Visible **only during storms**
- Location-based spawning (e.g., mountain peaks)
- Leylines do **not** create ore
- Leylines **may** amplify yield

### Generic Legendary Material
- Uses vanilla-like generation
- Not tied to leylines or storms

---

## Participation and Fairness

### NPC Philosophy
- NPCs are **optional accelerators, not requirements**
- Shamans can exist in isolation
- Blacksmith knowledge conveyed via **ruins/relics** (Ancient Forge) to avoid village dependency

### Multiplayer Design
- Private/party-based encounters
- **Late joiners allowed to spectate** (cannot affect outcome)
- Preserves witnessability without trivializing difficulty

---

## Phase 2 Anchored Trial Mechanic (Current Scaffold)

### Design Intent
Test the player's ability to **remain still** under pressure—the opposite of the Flowing Trial.

### Current Implementation

- **AnchoredTrialSession** tracks stationary streak
- Required stationary ticks: **40** (tunable)
- Stationary defined as: `!MotionSample.moving()`

### Success Condition
When streak reaches threshold:
- Session grants **Sigil B** via `StormseekerProgress.grantSigilB()`
- `AnchoredTrialHostTick` observes session progress
- Emits durable milestones on edges

### Presentation Hook
Host may receive per-step views via `StormseekerHostRuntime.onAnchoredTrialStep` (default no-op)

---

## Implementation Integration (2026-02-08 Update)

**CRITICAL:** Canonical engine integration now occurs via:

```java
StormseekerWiring.tick(host)
```

- Phase 1 and Phase 2 loops must **NOT** be invoked directly by engine code
- Anchored Trial participation is **host-controlled**
- `resetForTesting()` exists for JVM test isolation only

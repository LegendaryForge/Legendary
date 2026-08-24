# Stormseeker — Canonical Design

> **Source:** Stormseeker Canonical Design v1.3
> **Last Updated:** 2026-02-15

This document defines the **locked gameplay mechanics** for Stormseeker. These are design commitments that should not be changed without explicit discussion.

---

## Questline Phases (Locked Decisions)

| Phase | Name | Purpose | Status |
|-------|------|---------|--------|
| 0 | Storm Unease | Introduce storms + subtle guidance effects | **Locked** |
| 1 | The Trek | Elemental-guided trek; smart pathfinding; restoration persistence | **Locked** |
| 1.5 | Attunement | Ancient Air Leyline Calibration Station; unlock leyline-vision toggle | **Locked** |
| 2 | Dual Sigil Trials | Flowing + Anchored trials award Sigil A and Sigil B | **Locked** (implementation in progress) |
| 3+ | Later phases | Storms answer, final tempering, endgame forge beats | **Pending design** |

---

## Locked Mechanics

### Leyline Sight
- **Type:** Permanent, non-expiring perception shift.
- **Unlock:** Granted upon completing the Attunement event at the Ancient Air Leyline Calibration Station.
- **Input:** Toggled via a KeyBinding (default: 'V').
- **Effect:**
    - Renders **Leyline Harmonic** particle streams (sky/ground trails).
    - Applies a subtle **Electric Blue** color grade to the world.
    - Plays a faint, atmospheric wind/hum audio loop.
- **Requirement:** Must be active to perceive and participate in Phase 2 Trials.
- **Persistence:** State remains active across weather changes and world zones until manually toggled off.

### Leylines
- Always exist in the world.
- Only visible via **Leyline Sight**.
- Cannot be turned on/off by default; must be earned.

### Elementally Charged Crystals
- Always visible/collectable.
- Spawn **exclusively within leyline influence radius**.
- Density scales with proximity to leyline convergence points.

### Storm-Bound Elemental Ore
- Visible **only during storms**.
- Location-based spawning (e.g., mountain peaks).
- Leylines do **not** create ore.
- Leylines **may** amplify yield.

### Generic Legendary Material
- Uses vanilla-like generation.
- Not tied to leylines or storms.

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

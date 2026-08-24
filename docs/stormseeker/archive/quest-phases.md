# Stormseeker — Quest Phases (Implementation Guide)

> **Source:** Stormseeker Quest Spine v2.3
> **Last Updated:** 2026-02-15

This document defines **player-visible beats** and what the code must support, without over-committing to engine-specific assumptions.

---

## Spine Overview

Stormseeker is the first Legendary questline. The spine defines the **progression structure** that connects narrative intent to gameplay implementation.

---

## Phase 0 — Storm Unease

### Player Experience
- Storms occur naturally
- Player experiences **subtle directional guidance effects** (non-authoritative presentation)
- Guidance intensifies when moving generally toward the next objective
- Guidance weakens when moving away

### Implementation Constraints
- No hard gating
- Effects should be **infrequent enough to avoid annoyance**
- Purely atmospheric—should feel like the world is subtly guiding, not forcing

---

## Phase 1 — The Trek

### Player Experience
- Elemental-guided trek across the world.
- The path is not a straight line; it follows the contours of the land.
- The trail is marked by 'scorched earth' breadcrumbs and distant lightning strikes.

### Implementation Requirements
- **Navigation-Aware Pathfinding:** The elemental calculates a 'Humanoid-Walkable' path to the destination.
    - Prioritizes walkable terrain.
    - Evaluates water crossings (high cost for extensive swimming).
    - Treats 'Artificial' blocks (walls, planks, bricks) as high-cost obstacles to avoid player-built structures.
- **Smart Breadcrumbs:** 'Scorched earth' blocks are placed at ~20 block intervals along the calculated path.
    - Placement uses raycasting to snap to the highest natural ground level.
    - Only natural blocks (grass, dirt, sand, stone, snow) are modified.
- **Restoration Persistence:**
    - Original block states are stored in a thread-safe `ConcurrentHashMap<BlockPos, BlockState>`.
    - This map persists based on the Storm's status, independent of player login sessions.
    - **Global Cleanup:** On the `onWeatherChanged` event (storm end), the system iterates through the map and restores all modified blocks to their original state.

---

## Phase 1.5 — Attunement

### Player Experience
- The trek culminates at an **Ancient Air Leyline Calibration Station**.
- This is a 6-player platform structure found in the world.
- Standing on the platform during a storm triggers the attunement event.

### Implementation Requirements
- **Permanent Unlock:** Completing the attunement grants the **Leyline Sight** ability.
- This unlock is persistent (survives logout) and is stored in the player's capability data.
- **Visuals:** Unlocking triggers a permanent change in how the player perceives the world (see Design > Locked Mechanics).

---

## Phase 2 — Dual Sigil Trials

### Player Experience
Two complementary trials that test opposing approaches:

#### Flowing Trial → Sigil A
- Tests movement, adaptability, momentum
- (Implementation details TBD)

#### Anchored Trial → Sigil B
- Tests stillness, patience, control
- Player must remain stationary under duress

### Implementation Requirements
- Both trials grant **binary proofs** (sigils)
- Remain **deterministic and testable**
- When both sigils present, emit **DUAL_SIGILS_GRANTED** milestone edge
- Trials are **independent** (can be completed in any order)

---

## Phase 2 Implementation Notes (as of 2026-02-04)

### Anchored Trial Scaffold Status
✅ Merged to main
- Host driver filters participants and ticks only those playerIds
- Dogfood harness in LegendaryContent validates Sigil B grant and milestone emission
- Host hook present for anchored step view; driver delegates; host tick calls it

### Integration Pattern
**NOTE (2026-02-08):** Anchored Trial milestone emission is edge-based and coordinated exclusively via:

```java
StormseekerWiring.tick(host)
```

Direct invocation of phase loops from engine code is **not permitted**.

---

## Phase 3+ — Future Phases

### Narrative Intent
- Storms answer the player's attunement
- Final tempering trials
- Endgame forge beats
- Convergence and claim

### Design Status
**DEFERRED** — Pending detailed design.

These phases are narratively outlined but not yet mechanically specified. Implementation should not begin until design is locked.

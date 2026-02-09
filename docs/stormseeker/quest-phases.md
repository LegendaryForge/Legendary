# Stormseeker — Quest Phases (Implementation Guide)

> **Source:** Stormseeker Quest Spine v2.2
> **Last Updated:** 2026-02-04

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

## Phase 1 — Storm Attunement

### Player Experience
- Elemental-guided trek
- Culminates in a **convergence structure**
- Unlocks **leyline-vision toggle** ability
- Leylines become visible only while toggled

### Implementation Requirements
- After completion, grant **Sigil A** milestone (durable edge)
- Toggle ability must be persistent (survives logout)
- Leylines are always present in world generation, only visibility changes

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

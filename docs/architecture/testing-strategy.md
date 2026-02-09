# Testing Strategy

> **Source:** Hytale Modding Working Reference Pack v1.2
> **Last Updated:** 2026-02-04

## Stormseeker Runtime Seams (Current)

### Core Components

- **StormseekerProgress:** Authoritative binary sigils + phase tracking
  - `advanceIfEligible` enforces sequencing for Phase 2 → later phases based on sigil proofs

- **StormseekerAnchoredTrialService:** Enter/leave/tick control surface
  - Denies entry if Sigil B already present

- **AnchoredTrialParticipation:** Authoritative participation set
  - Engine decides when players enter/leave

- **AnchoredTrialHostDriver:** Wraps runtime to participating-only runtime
  - Filters playerIds, then delegates to host tick

- **AnchoredTrialHostTick:** Per-player stepping
  - Emits durable milestone edges
  - Forwards per-step view via `onAnchoredTrialStep`

- **AnchoredTrialSession:** Gameplay-side mutation
  - Tracks stationary streak → grants Sigil B

## Testing Approach

### Unit Tests (Legendary)
Validate pure session behavior + milestone edges

### Harness Tests (LegendaryContent)
Validate end-to-end seams via:
- Composite build wiring
- Realistic fake host runtime

### Design Principle
Keep gameplay mutation in **sessions**. Host tick should stay as **observer + emitter**.

# Repository Structure

> **Source:** Legendary Content Architecture v1.5
> **Last Updated:** 2026-02-04

## Repository Roles

### LegendaryCore
Shared library mod providing:
- Encounter lifecycle management
- Activation systems
- Invariants and utilities
- Engine-agnostic foundation for deterministic legendary encounters

### Legendary
Main quest mod containing:
- Stormseeker questline (and future questlines)
- Authoritative content logic
- Gameplay seams and integration points

### LegendaryContent
Dogfooding and validation mod:
- Harness tests for end-to-end seams
- Proving ground for gameplay systems
- Integration validation

## Composite Build Strategy

`LegendaryContent` uses Gradle composite builds to include:
- `../LegendaryCore`
- `../Legendary`

Uses dependency substitution so test code can depend on stable coordinates but resolve to local included builds.

### Coordinates

- **Legendary:** `io.github.legendaryforge:Legendary:0.0.0-SNAPSHOT`
- **LegendaryCore:** As defined in LegendaryCore build (included via composite build substitution)

## Dogfood Harness Philosophy

- **Harness-only code stays in LegendaryContent tests** (not in Legendary)
- Harness exercises gameplay mutation inside sessions (e.g., `AnchoredTrialSession` grants Sigil B)
- Host tick observes state and emits durable milestones on edges
- This keeps milestone emission truthful and avoids moving gameplay logic into host tick

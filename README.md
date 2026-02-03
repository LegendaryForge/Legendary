# Legendary

**Legendary** is the canonical gameplay and questline mod built on top of
**LegendaryCore**.

This repository hosts Legendary questlines (starting with **Stormseeker**) and
the **content-side systems** that exercise LegendaryCore’s encounter,
activation, and access-control primitives.

Legendary is where *player-facing legendary gameplay* lives.

---

## Purpose

Legendary exists to implement high-value, replayable legendary questlines using
the deterministic foundations provided by LegendaryCore.

It intentionally sits **above Core** and **below any engine/platform layer**.

This repo answers the question:

> “What does a real legendary questline look like when built correctly on the
> LegendaryCore model?”

---

## Scope

Legendary owns:

- Questline modules and wiring (enable/disable by questline)
- Content-side runtime systems and orchestration
- Quest-specific ECS systems, evaluators, and state machines
- Explicit host integration seams (engine-agnostic)
- Canonical questline implementations (e.g. Stormseeker, phased)

Legendary explicitly does **not** own:

- Engine or platform-specific code
- Deterministic encounter lifecycle primitives
- Access policy enforcement logic
- Validation-only or dogfood harnesses

Those concerns live in other repos by design.

---

## Architectural Position

Legendary follows the canonical Legendary architecture:

- **Events** define engine truth
- **ECS Systems** implement all authoritative gameplay logic
- **Entities & Components** embody durable world state
- **NPC Meta** is perception and expression only

Legendary systems consume Core-provided events and services, but never embed
engine assumptions directly.

All engine/platform coupling must be isolated behind explicit seams.

---

## Repositories

- **LegendaryCore**  
  Shared deterministic encounter foundation and activation primitives

- **Legendary** (this repo)  
  Canonical legendary gameplay and questline implementations

- **LegendaryContent**  
  Dogfooding and validation mod used to exercise Core + Legendary end-to-end

---

## Status

Under active development.  
Phase-based questlines are added incrementally as systems are validated.

---

## License

MIT License. See `LICENSE`.

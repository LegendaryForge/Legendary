# Hytale Modding - ECS Architecture Principles

> **Source:** Hytale Canonical Modding Architecture v1.2
> **Last Updated:** 2026-02-04

## Canonical Mental Model

- **Events** define engine truth (what happened)
- **ECS Systems** implement all authoritative gameplay logic via Components
- **Entities** embody state in the world (components attached)
- **NPC Meta** is strictly for local perception and expression (UI, barks, VFX, non-authoritative presentation)

### Implication

Content/quests should mutate authoritative state through **Systems and Components**. Host/runtime interfaces exist to surface presentation hooks and durable milestone edges, **not to encode gameplay logic**.

## Determinism and Multiplayer Principles

- Quest and encounter logic should be **deterministic and testable** (dogfood harnesses in LegendaryContent)
- Design language must remain truthful: primarily **single-player compatible**; multiplayer encouraged
- **Late joiners may spectate** (no affect) to preserve witnessability without trivializing difficulty

## Host/Runtime Boundary

- `StormseekerHostRuntime` is the engine-agnostic seam for:
  - Ticking trials
  - Reading motion samples
  - Emitting durable milestone outcomes

- **Presentation hooks** are default no-ops (e.g., `onFlowingTrialStep`, `onAnchoredTrialStep`) so host implementations are not forced to handle them

- **Durable milestones** are emitted on edges (at most once per player+milestone)

## Hard Constraints

### Architectural Rules (Non-Negotiable)

- ECS Systems are the **ONLY** place authoritative gameplay logic lives
- NPC Meta must **NEVER** decide progression, rewards, or entitlement
- No architectural redesigns unless explicitly requested

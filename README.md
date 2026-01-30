# LegendaryContent

LegendaryContent is a small legendary content mod built to dogfood **LegendaryCore**.

Its purpose is to validate encounter lifecycle seams *outside* of Core before any behavior or abstraction is promoted upstream. All gameplay behavior lives here first; Core is responsible only for signaling lifecycle events.

There is no marketing intent in this repository. It exists solely to prove correctness, scope, and ownership boundaries.

---

## Purpose

LegendaryContent exists to:

- Implement content-side behavior first
- Validate lifecycle seams emitted by LegendaryCore
- Promote **only proven seams** into Core
- Avoid premature abstraction

This repo is where behavior is tested under real content conditions before Core APIs are expanded.

---

## What’s in this repository

### Content definitions

- `ToyLightningEncounterDefinition`
- Example scripts:
  - `ToyLightningScript`
  - `ToyStormScript`

Each encounter owns **per-instance script state**, managed entirely by content.

---

### Content-side wiring

#### ScriptedEncounterManager

Decorates `EncounterManager` to invoke script hooks on:

- Join
- Participation

This is content behavior layered on top of Core’s admission logic.

#### ScriptEventBridge

Subscribes to Core lifecycle events and forwards them to scripts:

- Encounter start
- Encounter end

No Core APIs are modified to support this.

#### ScriptCleanupBridge

Subscribes to `EncounterCleanupEvent` emitted by LegendaryCore and forwards cleanup signals to content scripts.

This enables deterministic, per-instance teardown owned by content.

---

### Script abstractions

- `EncounterScript`  
  Base script contract for content behavior.

- `EncounterCleanupHandler`  
  **Content-only cleanup seam.**  
  This is *not* part of LegendaryCore.

Cleanup handling is intentionally implemented and validated here before any consideration of promotion.

---

## Cleanup model (authoritative)

### Ownership

**Cleanup is content-owned.**

#### LegendaryCore

- Emits `EncounterCleanupEvent`
- Guarantees the event fires **exactly once per encounter instance**
- Does **not** perform cleanup logic
- Does **not** define teardown semantics

#### LegendaryContent

- Owns per-instance cleanup semantics
- Clears script-managed state
- Defines what teardown means for content

This separation is deliberate dogfooding before any cleanup API promotion.

---

## Tests

The test suite validates the full lifecycle:

1. Encounter starts  
2. Script state is created  
3. Encounter ends  
4. `EncounterCleanupEvent` fires **exactly once**  
5. Script per-instance state is cleared  

Run:

```bash
./gradlew clean test

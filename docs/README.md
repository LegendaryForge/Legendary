# Legendary Project Documentation

This directory contains the **canonical design documentation** for the Legendary project. These documents define the creative vision, architectural principles, and implementation constraints.

---

## Documentation Structure

### 📐 Architecture (`/architecture`)
Core technical principles that govern how the mod is built:
- **[ecs-principles.md](architecture/ecs-principles.md)** - ECS architecture, host/runtime boundaries, multiplayer design
- **[repository-structure.md](architecture/repository-structure.md)** - How LegendaryCore, Legendary, and LegendaryContent relate
- **[testing-strategy.md](architecture/testing-strategy.md)** - Unit tests, harness tests, and validation approach

### ⚡ Stormseeker (`/stormseeker`)
The first Legendary questline:
- **[narrative.md](stormseeker/narrative.md)** - The story as experienced by the player
- **[design.md](stormseeker/design.md)** - Locked gameplay mechanics and design decisions
- **[quest-phases.md](stormseeker/quest-phases.md)** - Implementation guide for each phase

---

## How to Use This Documentation

### For Implementation Work
1. Read the relevant **quest-phases.md** to understand what needs to be built
2. Check **design.md** for locked mechanical constraints
3. Reference **ecs-principles.md** to ensure architectural compliance
4. Consult **testing-strategy.md** for how to validate your work

### For Creative/Design Work
1. Start with **narrative.md** to understand the player journey
2. Use **quest-phases.md** to connect narrative beats to implementation
3. Reference **design.md** when making mechanical decisions

### For Handoffs / New Contributors
1. Read **narrative.md** to understand the vision
2. Skim **design.md** to see what's locked vs pending
3. Review **ecs-principles.md** for non-negotiable constraints
4. Check the actual repo code for current implementation state

---

## Authority Hierarchy

When in doubt about what's "true":

1. **Code in the repositories** - Source of truth for what's implemented
2. **These markdown docs** - Source of truth for what should be implemented
3. **Conversations/chat history** - Non-authoritative, disposable

---

## Document Maintenance

### When to Update
- Design documents should be updated when creative decisions change
- Architecture documents should be updated when principles evolve
- Quest phase documents should be updated when implementation scope is locked

### What NOT to Do
- Don't update docs to reflect implementation progress (that's what git commits are for)
- Don't delete deferred/future content (mark it as `DEFERRED` or `FUTURE` instead)
- Don't duplicate implementation details that belong in code comments

---

## Current Status (2026-02-04)

### ✅ Locked & Implemented
- Phase 0, 1, 1.5 design locked
- Phase 2 design locked
- Phase 2 Anchored Trial scaffolded and merged

### 🚧 In Progress
- Phase 2 full implementation

### 📋 Pending Design
- Phase 3+
- Flowing Trial mechanics
- Ancient Forge implementation details

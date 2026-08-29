# Hytale — Deferred Work

Single canonical home for open Hytale deferrals. Inherits the discipline spec at
`Knowledge/MainVault/Systems/AIWorkspace/Deferral_Discipline.md`; parallel-sibling to
`Projects/PermitKit/.planning/DEFERRED.md`.

> **Created 2026-08-29.** Hytale was the only active project without one. Its deferred items had
> been living in the session-status doc's "Open — carry forward" list, which is a deferral queue
> with **no triggers** — and it shows: the `StormseekerPhase` rename has been re-typed at every
> session close since session 6, and N7 since session 10. The Deferral Discipline's own test —
> *"would I be okay with this sitting for years if the trigger never fires?"* — had already been
> answered by six sessions of silence.
>
> Lives in `Legendary/` rather than `Projects/Hytale/` deliberately: the latter is tracked by no
> repository at all (see #3).

## What belongs here, and what does not

**Here:** work with an honest **proactive** trigger that closes it. Reactive triggers ("fix when it
breaks", "fix when someone complains") are denial with extra steps.

**Not here:** active next work. N6's nexus-proximity density term, the `6×240` defaults, and
`nullRadius = 200` are unimplemented specs that are *next*, not deferred — they live in the session
doc's next-actions list. Filing active work here would make the queue the place work goes to be
forgotten, which is the failure this file exists to end.

**Also not here:** items awaiting only an operator decision that are already recorded at a
session-init read point. The `PreToolUse` cwd hook is recorded in workspace `CLAUDE.md`; filing it
again would dress an approval-wait as tracked work.

**Counter-balance:** filing here should cost at least as much as fixing the small thing inline. If
deferring becomes effortless, the carry-forward list returns in a new file.

---

## Trigger Index

**Scan at session-init.** Trigger-side discoverability is the point: you should be able to tell
whether anything fires today without reading the bodies.

- **The Stormseeker content spine is migrated to Hytale's native objectives** → [#1 StormseekerPhase rename](#1--stormseekerphase-renameretirement)
- **`SegmentMath` crossing filtering or `nexusesWithin` bounds logic is next modified** → [#2 N7 Z-filter regression hole](#2--n7--the-z-filter-regression-hole)
- **Any session is about to edit `Projects/Hytale/CLAUDE.md`** → [#3 project CLAUDE.md is untracked](#3--projectshytaleclaudemd-is-version-controlled-by-no-repository)
- **Questline #2 begins** → [#4 questline framework adoption path is undesigned](#4--the-questline-framework-adoption-path-is-undesigned)

---

## 1 — `StormseekerPhase` rename/retirement

**Open since:** session 6.
**Trigger type:** natural-coupling.
**Trigger:** the Stormseeker content spine is migrated to Hytale's native objectives.

`StormseekerPhase` is an enum of `PHASE_1_THE_MARK`-style constants consumed across
`quests/stormseeker` — `StormseekerProgress`, `StormseekerQuestStepMapper`,
`StormseekerFlowingTrialOutcome`. The rename was wanted from session 6 ("Phase1 → FlowingTrial")
and has been re-typed at every session close since.

The trigger is honest rather than invented: the 2026-08-25 decision
(`docs/architecture/native-objectives-migration-cost.md`) adopts native objectives for the content
spine, and `StormseekerPhase` **is** the spine's state model. That migration rewrites it regardless,
so renaming it beforehand is wasted work and renaming it afterwards is free.

**If the trigger never fires:** acceptable. The names are internal to one questline module, cost
nothing at runtime, and mislead only a reader already inside Stormseeker code.

## 2 — N7 — the Z-filter regression hole

**Open since:** session 10.
**Trigger type:** natural-coupling.
**Trigger:** `SegmentMath` crossing filtering or `nexusesWithin` bounds logic is next modified.

Production is correct. The test meant to prove Z filtering is independent of X does **not** kill the
`maxZ → maxX` mutation, so the regression it exists to catch would pass. A correct test must
*select* a crossing whose coordinates straddle both bounds — it cannot be written blind to the
geometry, which is why it was deferred rather than patched.

The coupling is real and near: N6's density work moves crossings into `CurrentGeometry` as an eager
field and touches this exact filtering path.

**If the trigger never fires:** acceptable but uncomfortable — it means a known-blind test sits in a
green suite. Escalate if a second blind test is found in the same file.

## 3 — `Projects/Hytale/CLAUDE.md` is version-controlled by no repository

**Open since:** 2026-08-29.
**Trigger type:** anticipatory-condition.
**Trigger:** any session is about to edit that file.
**Blocked on:** an operator decision, not on work.

The workspace repo ignores `Projects/` (`.gitignore:11`), `Projects/Hytale/` is not itself a
repository, and `Legendary/` begins one directory below. The file sits in the gap: auto-loaded into
every session, 200+ lines of build, branch, guard and jar knowledge, with no history and no recovery
path. `git status` in either enclosing repo reports a clean tree, so the exposure is invisible.

The obvious fix — move it into `Legendary/` — has a real cost: sessions start with cwd at
`Projects/Hytale/`, so moving it down may stop it auto-loading, which is the property that makes it
worth having. That trade-off is the operator's to settle.

**If the trigger never fires:** not acceptable, which is why the trigger is set at "about to edit"
rather than at a milestone. Every edit is another increment of unrecoverable content.

## 4 — The questline framework adoption path is undesigned

**Open since:** 2026-08-25 (reframed 2026-08-29).
**Trigger type:** natural-coupling.
**Trigger:** questline #2 begins.

`LegendaryHytalePlugin.start()` still wires Stormseeker imperatively; the `QuestlineModule` SPI,
registry and `LegendaryWiring` have no callers outside their own tests.

What changed on 2026-08-25 is that the *route* was withdrawn.
`docs/architecture/questline-framework-adoption.md` is marked SUPERSEDED and its P1–P3 are
"discarded, not pending" — they were preconditions for a hand-rolled content spine that native
objectives replace. So this is not "execute a documented migration"; it is **design work with no
current design**, and the project `CLAUDE.md` pointed at the withdrawn route until 2026-08-29.

Element identity landed 2026-08-29 (`f8a26be`), which was the one piece that had to precede
questline #2 regardless, since it is a breaking `:core` constructor change once a second consumer
exists.

**If the trigger never fires:** acceptable. A framework with one questline is a framework nobody has
had to generalise, and generalising it against a single example is how the last design went stale.

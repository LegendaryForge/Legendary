# Legendary Project Documentation

Canonical design, architecture, and integration documentation for the Legendary project.

> **Index verified 2026-08-29** — every path below was existence-checked against the tree.
>
> The previous version of this file was dated **2026-02-04**. It indexed 3 of 29 documents, linked
> three paths that had since moved to `stormseeker/archive/`, and presented
> `repository-structure.md` as authoritative although that file already carries its own ⚠️ STALE
> banner. It described the five-repository layout that the 2026-08-24 consolidation removed.

---

## Where does this go?

**Enforced by `checkDocsOrganization`, wired into `check`.** It prints `DOCS_VERDICT:` — read that
line by name, never the exit code.

| If you have… | It goes in | Why there |
|---|---|---|
| **a fact about the engine** — an API, a signature, a measured value, an asset schema | [`integration/hytale-engine-api.md`](integration/hytale-engine-api.md), as a new row with its **tier** and **build** | an API fact outlives the design that found it; designs get superseded |
| **a fact about the fiction** — lore, naming, what exists in the world | [`setting/hytale-orbis-setting-brief.md`](setting/hytale-orbis-setting-brief.md) | lore is the authority on meaning; see `../CLAUDE.md` § "Lore first" |
| **a design or decision** | a new `superpowers/specs/YYYY-MM-DD-*.md` with a `**Status:` line | dated and append-only; the status is what stops it reading as current after it is superseded |
| **a plan to execute one** | a new `superpowers/plans/YYYY-MM-DD-*.md` | same |
| **a durable rule for every session** | [`../CLAUDE.md`](../CLAUDE.md) | it is the only document auto-loaded every session |
| **operating notes for the test server** | [`integration/live-server-testing.md`](integration/live-server-testing.md) | perishable, engine-specific, read at point of use |
| **something superseded** | leave it where it is, add a banner saying so | deletion loses the record of what was believed and when |

### The six rules

1. **Curated directories are indexed here** — `architecture/`, `integration/`, `setting/`,
   `stormseeker/` and this directory. A document nobody links to has no first reader.
2. **Dated directories are date-prefixed** — `superpowers/specs/` and `superpowers/plans/` must
   start `YYYY-MM-DD-`. That is what makes them discoverable without a hand-maintained index, which
   is why they are *not* required here: they accumulate indefinitely, and indexing each one would
   turn this file into a changelog.
3. **Relative links resolve.** This is the only rule that catches a defect introduced by a *correct*
   edit elsewhere — moving a file breaks every document pointing at it, and nothing else reports it.
4. **Every spec declares a `**Status:`.** Four specs once read as "decided" while describing a model
   superseded a day earlier; the only thing a direct reader saw was that stale framing.
5. **`integration/` and `setting/` documents carry a verification stamp** — which build or which
   date their claims were checked against. The setting brief asserted "there are no runes" as a
   verified gap: true of build `0.5.9`, false of `0.7.2`, and nothing on the page said which.
6. **Every directory under `docs/` is classified** as curated, dated, or exempt. Without this, adding
   a directory means adding documents no rule examines — which is exactly how `architecture/` sat
   unguarded in the guard's own first version.

**Why rules and not habits.** Every documentation failure recorded in this project was content in the
wrong *place*, not content missing, and each instance was fixed by hand while the next slipped the
same way. The four ⚠️ banners in this tree are that history. A rule that fires on every commit is
worth more than a convention that fires on whoever remembers it.

---

## Architecture (`architecture/`)

| Document | What it is |
|---|---|
| [ecs-principles.md](architecture/ecs-principles.md) | ECS architecture, host/runtime boundaries, multiplayer design. Sourced from the Hytale Canonical Modding Architecture v1.2; dated 2026-02-04. |
| [testing-strategy.md](architecture/testing-strategy.md) | Unit tests, harness tests, validation approach. Dated 2026-02-04. |
| [native-objectives-migration-cost.md](architecture/native-objectives-migration-cost.md) | **DECIDED 2026-08-25** — adopt Hytale's native objectives for the Stormseeker content spine, and what that cost. |
| [questline-framework-adoption.md](architecture/questline-framework-adoption.md) | **SUPERSEDED 2026-08-25 — do not execute P1–P3.** They are preconditions for a hand-rolled content spine that will no longer exist. Kept unedited because the reasons they went unexecuted became evidence for the decision above. What survives is its description of what `core`'s questline framework *is*. |
| [repository-structure.md](architecture/repository-structure.md) | **STALE** — describes the pre-consolidation five-repository layout. Superseded by the root `CLAUDE.md`. |

## Integration (`integration/`)

Everything in this directory is verified against **one game build and expires with it** — Hytale
ships every 2–6 weeks. Re-verify by executing, not by reading, after any launcher update.

| Document | Verified against | What it is |
|---|---|---|
| [hytale-engine-api.md](integration/hytale-engine-api.md) | Server `0.7.2`, **living** | **The accreting engine API reference — start here.** Per-row build stamps and evidence tiers (measured / compiled / javap / asset). The other three integration docs are dated snapshots; this one is updated a row at a time. |
| [hytale-status.md](integration/hytale-status.md) | Sources compiling in `mod/hytale/`, 2026-08-24 | Server API package structure and consumption. Written from imports that currently compile, not from JAR inspection. |
| [hytale-asset-packs.md](integration/hytale-asset-packs.md) | Server `0.5.9`, by running | How a mod ships content as JSON. Verified with a probe plugin, not inferred from class names. |
| [hytale-capability-audit.md](integration/hytale-capability-audit.md) | Server `0.5.9`, `javap` | Engine capability audit. **Unreliable per row** — signature inspection is a hypothesis; at least one row (`BlockAccessor`) is wrong for 0.6.1. |
| [live-server-testing.md](integration/live-server-testing.md) | Server `0.6.1`, by running | Operating notes for a live test server: harness, console TTY/FIFO, command forms, weather gating, asset and prefab traps, probe design. |

## Stormseeker (`stormseeker/`)

| Document | What it is |
|---|---|
| [stormseeker-canonical.md](stormseeker/stormseeker-canonical.md) | **The canonical questline document.** v4.0, narrative rewritten from canon, updated 2026-08-25. Start here. |
| `archive/narrative.md`, `archive/design.md`, `archive/quest-phases.md`, `archive/canon-alignment-recommendations.md` | Superseded by the canonical document above. Retained as the record of the pre-v4.0 design. |

## Setting (`setting/`)

| Document | What it is |
|---|---|
| [hytale-orbis-setting-brief.md](setting/hytale-orbis-setting-brief.md) | Orbis — enough of Hytale's world to write a questline *inside* it rather than beside it. The **fiction** is durable; its `[asset]` and `[gap]` verifications are **not** — they carry a build, and were taken against 0.5.9 (§3 and §6 re-verified against 0.7.2 on 2026-10-21). Re-measure before building on an unstamped claim. |

## Specs and plans (`superpowers/`)

Dated design records, newest last. Specs state what was decided and why; plans state how it was to
be executed. These are **historical records, not live instructions** — a spec is authoritative for
the decision it records, not for current state.

**Specs** — `2026-08-24` monorepo consolidation · `2026-08-24` build conventions and CI ·
`2026-08-25` Stormseeker narrative redesign · `2026-08-25` Act III residue literacy ·
`2026-08-27` residue network · `2026-08-28` residue density Circle peak ·
`2026-08-28` graded nexuses · `2026-08-29` six-element residue framework ·
`2026-08-30` N9 all crossings are nexuses

**Plans** — `2026-08-24` monorepo consolidation · `2026-08-24` build conventions and CI ·
`2026-08-24` module boundary realignment · `2026-08-25` Stormseeker Act II the trace ·
`2026-08-27` core residue network

---

## Authority hierarchy

When in doubt about what is true:

1. **Code in the repository** — source of truth for what is implemented.
2. **The root `CLAUDE.md`** — source of truth for structure, build, and workflow.
3. **These documents** — source of truth for what should be implemented, and for decisions.
4. **Chat history** — non-authoritative, disposable.

A document that contradicts the code is stale, and marking it so is more useful than deleting it.

---

## Maintenance

**When to update:** design documents when creative decisions change; architecture documents when
principles evolve; integration documents after any launcher update that invalidates them.

**What not to do:**

- **Don't record implementation progress here.** That is what git and the vault session-status doc
  are for. The previous version of this file carried a "Current Status" section dated 2026-02-04 —
  exactly what this rule forbids. It was removed rather than refreshed.
- **Don't delete superseded content.** Mark it `SUPERSEDED` or `STALE` with a pointer to what
  replaced it, as the architecture entries above do.
- **Don't leave a pointer behind when you move or supersede a document.** The stale index this file
  replaced was not caused by documents going unmaintained — every document it mis-described had been
  correctly updated. It was caused by nobody updating the thing that *points at* them.

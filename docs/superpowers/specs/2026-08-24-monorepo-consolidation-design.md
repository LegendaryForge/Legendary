# Hytale Monorepo Consolidation — Design

> **Date:** 2026-08-24
> **Status:** Approved, pending implementation plan
> **Scope:** Project B of four (see Decomposition below)

## Problem

The project has been dormant since 2026-02-17. On 2026-08-17 the Hytale launcher
auto-updated the game; the new `HytaleServer.jar` is compiled for Java 25
(class-file major 69) while the build targets Java 21 (major 65). Java 21's
compiler cannot read Java 25 class files, so every module touching the Hytale API
stopped compiling.

The codebase itself did not rot. Excluding the two Hytale-touching files,
LegendaryCore builds green with all tests passing. The outage was caused entirely
by an external dependency changing underneath a build that had no guard against it.

Investigating the outage surfaced structural problems that predate it and that
would make any repair fragile:

- **Four dependency mechanisms for one dependency.** Legendary resolves
  LegendaryCore via a git submodule at `vendor/`; LegendaryContent via
  `includeBuild("../LegendaryCore")`; LegendaryDogfood via `include(':LegendaryCore')`
  as a subproject; LegendaryHytale via `mavenLocal()`.
- **A coordinate mismatch that makes one of them inert.** `Legendary/settings.gradle.kts`
  substitutes `com.example:LegendaryCore` while `build.gradle.kts` requests
  `io.github.legendaryforge:LegendaryCore`. The substitution never matches, so
  resolution falls through to `mavenLocal()`. Legendary has been building against
  `~/.m2/.../LegendaryCore-0.0.0-SNAPSHOT.jar` **dated 2026-02-09** — an untracked,
  machine-local artifact. This is why Legendary appeared green while Core was red.
- **Submodule drift.** `Legendary/vendor/LegendaryCore` is pinned at `5b74207`
  (2026-01-30) while the sibling checkout is at `377ea94` (2026-02-09).
- **Three Java targets** across five repos: 21, 25, and unset — plus a
  contradicting `java_version=25` property in Core, whose actual toolchain is 21.
- **Package sprawl and template residue.** LegendaryContent has two package roots;
  LegendaryHytale has three across 12 files, including untouched upstream template
  code at `dev.hytalemodding.*` and an unmodified template `README.md`.
- **Three implementations of consumer-side contract testing**: `legendarycore-testmod`,
  `LegendaryDogfood`, and LegendaryContent's harness tests.

## Decomposition

This design covers **Project B only**. The full effort decomposes into four
projects with a dependency order:

| | Project | Depends on |
|---|---|---|
| A | Restore the build (toolchain, Error Prone, JDK pinning) | — |
| **B** | **Repo topology and dependency wiring (this document)** | **A green** |
| C | Realign documentation to reality (7 known contradictions) | B settled |
| D | Re-scope the roadmap (Phase 0/1/1.5 vs. persistence-first) | B settled |

A is mechanical and already diagnosed; it is included here as the first migration
step because B cannot be verified without it.

## Decisions

**LegendaryCore is a shared resource for the owner's future quest mods, not a
publicly consumed library.** Stormseeker is the first such mod; more are planned.
This makes the Core↔quest boundary load-bearing, but as a *module* boundary. The
*repository* boundary is what produced four dependency mechanisms and the
submodule drift. The two are separable, and conflating them caused most of the
current state.

**The five GitHub repositories will be consolidated.** History preservation via
subtree merge was considered and rejected: the archived repositories retain the
full record, and four interleaved histories are noisier to bisect than a clean
forward history.

**No published-artifact story for `core`.** No `maven-publish`, no
`API_STABILITY.md` ceremony. With all consumers in-tree, publishing adds
versioning overhead and buys nothing — and it is the mechanism that produced the
stale `~/.m2` jar. Re-addable the day a real external consumer appears.

## Target structure

```
Legendary/                          single repo, Gradle multi-project
├── settings.gradle.kts             includes all four modules
├── gradle/libs.versions.toml       one version catalog
├── gradle/gradle-daemon-jvm.properties
├── core/                           engine-agnostic foundation, ZERO Hytale imports
├── quests/stormseeker/             questline logic, engine-agnostic
├── platform/hytale/                the ONLY module importing com.hypixel.*
└── harness/                        consumer-side contract + scenario tests
```

Dependency edges, all plain `implementation(project(":core"))`:

```
platform/hytale ──> quests/stormseeker ──> core
harness ──────────────────────────────────> core (+ stormseeker)
```

### What moves

| Move | Files | Rationale |
|---|---|---|
| `LegendaryCorePlugin`, `ExampleCommand` → **deleted** | 2 | Template residue, not platform code — see below. These are the only Hytale imports in Core (verified: 2 of 136) |
| `dev.hytalemodding.*` → deleted | 3 | Untouched upstream template code |
| `LegendaryDogfood` + `legendarycore-testmod` + Content's harness tests → `harness/` | 44 test files | Three implementations of one idea |
| LegendaryContent's toy encounters (ToyLightning, ToyStorm) → `harness/` | 19 main | Test fixtures, not shipped content |

The Hytale API surface across the whole project is **12 files of 359**, of which
**5 are template residue to delete** (3 in LegendaryHytale, 2 in Core). The
remaining 7 constitute `platform/hytale/`. Everything else is engine-agnostic.

### LegendaryCore is a hollowed-out plugin template

An initial draft of this design proposed *relocating* Core's two Hytale-touching
files into `platform/hytale/`. Enumerating them for the implementation plan showed
that to be wrong: they are unmodified Hytale plugin-template scaffolding, the same
class as `dev.hytalemodding.*`.

- `LegendaryCorePlugin` wires **nothing** from Core's own API — no `CoreRuntime`,
  no `EncounterManager`. It logs `"Hello from %s"` and registers the template's
  `ExampleCommand`. Its javadoc is still the template's.
- `build.gradle.kts:14` — `description = ... ?: "A Hytale plugin template"`.
- `build.gradle.kts:103` — `relocate("com.google.gson", "com.yourplugin.libs.gson")`.
- `manifest.json` declares `IncludesAssetPack: true` beside a template
  `Example_Recipe.json`.

### Coordinate identities — measured, not inferred

An earlier draft asserted that `build.gradle.kts:12`
(`project.group = findProperty("pluginGroup") ?: "com.example"`) left the template
default in force and was the root cause of the coordinate mismatch. **That is
wrong.** `gradle.properties` sets `pluginGroup=io.github.legendaryforge.legendary`
and `pluginVersion=0.0.2`, so the fallback never applies. Verified via
`./gradlew properties`: `group: io.github.legendaryforge.legendary`,
`version: 0.0.2`.

The actual defect is that **Core carries two different identities**, and three
distinct coordinate strings are in play across the repos:

| Identity | Coordinates | Used by |
|---|---|---|
| Gradle project | `io.github.legendaryforge.legendary:LegendaryCore:0.0.2` | composite-build automatic substitution |
| Maven publication | `io.github.legendaryforge:LegendaryCore:0.0.0-SNAPSHOT` | `mavenLocal()` consumers |
| Hardcoded in substitution rules | `com.example:LegendaryCore` | `Legendary` and `LegendaryContent` settings scripts |

Consequences, both verified: `LegendaryContent` requests `com.example:LegendaryCore:1.0.0`
and substitutes the same string, so **its composite build works**. `Legendary`
requests `io.github.legendaryforge:LegendaryCore:0.0.0-SNAPSHOT` while substituting
`com.example:LegendaryCore`, so **nothing matches** and resolution silently falls
through to the stale `~/.m2` jar. A single set of `project(":core")` edges removes
all three identities at once.

### The toolchain defect

`gradle.properties` declares `java_version=25` with the comment *"The game is
built on Java 21 but actually runs on Java 25."* A grep across `build.gradle.kts`
and `buildSrc` shows **the property is never read**. The build script hardcodes
`JavaLanguageVersion.of(21)` and `options.release = 21`. The correct value was
written down and then ignored — which is why the August game update broke a build
whose own configuration file already named the right target.

Consequence, accepted deliberately: `core/` stops producing a deployable Hytale
plugin jar and becomes a pure library. This is safe because `LegendaryHytale`
already bundles Core via `implementation(...)`, so nothing deploys Core
independently today. It also makes the zero-`com.hypixel`-imports check
self-consistent — under the relocate-instead approach, that check would have
failed on the files just moved in.

**Deleted with them:** Core's `manifest.json`, `src/main/resources/Server/Item/Recipes/Example_Recipe.json`,
and the `com.example` / `com.yourplugin` / `"A Hytale plugin template"` defaults
in its build script.

### On LegendaryDogfood

The owner noted Dogfood was "set aside" and offered to delete it. Evidence argues
for adapting it instead. Its tests import only `core.api.encounter.*` — true
black-box contract tests — while Core's own 58 tests across 32 files are white-box
against `internal.encounter.*`. Two behaviors exist **only** in Dogfood:
participant↔spectator **role switching** and **dual-membership exclusion**. A grep
for `roleSwitch|switchRole|dualMembership` across Core's main and test sources and
all of LegendaryContent returns zero hits. Dogfood is therefore the only coverage
of the contract future quest mods will depend on.

### Corner-avoidance property

A second quest chain is `quests/<name>/`, depending on `core` and nothing else. It
cannot reach into Stormseeker, and it inherits the platform adapter for free.

## Migration sequence

`Legendary` becomes the monorepo root — most history (134 commits), already the
main mod, and the only repo whose branch carries unpushed work worth preserving
in place.

Each step is independently verifiable with a green build:

1. **Repair the build in place**, on the current 5-repo layout. A green baseline
   *before* restructuring makes any later breakage unambiguously caused by the move.
2. **Land the unpushed commits** — Legendary (5 ahead of `origin/main`) and
   LegendaryHytale (4 ahead, no upstream configured). Unpushed work plus a
   directory rewrite is how work gets lost.
3. **Restructure into modules**, one module per commit, each message citing source
   repo and SHA for provenance.
4. **Delete the submodule and `vendor/`**, switch to `project(":core")`.
5. **Purge:** `dev.hytalemodding` (3 files), `pr.md` ×2, `test-output.txt`,
   `gradlew21`, stale `build/` output.

**Repo disposition:** archive, do not delete, the four absorbed GitHub repos.
Archiving is free, reversible, and keeps history and merged PRs reachable with no
sync obligation. This is an outward-facing action on the owner's org and will be
prepared but not executed without explicit instruction.

**Rollback:** everything through step 5 is local until pushed. The four archived
repos plus `Legendary`'s pre-restructure SHA reconstruct the current state exactly.

## Build configuration

**One toolchain: Java 25**, replacing today's three stories. Hytale ships Java 25
class files; matching it is a constraint, not a preference.

**Daemon JVM pinned** via `gradle/gradle-daemon-jvm.properties`, generated by
`./gradlew updateDaemonJvm --jvm-version=25 --jvm-vendor=ADOPTIUM`. This retires
`gradlew21`. The system default `java` is 26; Gradle 9.2 cannot run on 26
(`Unsupported class file major version 70`), which today is papered over by a shim
holding a hardcoded machine-specific path. Daemon JVM criteria is committed and
machine-independent. (Verified: Gradle 9.2 ships the `updateDaemonJvm` task.)

**Error Prone 2.27.1 → 2.50.0.** Verified: 2.27.1 fails on JDK 25's javac with
`NoSuchFieldError: TypeTag.UNKNOWN`; 2.50.0 completes a full clean build with all
checks enabled.

**`gradle/libs.versions.toml`** as the single declaration point for JDK, Error
Prone, Spotless, shadow, and JUnit. JUnit is currently 5.10.2 in three repos and
5.10.0 in a fourth with nothing enforcing agreement; in a single build that drift
becomes silent.

**Hytale jar detection moves to `platform/hytale/`.** It currently lives in Core's
`build.gradle.kts` — the module that after this change has no business knowing
Hytale exists. The graceful "no install detected, skip" path moves with it; it is
what lets the other three modules build on a machine with no game installed.

**Hytale version guard (new).** The build reads a mutable external path
(`~/.var/app/com.hypixel.HytaleLauncher/.../HytaleServer.jar`) that the launcher
rewrites without warning; that is the root cause of the outage. The jar cannot be
pinned — it is 123MB. Instead the build reads the jar's class-file major version
and fails with an explicit message
(`Hytale updated to Java 25; build targets 21 — update the toolchain`) rather than
a 23-error cascade that reads like the project's own code broke.

**Not changing:** Gradle stays 9.2, Spotless/palantir formatting stays, shadow
stays. None are implicated, and bundling upgrades into a repair makes the repair
unverifiable.

## Verification

**Invariant: 187 tests must still execute and pass** (revised from 192 at the final fix wave — see below). Not merely "the
build is green" — a module that silently stops being wired into `check` is also
green. The count is what catches it.

Baseline measured 2026-08-24:

| Repo | Tests | Files |
|---|---|---|
| Legendary | 73 | 52 |
| LegendaryCore | 63 | 37 |
| LegendaryContent | 26 | 21 |
| LegendaryDogfood | 20 | 18 |
| LegendaryHytale | 0 | 0 |
| **Total** | **182** | **128** |

LegendaryCore's 63 comprises 58 in `src/test` plus 5 in `legendarycore-testmod`.

**The invariant is a runtime executed count, not a static annotation count — corrected
mid-execution 2026-08-24.** The table above counts `@Test` / `@ParameterizedTest`
annotations; the verification steps count executed test cases in JUnit XML. These are
different metrics. `LegendaryCore` contains one `@ParameterizedTest`
(`DefaultEncounterJoinPolicyTest`) which expands to **11** executed cases, so its 63
annotations produce **73** executed tests. No other repo contains a parameterized test,
so their static and runtime figures agree. **True runtime total: 192.**

This was caught when a Task 2 implementer reported 73 executed tests against a predicted
63 and flagged the delta as unexplained inference rather than accepting it. Had the
invariant stayed in annotation units, Task 13 would have measured 192 against an expected
182 and read as a defect — with the likely "fix" being to lower the expectation, which
would have destroyed the invariant's value precisely when it mattered.
An earlier draft of this document said 177/123 — it counted only top-level
`src/test` directories while the surrounding sentence claimed a repo-wide total,
missing the testmod source set. Recorded here because it is the same
count-from-a-narrower-scope error the invariant exists to catch.

Final post-migration distribution (192 during migration, 187 after the fix wave deleted dead scaffold):

| Module | Tests executed |
|---|---|
| `core` | 63 |
| `quests/stormseeker` | 73 |
| `harness` | 51 (Content 26 + Dogfood 20 + testmod 5) |
| `platform/hytale` | 0 |
| **Total** | **187** |

Separately, `Legendary/vendor/LegendaryCore` holds a **complete duplicate copy of
Core**, including 37 test files. It is excluded from the baseline above and is
deleted by the restructure.

Gate at each step, invoked bare with the verdict read from output rather than exit
status:

```
./gradlew clean build
```

An absent verdict line is a failure, not a pass. This is not theoretical: the
first build run during this investigation reported exit 0 on a run with 23 errors,
because it was piped to `tail`.

| Checkpoint | Assertion |
|---|---|
| After build repair (pre-restructure) | `LegendaryCore`, `Legendary`, `LegendaryDogfood` build; **146 of 192 tests** execute and pass. `LegendaryContent` and `LegendaryHytale` stay red for structural reasons Phase 2 removes — see Partial Baseline below |
| After each module move | Build green; cumulative test count matches expected running total |
| After restructure complete | 182 tests, one `./gradlew build`, zero `mavenLocal`/submodule/composite resolution |

**Negative assertion, automated:** `core/` must contain zero `com.hypixel.*`
imports, enforced by a check in `core/build.gradle.kts` that fails the build. This
is the property the restructure buys, and it will erode silently the first time
someone adds a convenience import. Same reasoning as the version guard: make the
boundary real rather than documented.

## Known gaps — flagged, not addressed here

- **`platform/hytale` has 0 tests across 12 files.** Simultaneously the
  least-tested module and the one most exposed to game churn — the August outage
  was in exactly this layer. Belongs in project D, not in a restructure.
- **One deprecation warning:** `setPermissionGroup(GameMode)` is deprecated and
  marked for removal in the current Hytale API. Non-blocking; worth a follow-up
  before it becomes an error in a future game update.
- **Seven documentation contradictions** remain (project C), including
  `.planning/PROJECT.md` declaring a Kotlin stack for a codebase with zero Kotlin
  files, and `Project_Dependencies.md` describing the submodule as a "vendored copy".


## Post-implementation correction: core's Hytale surface was under-enumerated

This document claimed core's Hytale coupling was **exactly 2 files**, derived by grepping
`com.hypixel` imports. The whole-branch review found a third site the grep could not see:
`core/src/main/java/.../core/internal/platform/hytale/` — 6 main + 3 test files whose
`package-info.java` reserves the package for *"bindings between the Hytale server/runtime
and LegendaryCore"*, and whose `HytalePlatformAdapter` javadoc calls itself a scaffold for
a *"later dedicated adapter phase."* It imports only core's own API and `java.util`, so it
was invisible to an import-based enumeration while being unambiguously Hytale-designated
by intent. Nothing referenced it; `platform/hytale` is the phase it was waiting for. It
was deleted, taking the invariant from 192 to 187.

**The generalisable point:** "2 files" was a measurement of imports reported as a claim
about coupling. The two are not the same, and the gap between them was 9 files. This is
the same shape as several other errors recorded in this plan's execution ledger — a proxy
measured, a conclusion stated about the thing.

## Post-implementation correction: the guard's blast radius

The design asserted that isolating platform code means a game update can break only one
module. That held for *source* coupling — the actual February-to-August failure — but not
for the version guard itself, which threw from top-level build-script code and therefore
failed configuration for **every** module. Verified: a jar with no `com/hypixel` entries
failed `:core:build` in 304ms. The throws were moved into a task action scoped to
`:platform:hytale`. Now the same bad jar leaves `:core:build` green while
`:platform:hytale:build` fails with the guard's message.

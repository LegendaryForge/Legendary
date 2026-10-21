# Hytale — Claude Code Project Notes

This file orients Claude Code sessions working in the Hytale project tree. The
workspace-level `~/Workspace/CLAUDE.md` covers the broader environment (MCP servers,
agent loop, Observation Protocol, Deferral Discipline).

> ## Launch Hytale sessions from this directory
>
> `~/Workspace/Projects/Hytale/Legendary` — the directory this file is in.
>
> Claude Code auto-loads `CLAUDE.md` from the working directory and every **ancestor** of it.
> A `CLAUDE.md` sitting in a *subdirectory* is **not** in context at session start. Measured
> 2026-10-21 with a two-run probe, one variable changed: launched from a parent directory only
> the parent's file was in context; launched from the child, both were.
>
> So a session started one level up at `Projects/Hytale/` does not load this file at all — the
> residue read-point below included. `~/Workspace/CLAUDE.md` still loads either way, because it
> is an ancestor of both, which is precisely what makes the failure hard to notice: the session
> looks oriented.
>
> A short stub at `Projects/Hytale/CLAUDE.md` exists to say so out loud if you launch from
> there. It is the only guard available, because **nothing reports a `CLAUDE.md` that was never
> loaded** — the failure is silent and reads as green.
>
> Starting here also points the harness's git context at **this** repository instead of the
> workspace repo, which is what every branch and PR instruction below already assumes.

> **Until 2026-10-21 this file lived at `Projects/Hytale/CLAUDE.md` and was tracked by no
> repository.** The workspace repo ignores `Projects/` wholesale (`.gitignore:11`) and
> `Legendary` began one directory below, so the project's highest-traffic orientation document
> sat in the gap between the two repos with no history, no diff and no recovery path — for the
> two months it held load-bearing content, an `rm` or a bad edit would have been unrecoverable,
> and `git status` in either enclosing repo reported a clean tree throughout. Filed as
> the 2026-08-29 `#debt` entry in the vault's `GameDev/Hytale/Hytale_Observations.md`.
> It is now versioned with the code it describes, so a guard added to `check` and the guards
> table below can change in one commit.

---

## Project overview

**One repository, one Gradle multi-project build.** `Projects/Hytale/Legendary` is the
sole active repository. **Trunk is `main`**; it is the only long-lived branch, and it is
protected. Work happens on short-lived branches cut from `main` and deleted on merge.

```
Legendary/
├── core/                 engine-agnostic foundation: primitives, encounter framework,
│                         questline framework (SPI, registry, objectives, runtime seams)
├── quests/stormseeker/   one questline; depends on core and nothing else
├── mod/hytale/           the ONLY module touching com.hypixel.* — entrypoint,
│                         commands, per-questline adapters
└── harness/              consumer-side contract tests
```

**Package paths mirror module paths** (since 2026-08-24): `:a:b` → `io.github.legendaryforge.legendary.a.b.*`.

| Module | Package root |
|---|---|
| `:core` | `…legendary.core.*` |
| `:quests:stormseeker` | `…legendary.quests.stormseeker.*` |
| `:mod:hytale` | `…legendary.mod.hytale.*` |
| `:harness` | `…legendary.harness.*` |

So a questline's package is derivable from its module path, and questline #2 has one obvious home.
Before the rename `:quests:stormseeker` owned `legendary.mod.*` — the word that names a *different*
module — and three of its segments (`mod`, `runtime`, `questline`) read as generic while every file
under them was Stormseeker-specific. That is what produced the duplicate `StormseekerProgressStore`
simple name: a questline-specific port in a generic-sounding package, colliding with a concrete
class in `mod/hytale`.

The boundary rule is mechanical: **`core` holds frameworks; `quests/<name>/` holds *uses*
of them.** The question to ask of any file is "does it know about a specific questline?"
That rule is enforced by the build, not by judgement — see the guards below.

Language is **Java 25** (353 Java files). The only Kotlin is Gradle build logic
(`*.gradle.kts` plus `buildSrc/src/main/kotlin/ModuleCoverageExtension.kt`).

**Session status doc:** `Knowledge/MainVault/GameDev/Hytale/Hytale_Session_Status.md` —
read this before starting Hytale work (workspace `CLAUDE.md` Session Initialization step 8).

---

## History — read this before trusting any older document

Until 2026-08-24 this project was **five separate repositories** (`Legendary`,
`LegendaryCore`, `LegendaryContent`, `LegendaryDogfood`, `LegendaryHytale`) sharing a
feature branch, with `LegendaryCore` reaching `Legendary` through a vendored submodule.
**None of that is true now.** The four other repos were absorbed into `Legendary` and are
**archived (read-only) on GitHub**; the submodule and `vendor/` directory are deleted.

Their **local working copies still sit under `Projects/Hytale/`** and are redundant with
the monorepo. They are the only local copies of that history, so deleting them is an
operator decision that has not been made. Do not read them as current state.

Documents written before 2026-08-24 describe the five-repo world and are wrong about
structure even where they remain useful about intent.

---

## Build

```bash
./gradlew build              # full build; guards hang off `check`
python3 scripts/test-census.py       # CENSUS_VERDICT line
python3 scripts/coverage-census.py   # COVERAGE_VERDICT line
```

**Read verdict lines by name (`grep CENSUS_VERDICT`), never by position.** Exit status and
line position are both fragile — this workspace has recorded five separate occasions where
something appended output after a verdict and the reader saw the wrapper instead.

Toolchain is pinned via committed `gradle/gradle-daemon-jvm.properties` (foojay, Temurin 25),
so a fresh clone provisions its own JDK.

**Running a live test server** — reusable harness, console TTY/FIFO setup, in-game command forms,
weather gating, and the prefab/asset traps: `docs/integration/live-server-testing.md`. It is dated
and expires with the game build; re-verify by executing after any launcher update.

### Configuration cache — clean, but do NOT turn it on by default

As of 2026-08-24 `./gradlew build --configuration-cache` runs with **0 problems**; all seven
guard tasks live in `buildSrc` as typed tasks. It is still **opt-in on purpose**, because
measurement says enabling it globally would make the normal build slower:

| Invocation | no config cache | with config cache |
|---|---|---|
| `build` (daemon, as a developer runs it) | **4.77s** | 5.12s |
| `:mod:hytale:compileJava --no-daemon` (the session-init probe) | 3.95s | **2.32s** |

The split is the daemon, not the cache. A warm daemon already holds the configured model in
memory, so the on-disk entry adds serialization cost and saves nothing. `--no-daemon`
re-configures from scratch every run, which is exactly what the cache eliminates.

So it is enabled **only** in `agent/health_check.py`'s drift probe, which runs `--no-daemon`
to avoid leaving a ~1GB daemon resident. Adding `org.gradle.configuration-cache=true` to
`gradle.properties` would be a regression for everyone else — and for CI, which gets a fresh
checkout and so never reuses an entry at all.

### Pushing

**`main` does not accept direct pushes.** GitHub branch protection on `main` has
`enforce_admins: true` and `allow_force: false`, so even the repo owner cannot push to it
directly — changes must arrive via a pull request. The PR needs **no approving review**
(`required_approving_review_count: 0`); the requirement is only that a PR exists.

So every change needs a branch:

```bash
git checkout main && git pull
git checkout -b <type>/<short-description>
# ... commit ...
git push -u origin <branch>
gh pr create --base main
git checkout main           # BEFORE the merge — see below
gh pr merge <n> --merge     # merge commit, not squash or rebase — see below
```

**Merge, never squash or rebase.** Squash collapses deliberate commit separation (e.g. a
mechanical reformat kept apart from a config change so it cannot conceal one); rebase
rewrites SHAs that are cited across the knowledge vault.

**Check out `main` before `gh pr merge`.** Run with the PR's own branch checked out — or from
inside a worktree — the merge itself succeeds but its `--delete-branch` cleanup fails, so the
remote branch survives the merge and nothing reports that as a failure. Eight for eight since
adopting the precondition, which is why it is stated as a step above rather than as advice.

**Branches are short-lived, and that is load-bearing.** Until 2026-08-24 this project used a
long-lived `feat/stormseeker-bridge`. Because `main` can only be updated by merging *into*
it, that branch diverged from trunk on every cycle — it accumulated the merge commits it had
never seen — and a routine `git merge --ff-only origin/main` eventually aborted. A branch cut
fresh from `main` per change cannot drift, because it starts even with trunk. The old branch
was deleted once it was a strict ancestor of `main` with zero unmerged commits.

A global pre-push hook at `~/.config/git/hooks` also blocks it client-side, before the
network call. Do not bypass either layer.

Note that `.git/hooks/pre-push` in this repo is a **dead Spotless hook**: `core.hooksPath`
points at the global hooks directory, which overrides repo-local hooks, and its
`SPOTLESS_EXECUTOR` path (`/home/stephaneb/Projects/Hytale/Legendary/gradlew`) is missing the
`Workspace/` segment and does not exist. It has never run. Spotless is enforced by `check`
instead.

### Build guards — all wired into `check`

| Guard | Asserts |
|---|---|
| `:core:checkNoPlatformImports` | no `com.hypixel.` under `core/src` |
| `:core:checkNoQuestlineDependency` | `:core` declares no dependency on any `:quests:*` (name-independent) |
| `:core:checkNoQuestlineImports` | secondary text heuristic only — matches the literal `legendary.quests.`; not authoritative. Correct **by construction** since the 2026-08-24 rename: package paths mirror module paths, so every `:quests:<name>` lives under `legendary.quests.<name>`. The old needle `legendary.mod.` matched only because Stormseeker happened to occupy that prefix, and would have matched nothing once a second questline arrived |
| `:mod:hytale:checkHytaleJarVersion` | game jar's class-file version is compatible; skipped when no jar present |
| `:mod:hytale:checkManifestEntrypoint` | `manifest.json`'s `Main` names a class that exists, with a matching `package` line. The manifest refers to the entrypoint **as a string**, so the compiler cannot see it — a renamed package leaves the build fully green and produces a mod that will not load. Reads sources, not classes, so unlike the jar-version guard it is **not** skipped without the game and is the only check giving CI real coverage of this module |
| `:mod:hytale:checkAssetPackIntegrity` | this module's shipped assets cannot silently override base-game ones. Hytale resolves an asset's id from its **filename**, and a `.lang` key we ship replaces the base game's text for every player on the server — both are designed engine features, invisible to the compiler, and both **fail green**. Enforced by requiring a Stormseeker-scoped prefix on every shipped asset filename and translation key; also checks that objective lines name objectives that exist and that referenced translation keys are defined. Reads files, not classes, so it is meaningful on CI where this module compiles nothing |
| `checkModuleCoverage` (all modules) | a module compiles its **whole** source set, unless it declares why not and that declaration's predicate holds. `FULL` is the only state needing nothing said; `PARTIAL`, `EXEMPT` and `EMPTY` all fail when undeclared (amended 2026-08-24 — originally only `EXEMPT` required a declaration, so moving every file out of a module, or adding one file to a declared module, each defeated the check while staying green) |

---

## The Hytale jar, and what CI can and cannot see

`mod/hytale` compiles against the installed game's `HytaleServer.jar`. When that jar is
absent, the module **excludes `**/hytale/**` from compilation — which is 100% of its
sources.** So on any machine without the game it compiles nothing and still builds green.

`checkModuleCoverage` exists for exactly this: `mod/hytale` declares
`incompleteCompilationAllowedWhen("no Hytale server jar in this environment")`, so the gap is
**reported as `EXEMPT` on every run** instead of passing silently, while an *undeclared*
zero-compile fails the build.

**CI (`.github/workflows/ci.yml`, added 2026-08-24) therefore does not cover `mod/hytale`.**
Runners never have the proprietary jar, so on every CI run that module is EXEMPT and
`checkHytaleJarVersion` is skipped. CI gives lint/format parity across four modules and
guarantees a *new* module cannot go dark undeclared. **It does not detect Hytale API drift.**
That class of failure — the `Vector3d` package move that went unnoticed for ~6 months, the
game auto-updating to a Java 25 jar — can only be caught by compiling against the real jar.

**That observation point now exists, outside this repo.** `agent/health_check.py` has a
`Hytale: API drift` row that runs `:mod:hytale:compileJava --no-daemon` on this machine at
session-init (added 2026-08-24; ~6s cold, ~0.5s when the jar has not changed). Because
`compileJava` dependsOn `checkHytaleJarVersion`, one target covers both drift classes. The
row reports `INFO`, never `PASS`, on a machine with no game jar — keyed on the build's own
`checkHytaleJarVersion SKIPPED` line, so it cannot report success for compiling nothing.

Read the bound honestly: **detection is tied to a session starting on the machine that has
the game.** That converts an unbounded latency into "next session here", which is a real
gain over "whenever someone decides to look" — but it is not continuous, and no CI runner
can ever do it. If this machine stops being the one with the game installed, the row goes
`INFO` and the coverage is gone; that is visible, not silent, which is the point.

---

## Questline framework: placed, not adopted

The questline framework (`QuestlineModule` SPI, registry, `LegendaryWiring`) lives in
`core` and is correctly designed — but **`LegendaryHytalePlugin.start()` does not use it**,
wiring Stormseeker imperatively instead. Its only callers repo-wide are its own tests.

**Adding questline #2 is therefore not yet one registration line.** The migration route that
`docs/architecture/questline-framework-adoption.md` describes — its P1–P3 — is **discarded, not
pending.** That document was superseded on 2026-08-25 when Hytale's native objectives were adopted
for the Stormseeker content spine; its own second line reads *"Do not execute P1–P3,"* because they
are preconditions for a hand-rolled spine that will no longer exist. See
`docs/architecture/native-objectives-migration-cost.md` for the decision. What survives from the
superseded document is its description of what `core`'s questline framework *is*.

So the adoption path is **undesigned, not merely unexecuted** — feature work, and its first step is
deciding how the framework meets native objectives at all.

Before building on any extension point here, grep its call sites. If the only callers are
its own tests, it is a design, not a mechanism.

---

## Residue network: direction changed, old specs not retired

**Read `Legendary/docs/superpowers/specs/2026-10-20-residue-terrain-derivation-design.md` before any
residue work.** The three committed residue specs (`2026-08-27-residue-network-design.md`,
`2026-08-29-six-element-residue-framework-design.md`,
`2026-08-30-n9-all-crossings-are-nexuses-design.md`) describe a **single-convergence seeded-walk**
model keyed to the **six essence items**. That direction was superseded in discussion on 2026-10-20
and nothing was retired, so those specs still read as current. They are not.

Three facts from the new document that are easy to re-derive wrongly:

- **Extent.** One convergence per world yields **zero nexuses beyond ~3 km** — measured, in the
  committed `scripts/residue-scans/out-chosen.txt` §7. Tuning cannot fix it; the model is one
  localised feature in an unbounded world.
- **Roster.** `:core` keys residue to essences (Fire, Ice, Life, Lightning, Void, Water). Hytale's
  six *runes* are Wind, Earth, Lightning, Water, Fire, Void, and `Server/Abilities/Elements/` ships
  exactly the five Gaia ones — verifiable from the jar, no lore source needed. **Lightning is in
  every roster, so Stormseeker is unaffected.** Element identity is a `:core` constructor argument
  with one consumer, so changing it is cheap until questline #2.
- **Void is an incursion, not a darkness.** Not native to Orbis, no reachable destination, modelled
  by the engine as a staged invasion event. A "Void = night and underground dark" idea was
  considered and **dropped**; do not re-propose it.

The new document is a **proposed direction, not an approved one** — its own status line says so. The
first task when residue resumes is a decision, not implementation, and its §9 ranks the open
questions. The top two (is sky exposure queryable positionally? is biome?) decide whether derivation
can be on-demand or must move to generation time, which is an architectural fork.

---

*Last updated: 2026-10-21 — moved into this repository from `Projects/Hytale/CLAUDE.md`,
where nothing version-controlled it; added the launch-directory section above, whose claim about
ancestor-only auto-loading is probe-measured rather than assumed. Previously 2026-10-20 — added
the residue-direction pointer above. Previously 2026-08-24:
rewritten after the five-repo → monorepo consolidation, the module boundary realignment, and the
addition of CI; supersedes the version describing five repositories and a vendored LegendaryCore.*

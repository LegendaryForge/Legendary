# Hytale — Claude Code Project Notes

This file orients Claude Code sessions working in the Hytale project tree. The
workspace-level `~/Workspace/CLAUDE.md` covers the broader environment (MCP servers,
agent loop, Observation Protocol, Deferral Discipline).

> ## One directory, and it is the repository root
>
> `~/Workspace/Projects/Hytale` — the directory this file is in, and the git repository root.
>
> ```bash
> git rev-parse --show-toplevel     # must print …/Projects/Hytale
> ```
>
> **Until 2026-10-21 this was two directories, and that was the defect.** The repository sat at
> `Projects/Hytale/Legendary` while `Projects/Hytale` — named after the project, and the directory
> the operator's shell history actually held — resolved git to the *workspace* repo. Three
> iterations of a launch guard were written, all on 2026-10-21, each improving detection; sessions
> 19, 20 and 21 began from the wrong directory regardless, because every guard is read **after**
> the wrong command has already run, and the wrong command lives in fish history where no document
> can reach it. The fourth fix moved the repository instead, so the recalled command became correct
> and the condition stopped existing.
>
> **Do not reintroduce a launch guard here.** There is nothing left to guard: the natural launch
> directory and the repository root are the same path. A guard whose condition cannot occur is read
> as a live hazard by the next session and costs it the time to disprove — which is the mistake the
> third iteration made in the opposite direction. The `@Legendary/CLAUDE.md` import stub in the
> parent directory is deleted for the same reason.
>
> Design: `docs/superpowers/specs/2026-10-21-hytale-git-context-structural-fix-design.md`.

> **This file has been at `Projects/Hytale/CLAUDE.md` twice, and only the second time is safe.**
> Before 2026-10-21 it sat at that exact path tracked by **no repository at all**: the workspace repo
> ignores `Projects/` wholesale (`.gitignore:11`) and `Legendary` began one directory below, so the
> project's highest-traffic orientation document fell into the gap between two repos with no history,
> no diff and no recovery path. For the two months it held load-bearing content an `rm` or a bad edit
> would have been unrecoverable, and `git status` in either enclosing repo reported a clean tree
> throughout. Filed as the 2026-08-29 `#debt` entry in the vault's
> `GameDev/Hytale/Hytale_Observations.md`.
>
> It is back at the same path and now tracked, because what moved was the **repository boundary**,
> not the file. That is the distinction worth keeping: the path was never the problem, and relocating
> the file (2026-10-21, PR #113) fixed the tracking while leaving the mislaunch untouched. Only
> moving the boundary fixed both.

---

## Project overview

**One repository, one Gradle multi-project build.** `Projects/Hytale` is the sole active
repository, and it is the repository root — see the section above. **Trunk is `main`**; it is
the only long-lived branch, and it is protected. Work happens on short-lived branches cut from `main` and deleted on merge.

```
Hytale/
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

Their **local working copies were deleted 2026-10-21** (operator decision, taken when the
repository relocated — they could not remain as nested untracked repos inside a repository
root). Everything in them was verified reachable from a remote first, with one exception:
`LegendaryContent`'s `stash@{0}` belonged to no branch and was pushed nowhere, so it is
preserved as a git bundle at
`Knowledge/MainVault/GameDev/Hytale/legendarycontent-stash-2026-02-08.bundle` in the
workspace repo — restored into a fresh repo and diffed before the deletion.

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
gh pr merge <n> --merge --delete-branch   # merge commit, not squash or rebase — see below
```

**Merge, never squash or rebase.** Squash collapses deliberate commit separation (e.g. a
mechanical reformat kept apart from a config change so it cannot conceal one); rebase
rewrites SHAs that are cited across the knowledge vault.

**Check out `main` before `gh pr merge`, and pass `--delete-branch`.** Two separate
requirements, and the recipe above now carries both. Run with the PR's own branch checked out — or
from inside a worktree — and the merge itself succeeds while its `--delete-branch` cleanup fails,
so the remote branch survives the merge and nothing reports that as a failure. The flag has to be
written out because this repo's `deleteBranchOnMerge` is **false**: GitHub deletes nothing on its
own, so a merge without it strands the branch even when the checkout precondition is satisfied.

**The recipe omitted `--delete-branch` until 2026-10-21.** So the block above produced the exact
outcome the paragraph beneath it warned about, and attributed it to the checkout precondition
instead — copy-paste that block with `main` correctly checked out and the branch still survives.
Derived from the PR record: **130 merged PRs (#1–#133), zero with a surviving head branch.** The
one branch that remains, `docs/hytale-deferral-queue`, belongs to the deliberately closed #92.
Read the 130 for what it is: it does not isolate `--delete-branch` at merge time, being equally
consistent with later cleanup by hand, and it spans both the five-repo era and the
long-lived-branch era.

**It replaces a claim that could not be audited at all.** This sentence previously read *"Eight
for eight since adopting the precondition"* — and `git log -S 'Eight for eight' -- CLAUDE.md`
returns exactly one commit, `6c3c495`, the commit that brought this file into the repository. The
eight was written and incremented across the two months this file was tracked by **no**
repository: no history, no diff, and no origin date for "since adopting" that can be recovered.
Not stale — unauditable by construction. It is the third document found on 2026-10-21 asserting
git state that nothing re-derives, after the session-init orientation head (stale: it described
the pre-relocation layout) and this recipe (wrong).

**`--delete-branch` destroys a stacked PR, and `gh` cannot undo it.** If PR *B* is based on
PR *A*'s branch, merging *A* with `--delete-branch` **closes** *B* rather than retargeting it:
deleting the base leaves *B* `CLOSED` and `CONFLICTING`. It is then unrecoverable through `gh` —
`gh pr edit --base` refuses (*"Cannot change the base branch of a closed pull request"*) and
`gh pr reopen` refuses because the base branch no longer exists. The branch and its commits are
untouched, so the work survives; what is lost is the PR record and its review thread.

Before merging a base branch that anything is stacked on, do **one** of:

```bash
gh pr list --base <base-branch>          # is anything stacked on it? check FIRST
gh pr edit <dependent> --base main       # retarget the dependent, THEN merge the base
# or: merge the dependent first, so nothing is stacked when the base goes
```

This happened on 2026-10-21 to PR #136, hours after `--delete-branch` was added to the recipe
above — by the same session that added it. #136 was a guard PR stacked on the fix it asserted,
which was correct authoring: against `main` it would have failed. It was reopened as #137 from the
same branch. Recorded here because the recipe above now carries a flag that silently destroys
stacked PRs, and the flag is still right — the alternative strands every branch, since
`deleteBranchOnMerge` is `false`.

**Branches are short-lived, and that is load-bearing.** Until 2026-08-24 this project used a
long-lived `feat/stormseeker-bridge`. Because `main` can only be updated by merging *into*
it, that branch diverged from trunk on every cycle — it accumulated the merge commits it had
never seen — and a routine `git merge --ff-only origin/main` eventually aborted. A branch cut
fresh from `main` per change cannot drift, because it starts even with trunk. The old branch
was deleted once it was a strict ancestor of `main` with zero unmerged commits.

A global pre-push hook at `~/.config/git/hooks` also blocks it client-side, before the
network call. Do not bypass either layer.

**Spotless is enforced by `check`, not by any hook.** `core.hooksPath` points at the global
hooks directory, so repo-local hooks in `.git/hooks/` are overridden and never run — worth
knowing before adding one there and expecting it to fire.

### Build guards — all wired into `check`

| Guard | Asserts |
|---|---|
| `:core:checkNoPlatformImports` | no `com.hypixel.` under `core/src` |
| `:core:checkNoQuestlineDependency` | `:core` declares no dependency on any `:quests:*` (name-independent) |
| `:core:checkNoQuestlineImports` | secondary text heuristic only — matches the literal `legendary.quests.`; not authoritative. Correct **by construction** since the 2026-08-24 rename: package paths mirror module paths, so every `:quests:<name>` lives under `legendary.quests.<name>`. The old needle `legendary.mod.` matched only because Stormseeker happened to occupy that prefix, and would have matched nothing once a second questline arrived |
| `:mod:hytale:checkHytaleJarVersion` | game jar's class-file version is compatible; skipped when no jar present |
| `:mod:hytale:checkManifestEntrypoint` | `manifest.json`'s `Main` names a class that exists, with a matching `package` line. The manifest refers to the entrypoint **as a string**, so the compiler cannot see it — a renamed package leaves the build fully green and produces a mod that will not load. Reads sources, not classes, so unlike the jar-version guard it is **not** skipped without the game. It was once the only check giving CI real coverage of this module; `checkAssetPackIntegrity` and `checkCommandRegistration` now do too |
| `:mod:hytale:checkAssetPackIntegrity` | this module's shipped assets cannot silently override base-game ones. Hytale resolves an asset's id from its **filename**, and a `.lang` key we ship replaces the base game's text for every player on the server — both are designed engine features, invisible to the compiler, and both **fail green**. Enforced by requiring a Stormseeker-scoped prefix on every shipped asset filename and translation key; also checks that objective lines name objectives that exist and that referenced translation keys are defined. Reads files, not classes, so it is meaningful on CI where this module compiles nothing |
| `:mod:hytale:checkCommandRegistration` | no two commands declare the same name, and the boot log names exactly the commands registered. `CommandManager.register` is a `Map.put` with the return value **popped** — no duplicate check, no warning — so two commands claiming one name is not an error: the last registration silently wins, and which one that is depends on the order of the `registerCommand` calls. Both halves had failed by 2026-10-21: `residueprobe` was claimed twice (the wanted probe won by luck of ordering), and the boot log listed 13 names against 15 registrations. Prints `COMMANDS_VERDICT:`. Reads sources, so meaningful on CI. All five rules mutation-tested |
| `checkDocsOrganization` (root) | `docs/` stays organised, in six mechanical rules: curated directories indexed in `docs/README.md`, `specs/`+`plans/` date-prefixed, **every relative link resolves**, every spec declares a `**Status:`, `integration/`+`setting/` docs carry a verification stamp, and **every directory is classified** so a new one cannot go unchecked. Prints `DOCS_VERDICT:`. Reads files, not classes, so it is meaningful on CI. All six were mutation-tested — introduced a violation of each and confirmed RED — because the guard was GREEN on first write, which proves nothing |
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

**Where any document goes is decided by a table, and enforced.** `docs/README.md` §
"Where does this go?" maps the kind of thing you have to the file it belongs in — engine fact, lore
fact, design, plan, durable rule, operating note, superseded thing — and states the six rules
`checkDocsOrganization` enforces. **Read it before adding or moving a document.** Added 2026-10-21,
because every documentation failure recorded in this project was content in the wrong *place* rather
than content missing: each instance was fixed by hand, none of the fixes were enforced, and the next
one slipped the same way.

**What we know about the engine goes in one place.** `docs/integration/hytale-engine-api.md` is the
living API reference — per-row build stamps and evidence tiers (**measured** / **compiled** /
**javap** / **asset**), updated a row at a time. Read it before investigating an engine capability,
and add to it when you establish one. The other three `docs/integration/` files are dated snapshots:
`hytale-capability-audit.md` is `0.5.9` with a known-wrong row and an un-run re-audit checklist, and
`live-server-testing.md` is `0.6.1` operating notes.

Created 2026-10-21 because engine findings had been accumulating **inside design specs and the lore
brief** instead — surfaces that get superseded (four residue specs were bannered as superseded that
same day) or that are stamped for a different purpose. An API fact outlives the design that
discovered it, so it needs a home that is not that design. If you verify something about the engine
while doing something else, that is the normal case and this is where it goes.

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

## Lore first — the jar and this mod both derive from it

**Hytale's lore is the authority on what things *are*. The jar is the authority on what the engine
*does*, and carries no authority about meaning.** When the two disagree about naming or taxonomy, lore
wins and our code changes to match — never the reverse. This mod is content for someone else's world,
so getting the fiction right is not decoration on top of the engineering; it is the requirement the
engineering serves.

Three corollaries, each of which has already been got wrong once:

1. **A jar read can tell you a list exists. It cannot tell you the list answers your question.**
   `Server/Abilities/Elements/` ships exactly five files matching the five Gaia elements — and they are
   `{"DamageCause": "X"}` damage types. That corroboration was treated as settling a roster question and
   it settled nothing. Asset agreement is necessary and nowhere near sufficient. The sharpest form of
   this: the game ships **four** overlapping elemental rosters and no two are the same list, so the jar
   cannot answer *"which elements does Hytale have"* at all — only *"keyed to what"*. They are tabulated
   in `docs/setting/hytale-orbis-setting-brief.md` §3 *"Four rosters, no two identical"*; read it before
   any design keys to an element.
2. **First-party lore outranks community wikis.** A concept-art title on Hytale.com or a `server.lang`
   description is primary; a fandom page restating it is a report, and wiki pages disagree in ways that
   change conclusions — `hytalewiki.org`'s Element page omits the essence framing entirely, which is
   where one wrong roster argument came from. Tag the tier when citing: see the provenance block in
   `docs/superpowers/specs/2026-10-20-residue-terrain-derivation-design.md`.
3. **Hypixel keep the lore deliberately open-ended**, to leave players and creators room for their own
   stories. So lore constrains *less* than a rulebook would: where it is silent we are free to invent,
   and should. The discipline is only that where it **does** speak, we follow it rather than inventing
   over it — and that we check which of those two situations we are in before designing, rather than
   after.

Worked example, 2026-10-21: the residue roster. `:core` keyed residue to the six shipped **essence
items**; a design argued it against **runes** and nearly renamed the API. Lore distinguishes three
layers the project had collapsed into two — **essence** (world-substance, *"five magic essences that
compose life on Orbis"*), **element** (natural force), and **rune** (the written character that
*represents* an element, and which Trorks, Fauns and three named characters also have). Runes are
notation, so they were never the roster. The word *essence* was right; the membership was not.

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

**Read `docs/superpowers/specs/2026-10-20-residue-terrain-derivation-design.md` before any
residue work.** The three committed residue specs (`2026-08-27-residue-network-design.md`,
`2026-08-29-six-element-residue-framework-design.md`,
`2026-08-30-n9-all-crossings-are-nexuses-design.md`) describe a **single-convergence seeded-walk**
model keyed to the **six essence items**. That direction was superseded in discussion on 2026-10-20
and nothing was retired, so those specs still read as current. They are not.

Three facts from the new document that are easy to re-derive wrongly:

- **Extent.** One convergence per world yields **zero nexuses beyond ~3 km** — measured, in the
  committed `scripts/residue-scans/out-chosen.txt` §7. Tuning cannot fix it; the model is one
  localised feature in an unbounded world.
- **Roster — revised 2026-10-21, and the earlier version of this bullet was wrong.** It read
  *"Hytale's six **runes** are Wind, Earth, Lightning, Water, Fire, Void … verifiable from the jar, no
  lore source needed."* Runes are not a roster — they are the written characters that *represent*
  elements, and factions and named characters have them too. And the jar does **not** settle it: the
  five `Server/Abilities/Elements/` files are `{"DamageCause": "X"}` damage types, so they corroborate
  a list without being one. See § "Lore first" above; this bullet is that section's worked example.
  **DECIDED 2026-10-21** — `docs/superpowers/specs/2026-10-21-residue-roster-decision.md`. The roster
  is the **cosmological five**: Fire, Water, Lightning, Wind, Earth, with Void handled separately as an
  incursion. The word *essence* stays; only membership changes. `:core` still keys to the six shipped
  **essence items** (Fire, Ice, Life, Lightning, Void, Water) — there is nothing to change in code, so
  this is a documentation and content decision. **Lightning is in every roster, so Stormseeker is
  unaffected.**

  Recorded as a **reversible modelling default, not a claim about Hytale's taxonomy.** Two things from
  that decision are load-bearing for anyone building on it:

  - **Keep `elementId` an opaque `ResourceId`. Do not add an enum, a list, a default, or validation.**
    That openness is what makes a roster change a documentation edit rather than a breaking API change
    plus a migration, and the game ships every 2–6 weeks while still adding elements. It has been
    misread as a gap before; it is the mechanism that makes the decision safe to take at all.
  - **A content count is evidence about what has been built, never about what the world is.** An
    argument weighing rosters by how many shipped creatures drop each essence was used to take this
    decision and then withdrawn: the game's two elemental layers are each incomplete in *opposite*
    directions — `Server/Abilities/Elements/` is complete at five (Wind and Earth included), while the
    essence *items* omit Wind and Earth and add Ice, Life and Void. Fire has twenty droppers because
    Zone 4 got built. Weighing that is measuring Hypixel's schedule.
- **The roster is NOT in `:core`, and this bullet said otherwise until 2026-10-21.** It read *"element
  identity is a `:core` constructor argument with one consumer, so changing it is cheap until questline
  #2"*, and the specs said the roster was *"baked into `:core`'s API"*. Verified by reading the code:
  `elementId` is an opaque `ResourceId` — **no enum, no list, no validation, no default** — and every
  `DefaultResidueNetwork(seed, elementId, params)` call in the repository is **in a test**. There are
  **zero** production consumers, not one: `NullCoreResidueNetwork` in `quests/stormseeker` is a
  decorator over an injected delegate and supplies no id. So the roster decision has **no code cost
  and no deadline** — "cheap now, breaking after questline #2" was predicated on a roster that does
  not exist in code. It is a documentation and future-content decision, and can be made whenever.
  This is the heuristic two sections below applied to residue instead of the questline framework:
  **grep the call sites; if the only callers are its own tests, it is a design, not a mechanism.**
- **Void is an incursion, not a darkness.** Not native to Orbis, no reachable destination, modelled
  by the engine as a staged invasion event. A "Void = night and underground dark" idea was
  considered and **dropped**; do not re-propose it.

**The direction was ADOPTED 2026-10-21** — `docs/superpowers/specs/2026-10-21-residue-terrain-derivation-adoption.md`.
The convergence star retires (measured: 0.00 nexuses/km² beyond 3 km, and tuning is exhausted); the
placed-nexus model is approved; §9 q1–q5 are closed. Three things from that decision bind anyone
building on it:

- **Derivation is pure `:core` code behind an injected terrain port, and no residue is authored into
  worldgen JSON.** That declines §7's two-implementations trap by construction. It also costs nothing
  real: `HytaleWorldGenProvider` — the live generator — has **zero** bytecode references to
  `builtin/hytalegenerator`, so V2's graph machinery is not callable at runtime anyway. Verified, not
  recalled.
- **Timing is a deployment choice, not an architecture one.** The derivation is deterministic, so
  on-demand-near-players (the only thing possible today) and generation-time precomputation into
  `PointManager` return the same answer; the second is a *cache*, not a second source of truth.
- **The first deliverable is `/residueprobe`, which places nothing.** The capability question is
  answered — lava, water, environment and exposure reads all compile against the real jar. What no jar
  read can answer is whether the scoring puts a Fire nexus *at* the lava lake, so the probe prints its
  opinion about ground you can walk to and judge. River-versus-ocean is not expressible at all, and
  Lightning over-reports until storm frequency reaches the port.

---

*Last updated: 2026-10-22 — § Pushing gained the stacked-PR hazard it had been missing: `--delete-branch`
**closes** a PR stacked on the branch being merged rather than retargeting it, and `gh` can undo neither
half (`pr edit --base` refuses on a closed PR, `pr reopen` refuses because the base is gone). That cost
PR #136 on 2026-10-21, hours after the same session added the flag to the recipe. The flag stays — without
it every branch is stranded, since `deleteBranchOnMerge` is `false` — so the note is a precondition to
check, not a reversal. Also this day: `scripts/live-probes/server-up.sh` now runs **both** the FIFO holder
and the server under `setsid`, because the non-expiring holder was still dying with its launching shell and
the server then read EOF and **kicked the connected player**; the script prints `HOLDER alive`, since the
holder's death is what ends a live session. `docs/integration/live-server-testing.md` had recorded that as
"presenting as a hung server" — the understatement that sent a reader after a hung console instead of a dead
holder — and, three lines below, still recommended `ss -ltn` for liveness, which `server-up.sh` already
documented as a **false negative** on a healthy server because Hytale's transport is QUIC. Both corrected;
both had failed by reporting absence. Verification stamp deliberately NOT bumped: these are harness and
documentation fixes, not a re-verification against a newer game build, and the doc remains stamped `0.6.1`.
Previously 2026-10-21 (fifth change that day) — § Pushing's copy-pasteable recipe gained the
`--delete-branch` it had always been missing, and the "Eight for eight" claim beneath it was
replaced by a derived 130-of-130 plus a note on what that figure does not prove. The recipe was
producing the failure its own next paragraph warned about, while that paragraph blamed the checkout
precondition; the claim could not be audited at all, because this file's entire history is the
commit that brought it into the repository. Found while answering an operator report of "git
issues" for which no symptom existed — what did exist was three documents asserting git state that
nothing re-derives. Earlier 2026-10-21 (fourth change that day) — the dead repo-local `.git/hooks/pre-push`
was deleted from disk and this file's paragraph describing it removed; what survives is the fact
that paragraph existed to convey, that `core.hooksPath` overrides repo-local hooks and Spotless is
enforced by `check`. The hook had never run, and its `SPOTLESS_EXECUTOR` path was wrong twice over
after the morning's relocation — a guard whose condition cannot occur reads as live to the next
reader, which is the same reason the launch blockquote was deleted rather than reworded earlier the
same day. Earlier 2026-10-21 (third change that day) — the repository moved from
`Projects/Hytale/Legendary` to `Projects/Hytale`, so the launch directory and the repository root
are the same path and the mislaunch condition no longer exists. The launch blockquote was deleted
rather than reworded, the four archived working copies were deleted (one stash bundled first), and
the History section's claim that they still exist locally was corrected. Earlier 2026-10-21 (second
change that day) — added § "Lore first" and corrected the
residue Roster bullet, which had argued the roster against runes and claimed the jar settled it
without a lore source; both were wrong. Earlier 2026-10-21 — moved into this repository from `Projects/Hytale/CLAUDE.md`,
where nothing version-controlled it; added the launch-directory section above, whose claim about
ancestor-only auto-loading is probe-measured rather than assumed. Previously 2026-10-20 — added
the residue-direction pointer above. Previously 2026-08-24:
rewritten after the five-repo → monorepo consolidation, the module boundary realignment, and the
addition of CI; supersedes the version describing five repositories and a vendored LegendaryCore.*

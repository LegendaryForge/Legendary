# Build conventions and CI — design

**Date:** 2026-08-24
**Status:** approved, not yet implemented
**Baseline:** `feat/stormseeker-bridge` @ `96b9fc2`

## Problem

Four build guards were added over the two preceding sessions —
`:core:checkNoPlatformImports`, `:core:checkNoQuestlineDependency`,
`:core:checkNoQuestlineImports`, and `:mod:hytale:checkHytaleJarVersion` — plus
`scripts/test-census.py`. Every one of them fires only if a developer runs it
locally. `.github/` contains exactly one file, `PULL_REQUEST_TEMPLATE.md`; there
is no CI.

The cost of that is not hypothetical. This project was broken from outside twice
without anyone noticing: `Vector3d` moved from `com.hypixel.hytale.math.vector`
to `org.joml` and the platform layer did not compile for roughly six months, and
the Hytale launcher auto-updated the game to a Java 25 jar on 2026-08-17 against
a build targeting Java 21. Both were found by a human deciding to look.

A second, narrower problem sits alongside it: Error Prone and Spotless-for-Java
are applied in `core` and `quests/stormseeker` and are **absent** from
`mod/hytale` and `harness`, so half the codebase is unlinted and unformatted by
policy that nothing states.

## The finding that shaped this design

`mod/hytale/build.gradle.kts:122-125` excludes `**/hytale/**` from `JavaCompile`
when the game jar is absent:

```kotlin
if (!hasHytaleServerJar) {
    exclude("**/hytale/**")
}
```

All 8 of that module's main sources live under
`io/github/legendaryforge/hytale/` — verified with
`find mod/hytale/src/main/java -name '*.java'`. The pattern therefore excludes
**100% of the module**. The module also has 0 test files.

So a stock GitHub-hosted runner, which cannot have the proprietary game jar,
would print `BUILD SUCCESSFUL` having compiled **nothing** in the only module
that touches `com.hypixel.*` — the exact surface that broke silently twice. CI
built naively would fail **green**, which is the failure shape this workspace has
now catalogued five separate times.

Rejected mitigation, recorded so it is not re-proposed: a checked-in **stub jar**
captures the API as of its generation date, so it detects *our code* drifting
from the API but not *the API drifting underneath us*. Both real incidents were
the latter. Only compiling against the current real jar catches those, and that
can only happen on a machine with the game installed.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | CI runs on GitHub-hosted runners and covers 3 of 4 modules. The gap is **declared and restated on every run**, and an *undeclared* zero-compile fails the build | The 4th module cannot be compiled without a proprietary jar, so CI must stay green — but the gap must be stated every time rather than inferred, and it must be impossible for a *new* module to go dark unnoticed |
| D1a | **Amended 2026-08-24 (operator decision):** a module with **no sources at all** also requires a declaration. Previously `EMPTY` was tested first and passed free | D1's own rationale — "impossible for a *new* module to go dark unnoticed" — was undercut by exempting the one state a brand-new module is in. Worse, it made the check defeatable by deleting the evidence: moving every source out of a module turned a FAIL-worthy condition into a green `0/0 EMPTY`, with build, guard and census all reporting success. Demonstrated by deleting `mod/hytale/src/main/java`. `zeroCompileAllowedWhen` now covers both ways a module can contribute nothing; read its name as *zero output*. A genuinely new module declares `{ true }` and says so on every run, which is the behaviour D1 asked for |
| D1b | **Amended 2026-08-24 (operator decision):** an **undeclared `PARTIAL`** fails too. `FULL` is now the only state requiring no declaration | `PARTIAL` let a *declared* module drift out of its own declaration. Adding one portable file to `mod/hytale` — a helper needing no game jar, so no reason to sit under the excluded package — flips it on any jar-less machine from `0/7 EXEMPT (no Hytale server jar in this environment)` to a bare `1/8 PARTIAL`: seven of eight sources stop compiling, the verdict stays green, and the `EXEMPT` line D1 promises on *every* run silently stops being printed. Renamed `zeroCompileAllowedWhen` → `incompleteCompilationAllowedWhen` in the same change; the old name had already been stretched once to cover `EMPTY`, and extending it to a partial compile would have made it false. One call site. Known residual: a single declaration covers all three shortfalls, so a module declared for "no SDK" will also bless a `PARTIAL` from an unrelated cause while its predicate holds |
| D2 | Triggers are `push` to `feat/stormseeker-bridge` and `pull_request` (unrestricted target) | `origin/main` is at `328ea6b` dated 2026-02-10, 35 commits behind and 0 ahead, and its tree still contains `.gitmodules`, `vendor/` and a single `src/`. CI there would gate a pre-consolidation tree. Reconciling `main` is a separate, deliberate decision and is explicitly **out of scope** here |
| D3 | Lint asymmetry is fixed now via a `buildSrc` convention plugin, not deferred | Measurement (below) showed the cost is one mechanical reformat and zero code changes |
| D4 | One convention plugin, not a base/library split, and not `subprojects {}` | All four modules are `java-library` and want the same five things; `subprojects {}` is cross-configuration Gradle is moving away from and breaks under isolated projects |
| D5 | `test-census.py`'s verdict changes from `total == EXPECTED` to `total >= EXPECTED` | As a strict equality it fails every PR that adds a test, which trains readers to ignore it. As a floor it becomes a ratchet: the test count can only go up |

## Measurement

Run against a dirty tree at `96b9fc2` with `core`'s exact lint config applied to
`harness` and `mod/hytale`, then reverted. Commands stated beside their results
so no figure here is inference.

| Question | Command | Result |
|---|---|---|
| Error Prone damage | `./gradlew :harness:compileJava :harness:compileTestJava :mod:hytale:compileJava :mod:hytale:compileTestJava` | **BUILD SUCCESSFUL** — 9 warnings, **0 errors** |
| Formatting damage | `./gradlew :harness:spotlessApply :mod:hytale:spotlessApply` then `git diff --shortstat` | **58 of 72 files**, 755 insertions / 824 deletions |
| Safe afterwards | `./gradlew build` then `python3 scripts/test-census.py` | **204 tests, 0 failures**, `CENSUS_VERDICT: GREEN` |

Error Prone is free because the config runs `-XepAllErrorsAsWarnings` and
promotes only `EqualsHashCode` and `MissingOverride` to ERROR; neither fires in
either module. The formatting change is mechanical — produced entirely by
`spotlessApply`, with no hand edits.

## Design

### Part 1 — `buildSrc` convention plugin

`buildSrc/src/main/kotlin/legendary.java-conventions.gradle.kts` absorbs what all
four modules share:

- Java toolchain from `libs.versions.java`
- `JavaCompile` encoding (UTF-8) and `options.release`
- Error Prone: enabled, `disableWarningsInGeneratedCode`,
  `-XepAllErrorsAsWarnings`, `EqualsHashCode:ERROR`, `MissingOverride:ERROR`
- Spotless Java: `palantirJavaFormat`, `removeUnusedImports`,
  `trimTrailingWhitespace`, `endWithNewline`
- `tasks.test { useJUnitPlatform() }`

Each module script keeps only its dependencies and genuinely module-specific
tasks. `mod/hytale` retains the shadow plugin, the Hytale install detection, and
`checkHytaleJarVersion`. `core` retains its three boundary guards. Expected
shape: `core` 145 → ~30 lines, `mod/hytale` 135 → ~95, `quests/stormseeker`
61 → ~15, `harness` 40 → ~15.

The reformat lands as its **own commit**, separate from the plugin commit, so a
755-line mechanical diff never conceals a semantic change.

### Part 2 — `checkModuleCoverage`

Registered by the convention plugin, wired into `check` so it runs on developer
machines as well as CI.

Per module it compares the count of `.java` files on disk under `src/main/java`
against the count `compileJava` actually consumed after excludes.

A naive "fail whenever `onDisk > 0 && compiled == 0`" is wrong: on a runner
without the game jar that is *always* true for `mod/hytale`, so CI would be
permanently red — and a permanently-red CI is ignored for the same reason a
silently-green one is. The rule must separate an **undeclared** zero from a
**declared, environment-dependent** one.

A module may therefore declare a predicate naming the condition under which zero
compiled sources is expected:

```kotlin
moduleCoverage {
    zeroCompileAllowedWhen("no Hytale server jar in this environment") {
        !hasHytaleServerJar
    }
}
```

Behaviour:

| Condition | Result |
|---|---|
| `compiled == 0`, `onDisk > 0`, no predicate declared | **FAIL** |
| `compiled == 0`, `onDisk > 0`, predicate declared and **false** | **FAIL** — the exemption cannot mask a real breakage |
| `compiled == 0`, `onDisk > 0`, predicate declared and true | report `EXEMPT (<reason>)`, do not fail |
| `compiled < onDisk` | report partial exclusion, do not fail |
| `onDisk == 0` | report, do not fail |

The second row is the load-bearing one: if the jar *is* present and the module
still compiles nothing, the exemption does not apply and the build fails. So the
declaration documents a known environmental gap without ever excusing a genuine
one.

It prints a `COVERAGE_VERDICT:` line naming each module's compiled/on-disk ratio
and its exemption state. Per-module detail on which of {Error Prone, Spotless,
tests} ran is not printed, and would now be redundant anyway: the convention
plugin applies the same checks to all four modules uniformly.

The verdict is a printed **line**, not an exit code, following the local rule
that exit status is positional and any wrapper swallows it. The line is
retrieved by name (`grep COVERAGE_VERDICT`), never by position.

Per local norm, the task is to be **observed firing** — deliberately run against
a module with sources excluded — before it is trusted.

### Part 3 — `.github/workflows/ci.yml`

```
on:
  push:     branches: [feat/stormseeker-bridge]
  pull_request:
```

`ubuntu-latest`. Steps: `actions/checkout`, `actions/setup-java` (Temurin 25,
which satisfies the committed foojay daemon pin from cache rather than
downloading), `gradle/actions/setup-gradle` for dependency caching, then
`./gradlew build --console=plain`, then `python3 scripts/test-census.py`.

The repo is public, so Actions minutes are free.

CI will honestly cover `core`, `quests/stormseeker` and `harness`.
`COVERAGE_VERDICT` will state `mod/hytale 0/8 EXEMPT (no Hytale server jar in
this environment)` on every run — green, but with the gap restated on every
build rather than discovered later.

### Part 4 — `test-census.py` ratchet

`green = total == EXPECTED and failed == 0` becomes
`green = total >= EXPECTED and failed == 0`, with the message wording updated to
say "at least". `EXPECTED` stays at 204 and stays env-overridable. The invariant
it was built for — the boundary realignment — is complete; as a floor it keeps
protecting against silent test loss without penalising test addition.

## Out of scope

- **Reconciling `origin/main`.** A pure fast-forward is available (35 behind, 0
  ahead) but it would publish the monorepo as this public repo's front door.
  That is a decision on its own merits, not a side effect of adding CI.
- **Tests for `mod/hytale`.** Its 8 files remain untested. Writing them runs into
  `CoreRuntime` having no headless implementation, which is separately deferred.
- **Adopting the questline framework.** Blocked on preconditions P1–P3 in
  `docs/architecture/questline-framework-adoption.md`.
- **Deleting the four archived repos' local working copies** under
  `Projects/Hytale/`.

## Success criteria

1. `./gradlew build` green, `204` tests, on this machine (which has the game
   installed, so all four modules compile).
2. On this machine, `COVERAGE_VERDICT` reports all four modules with Error Prone
   and Spotless applied and none exempt.
3. `checkModuleCoverage` observed **failing** before being trusted, in both
   directions: once on a module with sources but no declared predicate, and once
   on `mod/hytale` with the jar present but compilation forced empty — proving
   the exemption cannot mask a real breakage.
4. A CI run visible on GitHub for a push to `feat/stormseeker-bridge`, **green**,
   whose log contains `COVERAGE_VERDICT` stating `mod/hytale 0/8 EXEMPT` and the
   other three modules fully compiled.
5. `test-census.py` passes at 204 and still passes at 205; fails at 203.

# Build Conventions and CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lift shared Java build configuration into a `buildSrc` convention plugin applied by all four modules, add a `checkModuleCoverage` guard that makes an undeclared zero-compile fail, and add GitHub Actions CI to a project that has never had any.

**Architecture:** A single precompiled script plugin `legendary.java-conventions` in `buildSrc` owns the toolchain, compile options, Error Prone, Spotless and JUnit setup. Each module keeps only its dependencies and genuinely module-specific tasks. A per-module `checkModuleCoverage` task writes a small JSON and fails on undeclared zero-compile; `scripts/coverage-census.py` aggregates those files into one `COVERAGE_VERDICT:` line, mirroring the existing `scripts/test-census.py` idiom. CI runs `./gradlew build` plus both census scripts.

**Tech Stack:** Gradle 9.2.0, Kotlin DSL 2.2.20, Java 25 (Temurin), Error Prone 2.50.0, Spotless 8.2.0 with palantir-java-format 2.85.0, JUnit 5.10.2, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-24-build-conventions-and-ci-design.md`

**Baseline:** `feat/stormseeker-bridge` @ `8997f53`, tree clean, `./gradlew build` green at 204 tests.

## Global Constraints

Every one of these was established by a spike against this repo at `8997f53`, not by recollection. Ignoring any of them produces a build that does not configure.

- **`buildSrc/settings.gradle.kts` is REQUIRED.** Without it, `libs` is unresolved inside `buildSrc/build.gradle.kts` — observed failure: `Unresolved reference 'plugins'` at `libs.plugins.errorprone`. The version catalog is **not** automatically visible to buildSrc's own build script.
- **The type-safe `libs` accessor does NOT exist inside precompiled script plugins.** In `legendary.java-conventions.gradle.kts`, versions must be read at runtime via `extensions.getByType<VersionCatalogsExtension>().named("libs")`. The `libs.versions.x` form works fine in *module* scripts and must be left alone there.
- **Plugins are applied by id WITHOUT a version inside the convention plugin.** Versions come from `buildSrc/build.gradle.kts` dependencies.
- **The conversion is ALL-OR-NOTHING and must land in ONE commit.** Once buildSrc puts Error Prone and Spotless on the classpath, any remaining `alias(libs.plugins.errorprone)` or `alias(libs.plugins.spotless)` — in a module *or* in the root script — fails with `Error resolving plugin [...] the plugin is already on the classpath with an unknown version`. Observed twice during the spike. You cannot migrate one module at a time.
- **`alias(libs.plugins.shadow)` in `mod/hytale` stays as-is.** Shadow is not on buildSrc's classpath, so it does not conflict.
- **Java target is `25`**, sourced from `libs.versions.java`. Never hardcode it.
- **Verification is the full `./gradlew build`** — not a single module, not a single task. `check` is where the guards hang.
- **Read every verdict line by NAME, never by position.** Use `grep "CENSUS_VERDICT"` / `grep "COVERAGE_VERDICT"`. Do not use `tail -1`: this repo's `run_full_suite`-class gates have failed five separate times because something appended output after the verdict. Background-task harnesses append their own status line after the script's output.
- **Do not push.** Every task commits locally only. Pushing is a separate operator decision.
- **Do not touch `main`.** It is 35 commits behind and out of scope.

---

### Task 1: buildSrc convention plugin + all-module conversion

**Files:**
- Create: `buildSrc/settings.gradle.kts`
- Create: `buildSrc/build.gradle.kts`
- Create: `buildSrc/src/main/kotlin/legendary.java-conventions.gradle.kts`
- Modify: `build.gradle.kts` (root, 20 lines → 16)
- Modify: `core/build.gradle.kts` (145 → 96)
- Modify: `quests/stormseeker/build.gradle.kts` (61 → 11)
- Modify: `mod/hytale/build.gradle.kts` (135 → 118)
- Modify: `harness/build.gradle.kts` (40 → 12)
- Reformat: 58 files under `harness/src` and `mod/hytale/src`

**Interfaces:**
- Produces: a plugin id `legendary.java-conventions`, applied as `id("legendary.java-conventions")`. It configures the Java toolchain, `JavaCompile` encoding/release, Error Prone, Spotless-for-Java, and `useJUnitPlatform()`. Task 2 extends this same file.
- Note: the convention plugin adds `repositories { mavenCentral() }`, so modules must **drop** their own `repositories` block.

- [ ] **Step 1: Create `buildSrc/settings.gradle.kts`**

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```

- [ ] **Step 2: Create `buildSrc/build.gradle.kts`**

```kotlin
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(
        libs.plugins.errorprone.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" },
    )
    implementation(
        libs.plugins.spotless.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" },
    )
}
```

- [ ] **Step 3: Create `buildSrc/src/main/kotlin/legendary.java-conventions.gradle.kts`**

```kotlin
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("java-library")
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val javaVersion = libs.findVersion("java").get().requiredVersion.toInt()
val palantirVersion = libs.findVersion("palantir").get().requiredVersion

dependencies {
    "errorprone"(libs.findLibrary("errorprone-core").get())
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(javaVersion)) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = javaVersion
    options.errorprone.isEnabled.set(true)
    (options.errorprone as ErrorProneOptions).disableWarningsInGeneratedCode.set(true)
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-XepAllErrorsAsWarnings")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:EqualsHashCode:ERROR")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:MissingOverride:ERROR")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(palantirVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

- [ ] **Step 4: Verify buildSrc compiles on its own**

Run: `./gradlew :buildSrc:build --console=plain`
Expected: `BUILD SUCCESSFUL`. If you see `Unresolved reference 'plugins'`, Step 1 was skipped.

- [ ] **Step 5: Replace the root `build.gradle.kts`**

The `alias(...)` forms must go — buildSrc now supplies both plugins.

```kotlin
plugins {
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts", "*/*/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

- [ ] **Step 6: Replace `harness/build.gradle.kts`**

```kotlin
plugins {
    id("legendary.java-conventions")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":quests:stormseeker"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 7: Replace `quests/stormseeker/build.gradle.kts`**

```kotlin
plugins {
    id("legendary.java-conventions")
}

dependencies {
    api(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
```

- [ ] **Step 8: Rewrite the head of `core/build.gradle.kts`**

Replace everything from line 1 up to (but NOT including) `val checkNoPlatformImports by tasks.registering {` with the block below. The three guard tasks and their three `tasks.named("check") { dependsOn(...) }` lines are preserved **verbatim** — do not edit them.

```kotlin
import org.gradle.api.artifacts.ProjectDependency

plugins {
    id("legendary.java-conventions")
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jetbrains.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

```

- [ ] **Step 9: Edit `mod/hytale/build.gradle.kts` — four surgical removals**

Keep the `import java.util.zip.ZipFile` line, the install-detection block, `hytaleJarJavaVersion`, `targetJava`, the lifecycle log, `checkHytaleJarVersion`, the `dependencies` block, and the `tasks.named("compileJava")` block. Make exactly these four changes:

1. Replace the `plugins` block and delete the `repositories` block that follows it:

```kotlin
plugins {
    id("legendary.java-conventions")
    alias(libs.plugins.shadow)
}
```

2. Delete this block entirely (the convention plugin now sets the toolchain):

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJava))
    }
}
```

3. Delete this block entirely (the convention plugin now sets it):

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

4. Strip the two lines the convention plugin now owns from the `JavaCompile` block, leaving only the exclude:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    // Without the Hytale jar these cannot compile; skip them so the rest of the
    // module still builds on a machine with no game installed.
    if (!hasHytaleServerJar) {
        exclude("**/hytale/**")
    }
}
```

- [ ] **Step 10: Run the build and confirm it fails ONLY on formatting**

Run: `./gradlew build --console=plain 2>&1 | tail -40`
Expected: `BUILD FAILED`, and every reported problem is a Spotless violation ending in `Run './gradlew spotlessApply' to fix all violations.`

This failure is expected and correct — it is Spotless seeing `harness` and `mod/hytale` for the first time. If you see `Error resolving plugin`, a version-bearing `alias(...)` survived somewhere; find it and remove the version.

- [ ] **Step 11: Apply the formatter**

Run: `./gradlew spotlessApply --console=plain`
Expected: `BUILD SUCCESSFUL`.

Then confirm the scope matches the spec's measurement:

Run: `git diff --shortstat -- harness/src mod/hytale/src`
Expected: `58 files changed, 755 insertions(+), 824 deletions(-)` — treat a materially different number as a signal that the Spotless config differs from `core`'s, and stop.

- [ ] **Step 12: Verify the whole build is green**

Run: `./gradlew build --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

Run: `python3 scripts/test-census.py | grep CENSUS_VERDICT`
Expected: `CENSUS_VERDICT: GREEN | 204 tests | 0 failures | ...`

- [ ] **Step 13: Commit the reformat alone, first**

Committing formatting separately keeps a 755-line mechanical diff from concealing a config change. This commit is green in isolation: the pre-buildSrc build had no Spotless check on these two modules, so reformatted sources build identically under it.

```bash
git add -- harness/src mod/hytale/src
git commit -m "style: apply palantir formatting to harness and mod/hytale

Mechanical output of ./gradlew spotlessApply, no hand edits. These two
modules were never covered by Spotless; the next commit brings them under
the shared convention plugin, which requires them to already be formatted."
```

- [ ] **Step 14: Commit the build configuration**

```bash
git add buildSrc build.gradle.kts core/build.gradle.kts \
        quests/stormseeker/build.gradle.kts mod/hytale/build.gradle.kts \
        harness/build.gradle.kts
git commit -m "build: extract shared Java config into a buildSrc convention plugin

All four modules now apply legendary.java-conventions, which owns the
toolchain, compile options, Error Prone, Spotless and JUnit setup. This
closes the asymmetry where Error Prone and Spotless ran in core and
quests/stormseeker but not in mod/hytale or harness.

The conversion is necessarily atomic: once buildSrc puts the plugins on
the classpath, any surviving version-bearing alias() fails to resolve.

core 145->96, quests 61->11, mod/hytale 135->118, harness 40->12."
```

---

### Task 2: `checkModuleCoverage` guard

**Files:**
- Create: `buildSrc/src/main/kotlin/ModuleCoverageExtension.kt`
- Modify: `buildSrc/src/main/kotlin/legendary.java-conventions.gradle.kts` (append)
- Modify: `mod/hytale/build.gradle.kts` (add the exemption declaration)
- Create: `scripts/coverage-census.py`

**Interfaces:**
- Consumes: the `legendary.java-conventions` plugin from Task 1.
- Produces: a `moduleCoverage { zeroCompileAllowedWhen(reason) { predicate } }` DSL block; a per-module `checkModuleCoverage` task wired into `check`; a per-module `build/module-coverage.json`; and `scripts/coverage-census.py` printing a single `COVERAGE_VERDICT:` line.

- [ ] **Step 1: Create the extension class**

A precompiled script plugin cannot cleanly declare top-level classes, so this lives in its own Kotlin file.

```kotlin
// buildSrc/src/main/kotlin/ModuleCoverageExtension.kt
abstract class ModuleCoverageExtension {
    internal var exemptionReason: String? = null
    internal var exemptionPredicate: (() -> Boolean)? = null

    /**
     * Declares the condition under which this module legitimately compiles zero
     * sources. If the predicate is false at execution time the exemption does NOT
     * apply and checkModuleCoverage fails — a declaration can document a known
     * environmental gap but can never excuse a real breakage.
     */
    fun zeroCompileAllowedWhen(reason: String, predicate: () -> Boolean) {
        exemptionReason = reason
        exemptionPredicate = predicate
    }
}
```

- [ ] **Step 2: Append the task to the convention plugin**

Append to `buildSrc/src/main/kotlin/legendary.java-conventions.gradle.kts`:

```kotlin
val moduleCoverage = extensions.create<ModuleCoverageExtension>("moduleCoverage")

val checkModuleCoverage by tasks.registering {
    group = "verification"
    description =
        "Fails if this module has Java sources on disk but compiles none of them, " +
        "unless a moduleCoverage exemption is declared AND its predicate holds."
    outputs.upToDateWhen { false }
    doLast {
        val srcDir = file("src/main/java")
        val onDisk =
            if (srcDir.exists()) fileTree(srcDir) { include("**/*.java") }.files.size else 0
        val compiled = (tasks.getByName("compileJava") as JavaCompile).source.files.size
        val reason = moduleCoverage.exemptionReason
        val exempt = moduleCoverage.exemptionPredicate?.invoke() ?: false

        val state =
            when {
                onDisk == 0 -> "EMPTY"
                compiled == 0 && reason == null -> "FAIL"
                compiled == 0 && !exempt -> "FAIL"
                compiled == 0 -> "EXEMPT"
                compiled < onDisk -> "PARTIAL"
                else -> "FULL"
            }

        val report = layout.buildDirectory.file("module-coverage.json").get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            """{"module":"$path","onDisk":$onDisk,"compiled":$compiled,""" +
                """"state":"$state","reason":${if (reason == null) "null" else "\"$reason\""}}""",
        )

        if (state == "FAIL") {
            throw GradleException(
                buildString {
                    appendLine("$path has $onDisk Java source file(s) but compiled 0 of them.")
                    if (reason == null) {
                        appendLine("No moduleCoverage exemption is declared for this module.")
                        appendLine("Either fix the build so sources compile, or declare:")
                        appendLine("  moduleCoverage { zeroCompileAllowedWhen(\"why\") { condition } }")
                    } else {
                        appendLine("An exemption is declared (\"$reason\") but its predicate is FALSE,")
                        appendLine("so the exemption does not apply. This is a real breakage.")
                    }
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkModuleCoverage) }
```

- [ ] **Step 3: Declare the exemption in `mod/hytale/build.gradle.kts`**

Add this block **immediately after** the line `val hasHytaleServerJar = hytaleHome.isNotBlank() && hytaleServerJar.exists()`:

```kotlin
moduleCoverage {
    zeroCompileAllowedWhen("no Hytale server jar in this environment") { !hasHytaleServerJar }
}
```

Placement matters. Kotlin script top-level `val`s can compile to locals, and a lambda that captures one **before its declaration** is a compile error (`Variable 'hasHytaleServerJar' must be initialized`). Putting the block after the declaration avoids the question entirely — do not "tidy" it up next to the `plugins` block.

- [ ] **Step 4: Verify the guard passes on a healthy build**

Run: `./gradlew build --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL` (this machine has the game installed, so all four modules compile fully).

Run: `cat mod/hytale/build/module-coverage.json`
Expected: `{"module":":mod:hytale","onDisk":8,"compiled":8,"state":"FULL","reason":"no Hytale server jar in this environment"}`

- [ ] **Step 5: Observe the guard FAIL — direction 1, no exemption declared**

Per local norm, a guard is not trusted until it has been seen firing. Temporarily add to `harness/build.gradle.kts`:

```kotlin
tasks.withType<JavaCompile>().configureEach { exclude("**/*.java") }
```

Run: `./gradlew :harness:check --console=plain 2>&1 | tail -12`
Expected: `BUILD FAILED` with `:harness has 64 Java source file(s) but compiled 0 of them.` and `No moduleCoverage exemption is declared for this module.`

Then **remove** that temporary line.

- [ ] **Step 6: Observe the guard FAIL — direction 2, exemption declared but predicate false**

This is the load-bearing case: it proves a declared exemption cannot mask a real breakage. Temporarily add to `mod/hytale/build.gradle.kts`, after the existing `tasks.withType<JavaCompile>` block:

```kotlin
tasks.withType<JavaCompile>().configureEach { exclude("**/*.java") }
```

The game jar IS present on this machine, so `hasHytaleServerJar` is true and the predicate `{ !hasHytaleServerJar }` is false.

Run: `./gradlew :mod:hytale:check --console=plain 2>&1 | tail -12`
Expected: `BUILD FAILED` with `An exemption is declared ("no Hytale server jar in this environment") but its predicate is FALSE,` and `so the exemption does not apply. This is a real breakage.`

Then **remove** that temporary line.

- [ ] **Step 7: Verify the build is green again**

Run: `./gradlew build --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

Run: `grep -rn 'exclude("\*\*/\*.java")' --include=*.kts .`
Expected: **no output**. Both temporary probe lines from Steps 5 and 6 must be gone; a surviving one silently disables a module's compilation.

Run: `git status --porcelain`
Expected: modifications to `buildSrc/` and `mod/hytale/build.gradle.kts` only. (`scripts/coverage-census.py` does not exist yet — it arrives in Step 8.)

- [ ] **Step 8: Create `scripts/coverage-census.py`**

```python
#!/usr/bin/env python3
"""Aggregates per-module coverage reports into one verdict LINE.

Mirrors scripts/test-census.py: the verdict is content, not an exit code,
because exit status is positional and any wrapper swallows it. Retrieve it
with `grep COVERAGE_VERDICT`, never with `tail -1`.
"""
import json
import glob
import sys

MODULES = ["core", "quests/stormseeker", "mod/hytale", "harness"]

rows = []
missing = []
for module in MODULES:
    matches = glob.glob(f"{module}/build/module-coverage.json")
    if not matches:
        missing.append(module)
        continue
    with open(matches[0], encoding="utf-8") as handle:
        rows.append(json.load(handle))

for row in rows:
    suffix = f"  EXEMPT ({row['reason']})" if row["state"] == "EXEMPT" else ""
    print(f"{row['module']:22} {row['compiled']:3}/{row['onDisk']:<3} {row['state']}{suffix}")
for module in missing:
    print(f"{module:22} (no report -- run ./gradlew check)")

failed = [r for r in rows if r["state"] == "FAIL"]
green = not failed and not missing
exempt = sum(1 for r in rows if r["state"] == "EXEMPT")
print(
    f"COVERAGE_VERDICT: {'GREEN' if green else 'RED'} | "
    f"{len(rows)}/{len(MODULES)} modules reported | {exempt} exempt | {len(failed)} failing"
)
sys.exit(0 if green else 1)
```

- [ ] **Step 9: Run the census**

Run: `python3 scripts/coverage-census.py | grep COVERAGE_VERDICT`
Expected: `COVERAGE_VERDICT: GREEN | 4/4 modules reported | 0 exempt | 0 failing`

- [ ] **Step 10: Commit**

```bash
git add buildSrc/src/main/kotlin/ModuleCoverageExtension.kt \
        buildSrc/src/main/kotlin/legendary.java-conventions.gradle.kts \
        mod/hytale/build.gradle.kts scripts/coverage-census.py
git commit -m "build: add checkModuleCoverage guard against silent zero-compile

mod/hytale excludes **/hytale/** when the game jar is absent, and all 8 of
its sources live under that path -- so a runner without the game compiles
nothing there and still reports BUILD SUCCESSFUL. CI built naively would
fail green.

checkModuleCoverage fails when a module has sources but compiles none,
unless an exemption is declared AND its predicate holds. A declared
exemption whose predicate is false still fails, so it documents a known
environmental gap without ever excusing a real breakage.

Observed firing in both directions before being trusted."
```

---

### Task 3: `test-census.py` ratchet

**Files:**
- Modify: `scripts/test-census.py:46-49`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: a census that passes at or above `EXPECTED` instead of only at exactly `EXPECTED`.

- [ ] **Step 1: Change the comparison**

In `scripts/test-census.py`, replace:

```python
green = total == EXPECTED and failed == 0
print(f"{'TOTAL':22} tests={total:4} failures+errors={failed}  (expected {EXPECTED})")
```

with:

```python
green = total >= EXPECTED and failed == 0
print(f"{'TOTAL':22} tests={total:4} failures+errors={failed}  (expected at least {EXPECTED})")
```

- [ ] **Step 2: Update the module docstring**

Replace the line `The EXPECTED-test invariant (see EXPECTED below) is load-bearing during the` and the line following it with:

```
EXPECTED is a FLOOR, not an equality: the count may rise freely but must never
fall. Strict equality failed every commit that added a test, which trains
readers to ignore the verdict -- the precise failure this gate exists to avoid.
```

- [ ] **Step 3: Verify it passes at the current count**

Run: `python3 scripts/test-census.py | grep CENSUS_VERDICT`
Expected: `CENSUS_VERDICT: GREEN | 204 tests | 0 failures | ...`

- [ ] **Step 4: Verify it passes ABOVE the floor**

Run: `EXPECTED_TESTS=203 python3 scripts/test-census.py | grep CENSUS_VERDICT`
Expected: `CENSUS_VERDICT: GREEN | 204 tests | ...` — 204 exceeds a floor of 203.

- [ ] **Step 5: Verify it still FAILS below the floor**

Run: `EXPECTED_TESTS=205 python3 scripts/test-census.py | grep CENSUS_VERDICT`
Expected: `CENSUS_VERDICT: RED | 204 tests | ...` — the ratchet still catches test loss.

- [ ] **Step 6: Commit**

```bash
git add scripts/test-census.py
git commit -m "test: make the census a ratchet rather than an equality

EXPECTED becomes a floor. As strict equality the census failed every commit
that added a test, which trains readers to ignore the verdict -- the exact
failure mode it exists to prevent. As a floor it still catches silent test
loss while permitting test growth, which is what CI needs."
```

---

### Task 4: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `./gradlew build` (Task 1), `scripts/coverage-census.py` (Task 2), `scripts/test-census.py` (Task 3).
- Produces: a CI run on pushes to `feat/stormseeker-bridge` and on all pull requests.

- [ ] **Step 1: Create `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [feat/stormseeker-bridge]
  pull_request:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      # Satisfies the committed foojay daemon pin (gradle/gradle-daemon-jvm.properties,
      # toolchainVersion=25 vendor=ADOPTIUM) from the runner cache instead of
      # downloading a JDK on every run.
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'

      - uses: gradle/actions/setup-gradle@v4

      - name: Build
        run: ./gradlew build --console=plain

      # Runners cannot have the proprietary Hytale server jar, so mod/hytale
      # compiles zero sources here and reports EXEMPT. That gap is restated on
      # every run rather than inferred. An UNDECLARED zero-compile fails the
      # build inside ./gradlew build above.
      - name: Module coverage census
        run: python3 scripts/coverage-census.py

      - name: Test census
        run: python3 scripts/test-census.py
```

- [ ] **Step 2: Verify the workflow is valid YAML**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml')); print('YAML OK')"`
Expected: `YAML OK`

- [ ] **Step 3: Simulate the runner's module-coverage outcome locally**

CI has no game jar. Reproduce that state to confirm the census reports EXEMPT rather than failing:

Run: `./gradlew build -Phytale_home= --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

Run: `python3 scripts/coverage-census.py | grep COVERAGE_VERDICT`
Expected: `COVERAGE_VERDICT: GREEN | 4/4 modules reported | 1 exempt | 0 failing`

Run: `python3 scripts/coverage-census.py | grep 'mod:hytale'`
Expected: a line showing `0/8   EXEMPT (no Hytale server jar in this environment)`

If instead the build FAILS here, the exemption predicate is not seeing the overridden property — fix that before committing, because this is exactly the state every CI run will be in.

- [ ] **Step 4: Restore the normal build**

Run: `./gradlew build --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

Run: `python3 scripts/coverage-census.py | grep COVERAGE_VERDICT`
Expected: `COVERAGE_VERDICT: GREEN | 4/4 modules reported | 0 exempt | 0 failing`

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions build for feat/stormseeker-bridge and PRs

This project has had no CI. Every guard added over the last three sessions --
checkNoPlatformImports, checkNoQuestlineDependency, checkHytaleJarVersion,
checkModuleCoverage, and both census scripts -- fired only if a developer ran
them locally. The build was broken from outside twice without anyone noticing:
Vector3d moved packages and went undetected for roughly six months, and the
game auto-updated to a Java 25 jar against a Java 21 build.

Triggers are the feature branch and pull requests. main is 35 commits behind
with a pre-consolidation tree, so gating it would test nothing; reconciling it
is a separate decision."
```

- [ ] **Step 6: Report, do not push**

Print the final state and stop:

```bash
git log --oneline 8997f53..HEAD
git status --porcelain
```

Pushing is an operator decision and is explicitly out of scope for this plan. Report the commit list and await instruction.

---

## Verification summary

After Task 4, all of the following must hold on this machine:

| Check | Command | Expected |
|---|---|---|
| Build green | `./gradlew build --console=plain 2>&1 \| tail -5` | `BUILD SUCCESSFUL` |
| Test count | `python3 scripts/test-census.py \| grep CENSUS_VERDICT` | `CENSUS_VERDICT: GREEN \| 204 tests \| 0 failures` |
| Coverage, game present | `python3 scripts/coverage-census.py \| grep COVERAGE_VERDICT` | `GREEN \| 4/4 modules reported \| 0 exempt \| 0 failing` |
| Coverage, game absent | `./gradlew build -Phytale_home=` then census | `GREEN \| 4/4 modules reported \| 1 exempt \| 0 failing` |
| No stray probes | `grep -rn 'exclude("\*\*/\*.java")' --include=*.kts .` | no output |
| Nothing pushed | `git status -sb \| head -1` | shows `ahead` of origin |

# Hytale Monorepo Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the build broken by the 2026-08-17 Hytale update, then consolidate five repositories into one Gradle multi-project with a compiler-enforced platform boundary.

**Architecture:** Phase 1 repairs the build in place on the current five-repo layout, establishing a green baseline so that any later breakage is unambiguously caused by the restructure. Phase 2 collapses the five repos into `core/`, `quests/stormseeker/`, `platform/hytale/`, and `harness/` inside the existing `Legendary` repository, replacing four dependency mechanisms with plain `project(":core")` edges.

**Tech Stack:** Java 25, Gradle 9.2 (wrapper), JUnit 5, Error Prone, Spotless/palantir-java-format, Gradle Shadow, Hytale Server API (`com.hypixel.hytale.*`).

**Design spec:** `docs/superpowers/specs/2026-08-24-monorepo-consolidation-design.md`

## Global Constraints

- **Java toolchain and `options.release`: 25** in every module. Hytale's `HytaleServer.jar` ships class-file major 69 (Java 25); Java 21's compiler cannot read it.
- **Error Prone: `2.50.0`.** Version `2.27.1` fails on JDK 25's javac with `NoSuchFieldError: TypeTag.UNKNOWN`.
- **Gradle stays 9.2.** Gradle 9.2 cannot *run* on Java 26 (`Unsupported class file major version 70`); the daemon JVM is pinned to 25.
- **JDK 25 location:** `~/.jdks/jdk-25.0.4.1+1` (Eclipse Temurin 25.0.4.1+1, already installed and checksum-verified). Gradle auto-detection finds it.
- **Never invoke a build gate through a pipe.** Redirect to a file as the whole command, then read the file separately. Read the `BUILD SUCCESSFUL` / `BUILD FAILED` line as the verdict — **an absent verdict line is a failure, not a pass**. Exit status is not the authority: during investigation `./gradlew build | tail -40` reported exit 0 on a run with 23 errors.
- **Phase 1's baseline is PARTIAL — 166 of 192 (recorded 2026-08-24).** Two repos cannot go green before the restructure, for reasons the restructure removes by construction. `LegendaryContent` cannot resolve `io.github.legendaryforge:LegendaryCore:0.0.0-SNAPSHOT` — that coordinate leaks transitively out of `Legendary`'s composite build and Content declares no `mavenLocal()`; this is the coordinate-identity defect the design documents, and adding `mavenLocal()` to paper over it would perpetuate the very mechanism Phase 2 deletes. `LegendaryHytale` needs the `dev.scaffoldit` SDK and has never built standalone. Content's 26 and Hytale's 0 are first verified at Task 13, not Task 5. Green today: Core 73 + Legendary 73 + Dogfood 20 = 166; 166 + 26 = 192, confirming the invariant's arithmetic. The baseline is weakened but not void: for the three repos that do go green, later failure remains attributable to the restructure.
- **Test invariant: 187 tests EXECUTED** must pass. Per-module: `core` 63, `quests/stormseeker` 73, `harness` 51, `platform/hytale` 0.
- **The invariant moved 192 → 187 at the final fix wave, deliberately.** The whole-branch review found `core/src/.../core/internal/platform/hytale/` — 6 main + 3 test files of self-described Hytale-adapter scaffold, referenced by nothing and superseded by the real `platform/hytale` module. Deleting it removed 5 executed tests along with it. The design's enumeration of core's Hytale surface as "exactly 2 files" was derived by grepping `com.hypixel` imports; that package imports only core's own API and `java.util`, so the grep could not see it. **True by import count, false by intent.**
- **The invariant is a RUNTIME executed count, not a static annotation count.** These are different numbers and must never be compared to each other. `LegendaryCore` contains one `@ParameterizedTest` (`DefaultEncounterJoinPolicyTest`) that expands to **11** executed cases, so its 63 annotations yield 73 executed tests. No other repo has a parameterized test. Always measure with `find . -path '*/build/test-results/test/*.xml' -exec grep -ho 'tests="[0-9]*"' {} + | awk -F'"' '{s+=$2} END {print s}'`, never by grepping for `@Test`.
- **Local commits are authorized** for the five Hytale repos for this plan's work (operator, 2026-08-24). **Nothing is pushed without separate explicit go-ahead** — this includes Task 6's push and any GitHub archiving.
- **`core/` must contain zero `com.hypixel.*` imports.** Enforced by `:core:checkNoPlatformImports` from Task 8 onward.

---

# Phase 1 — Build repair (current five-repo layout)

### Task 1: Pin the daemon JVM in LegendaryCore

**Files:**
- Create: `LegendaryCore/gradle/gradle-daemon-jvm.properties`
- Delete: `LegendaryCore/gradlew21`

**Interfaces:**
- Produces: a committed, machine-independent daemon JVM pin. Every later task runs `./gradlew` with no `JAVA_HOME` prefix.

- [ ] **Step 1: Confirm the failure this fixes**

Run — note the bare invocation and file redirect:

```bash
cd LegendaryCore
./gradlew --version > /tmp/gradle-default-jvm.log 2>&1
cat /tmp/gradle-default-jvm.log
```

Expected: failure containing `Unsupported class file major version 70`, because the system default `java` is 26. This is the condition the pin removes.

- [ ] **Step 2: Generate the daemon JVM criteria file**

```bash
cd LegendaryCore
JAVA_HOME=$HOME/.jdks/jdk-25.0.4.1+1 ./gradlew updateDaemonJvm --jvm-version=25 --jvm-vendor=ADOPTIUM > /tmp/updatedaemon.log 2>&1
cat /tmp/updatedaemon.log
```

Expected: `BUILD SUCCESSFUL`, and `gradle/gradle-daemon-jvm.properties` created.

- [ ] **Step 3: Verify the pin works without JAVA_HOME**

```bash
cd LegendaryCore
./gradlew --version > /tmp/gradle-pinned.log 2>&1
cat /tmp/gradle-pinned.log
```

Expected: succeeds, reporting `JVM: 25.0.4.1`. If it still reports 26 or fails, the criteria file was not picked up — stop and report.

- [ ] **Step 4: Remove the superseded shim**

```bash
cd LegendaryCore
git rm gradlew21
```

- [ ] **Step 5: Prepare commit (await authorization)**

```bash
git add gradle/gradle-daemon-jvm.properties
git commit -m "build: pin daemon JVM to Java 25, retire gradlew21 shim"
```

---

### Task 2: Retarget LegendaryCore to Java 25 and upgrade Error Prone

**Files:**
- Modify: `LegendaryCore/build.gradle.kts` (toolchain block, `options.release`, and the `errorprone` dependency)
- Modify: `LegendaryCore/legendarycore-testmod/build.gradle.kts` (toolchain block)

**Scope note (plan defect, corrected mid-execution 2026-08-24):** an earlier draft
of this task listed only the root `build.gradle.kts`. `LegendaryCore` is a
*multi-project* build — `settings.gradle` declares `include("legendarycore-testmod")`
— and that subproject hardcodes `JavaLanguageVersion.of(21)`. Retargeting only the
root makes `clean build` fail at dependency resolution with *"looking for a library
compatible with JVM runtime version 21, but 'root project :' is only compatible with
JVM runtime version 25 or newer."* Every module of a multi-project build must move
together.

**Interfaces:**
- Consumes: the daemon JVM pin from Task 1.
- Produces: a green `LegendaryCore` build compiling against the current Hytale jar.

- [ ] **Step 1: Run the build to capture the current failure**

```bash
cd LegendaryCore
./gradlew clean build > /tmp/core-before.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)|error:" /tmp/core-before.log | head -20
```

Expected: `BUILD FAILED`, with `class file has wrong version 69.0, should be 65.0` and `23 errors`.

- [ ] **Step 2: Set the toolchain to 25**

In `LegendaryCore/build.gradle.kts`, replace:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
```

with:

```kotlin
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java_version").get().toInt()))
    }
}
```

`gradle.properties` already declares `java_version=25`; until now the build script never read it. This wires the declared value to the actual toolchain.

**Use `getOrElse`, not `get` (regression found in Task 4, 2026-08-24).** Write
`providers.gradleProperty("java_version").getOrElse("25").toInt()` at every read site —
toolchain, `options.release`, the testmod toolchain, and the guard's `targetJava`.
`.get()` **throws** when the property is absent, and `LegendaryDogfood` consumes
`LegendaryCore` as an out-of-tree subproject
(`project(':LegendaryCore').projectDir = file('../LegendaryCore')`), so Gradle reads
*Dogfood's* `gradle.properties` — which has no `java_version` — and the build dies with
`Cannot query the value of Gradle property 'java_version' because it has no value
available.` The property still governs wherever it is present, including Task 3's
negative test; the literal is only a fallback for out-of-tree consumers.

- [ ] **Step 3: Set the release level to 25**

In the same file, inside `tasks.withType<JavaCompile>().configureEach { ... }`, replace `options.release = 21` with:

```kotlin
    options.release = providers.gradleProperty("java_version").get().toInt()
```

- [ ] **Step 4: Upgrade Error Prone**

Replace:

```kotlin
    errorprone("com.google.errorprone:error_prone_core:2.27.1")
```

with:

```kotlin
    errorprone("com.google.errorprone:error_prone_core:2.50.0")
```

- [ ] **Step 4b: Retarget the testmod subproject**

In `LegendaryCore/legendarycore-testmod/build.gradle.kts`, replace:

```kotlin
        languageVersion.set(JavaLanguageVersion.of(21))
```

with:

```kotlin
        languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("java_version").get().toInt()))
```

A Java 21 consumer cannot resolve a Java 25 producer; both must move together.

- [ ] **Step 5: Run the build and verify it passes**

```bash
cd LegendaryCore
./gradlew clean build > /tmp/core-after.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/core-after.log
grep -cE "error:" /tmp/core-after.log
```

Expected: `BUILD SUCCESSFUL`, error count `0`. One deprecation warning for `setPermissionGroup(GameMode)` is expected and acceptable.

- [ ] **Step 6: Prepare commit (await authorization)**

```bash
git add build.gradle.kts
git commit -m "build: target Java 25 from java_version property, upgrade Error Prone to 2.50.0

The build script hardcoded 21 while gradle.properties already declared
java_version=25 and was never read. Hytale's server jar is class-file
major 69 (Java 25) as of the 2026-08-17 game update."
```

---

### Task 3: Add the Hytale class-file version guard

**Files:**
- Modify: `LegendaryCore/build.gradle.kts` (after the `hasHytaleServerJar` declaration, around line 50)

**Interfaces:**
- Consumes: `hytaleServerJar` and `hasHytaleServerJar` (existing vals in `build.gradle.kts`).
- Produces: `hytaleJarJavaVersion(File): Int?` — a build-script helper returning the Java language version of the Hytale jar, or `null` when unreadable.

- [ ] **Step 1: Write the guard**

Insert into `LegendaryCore/build.gradle.kts` immediately after the line
`val hasHytaleServerJar = hasHytaleInstall && hytaleServerJar.exists()`:

Add `import java.util.zip.ZipFile` to the **top** of the file, alongside the existing
errorprone imports. The `java-library` plugin contributes a `java { }` extension whose
name shadows the `java.*` package prefix inside a build script, so a fully-qualified
`java.util.zip.ZipFile(...)` fails to compile with `Unresolved reference 'util'`.

```kotlin
/**
 * Reads the class-file major version of a known class inside the Hytale server jar.
 * Class-file major 65 = Java 21, 69 = Java 25. Returns null if unreadable.
 */
fun hytaleJarJavaVersion(jar: File): Int? {
    if (!jar.exists()) return null
    return runCatching {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("com/hypixel/hytale/protocol/GameMode.class") ?: return null
            zip.getInputStream(entry).use { input ->
                val header = ByteArray(8)
                if (input.read(header) < 8) return null
                val major = ((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)
                major - 44
            }
        }
    }.getOrNull()
}

if (hasHytaleServerJar) {
    val jarJava = hytaleJarJavaVersion(hytaleServerJar)
    val targetJava = providers.gradleProperty("java_version").get().toInt()
    if (jarJava != null && jarJava > targetJava) {
        throw GradleException(
            """
            |Hytale server jar requires Java $jarJava but this build targets Java $targetJava.
            |The game was updated underneath the build; javac cannot read newer class files.
            |Fix: set java_version=$jarJava in gradle.properties (and install a matching JDK).
            |Jar: $hytaleServerJar
            """.trimMargin(),
        )
    }
}
```

- [ ] **Step 2: Verify the guard stays silent when versions agree**

```bash
cd LegendaryCore
./gradlew clean build > /tmp/core-guard-ok.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/core-guard-ok.log
```

Expected: `BUILD SUCCESSFUL`. `java_version=25` and the jar is Java 25, so the guard does not fire.

- [ ] **Step 3: Verify the guard fires on mismatch**

Temporarily set `java_version=21` in `gradle.properties`, then:

```bash
cd LegendaryCore
./gradlew clean build > /tmp/core-guard-fires.log 2>&1
grep -A4 "Hytale server jar requires" /tmp/core-guard-fires.log
```

Expected: the explicit guard message naming Java 25 vs 21 — **not** a 23-error compiler cascade. This is the whole point of the guard: it must fail before javac does.

- [ ] **Step 4: Restore the correct value**

Set `java_version=25` back in `gradle.properties`, then re-run Step 2 and confirm `BUILD SUCCESSFUL`.

- [ ] **Step 5: Prepare commit (await authorization)**

```bash
git add build.gradle.kts
git commit -m "build: fail fast when the Hytale jar outpaces the configured Java target

The build reads a mutable launcher-managed path. When the game updated on
2026-08-17 the result was a 23-error cascade that read like our own code
broke. This turns that into one explicit message."
```

---

### Task 4: Align the remaining four repos to Java 25

**Files:**
- Modify: `Legendary/build.gradle.kts` (toolchain 21 → 25, `options.release` 21 → 25, Error Prone version)
- Modify: `LegendaryContent/build.gradle.kts` (already toolchain 25 — no change needed; verify only)
- Modify: `LegendaryDogfood/build.gradle` (add explicit toolchain 25)
- Create: `Legendary/gradle/gradle-daemon-jvm.properties`, `LegendaryContent/gradle/gradle-daemon-jvm.properties`, `LegendaryDogfood/gradle/gradle-daemon-jvm.properties`, `LegendaryHytale/gradle/gradle-daemon-jvm.properties`

**Interfaces:**
- Consumes: the pattern established in Tasks 1–2.
- Produces: all five repos building on Java 25 with a pinned daemon.

- [ ] **Step 0: Put LegendaryDogfood on a feature branch**

LegendaryDogfood sits on `master`; the other four are on `feat/stormseeker-bridge`.
Match them before modifying it (operator decision, 2026-08-24).

```bash
cd LegendaryDogfood
git checkout -b feat/stormseeker-bridge
git rev-parse --abbrev-ref HEAD
```

Expected: `feat/stormseeker-bridge`.

- [ ] **Step 1: Pin the daemon JVM in the other four repos**

```bash
for r in Legendary LegendaryContent LegendaryDogfood LegendaryHytale; do
  (cd "$r" && JAVA_HOME=$HOME/.jdks/jdk-25.0.4.1+1 ./gradlew updateDaemonJvm --jvm-version=25 --jvm-vendor=ADOPTIUM > "/tmp/daemon-$r.log" 2>&1)
done
grep -l "BUILD SUCCESSFUL" /tmp/daemon-*.log
```

Expected: four log files listed. Any repo missing from the output failed — investigate before continuing.

- [ ] **Step 2: Retarget Legendary to Java 25**

In `Legendary/build.gradle.kts`, change `JavaLanguageVersion.of(21)` to `JavaLanguageVersion.of(25)`, change `options.release = 21` to `options.release = 25`, and change `error_prone_core:2.27.1` to `error_prone_core:2.50.0`.

- [ ] **Step 3: Add an explicit toolchain to LegendaryDogfood**

`LegendaryDogfood/build.gradle` declares no Java version at all, silently inheriting the Gradle JVM. Add after the `plugins` block:

```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

- [ ] **Step 4: Build each repo and record the verdict**

```bash
for r in LegendaryCore Legendary LegendaryContent LegendaryDogfood; do
  (cd "$r" && ./gradlew clean build > "/tmp/build-$r.log" 2>&1)
done
for r in LegendaryCore Legendary LegendaryContent LegendaryDogfood; do
  printf "%-18s %s\n" "$r" "$(grep -E 'BUILD (SUCCESSFUL|FAILED)' /tmp/build-$r.log | tail -1)"
done
```

Expected: `BUILD SUCCESSFUL` on all four. A blank verdict for any repo is a failure, not a pass.

`LegendaryHytale` is expected to require the `dev.scaffoldit` Hytale SDK plugin and may not build standalone; record its result but do not block on it.

- [ ] **Step 5: Prepare commits (await authorization)**

One commit per repo, each naming only that repo's files.

---

### Task 5: Establish the green baseline

**Files:** none modified — this task only measures.

**Interfaces:**
- Produces: the confirmed 182/128 baseline that Phase 2 must preserve.

- [ ] **Step 1: Count tests, excluding the vendored duplicate**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
tot=0
for r in Legendary LegendaryCore LegendaryContent LegendaryDogfood LegendaryHytale; do
  n=$(find "$r" -path '*/src/test/*' -name '*.java' -not -path '*/build/*' -not -path '*/vendor/*' -exec grep -h '@Test\|@ParameterizedTest' {} + 2>/dev/null | wc -l)
  printf "  %-18s %4s\n" "$r" "$n"; tot=$((tot+n))
done
echo "TOTAL: $tot"
```

Expected: `TOTAL: 182` **static annotations** — this is a cross-check, NOT the invariant. The runtime figure is 192; the difference is one parameterized test expanding to 11 cases. The `-not -path '*/vendor/*'` exclusion is required — `Legendary/vendor/LegendaryCore` is a complete second copy of Core and double-counts 37 test files.

**Sweep caveat (measured 2026-08-24).** `LegendaryDogfood` includes `../LegendaryCore`
as an out-of-tree subproject, so **building Dogfood alone writes Core's 73 results into
`LegendaryCore/build/test-results/`** — verified by deleting that directory and building
only Dogfood. This does not inflate a repo-wide sum (the results land in one directory
either way and are overwritten, not appended), but it does mean a sweep is only
meaningful **after building every repo**: run against a partial build and Core's tests
appear to belong to whichever build last produced them.

- [ ] **Step 2: Confirm tests actually executed, not just compiled**

```bash
for r in LegendaryCore Legendary LegendaryContent LegendaryDogfood; do
  echo "=== $r ==="
  find "$r" -path '*/build/test-results/test/*.xml' -not -path '*/vendor/*' -exec grep -ho 'tests="[0-9]*"' {} + | awk -F'"' '{s+=$2} END {print "  tests executed: " s}'
done
```

Expected: a non-zero count per repo. A module that compiles but never runs its tests reports zero here while still showing `BUILD SUCCESSFUL` — that is exactly what this step exists to catch.

- [x] **Step 3: Record the baseline in the plan** — measured 2026-08-24, twice independently (controller + Task 5 subagent, agreeing):

| Repo | Verdict | Tests executed | Failures |
|---|---|---|---|
| LegendaryCore | BUILD SUCCESSFUL | 73 | 0 |
| Legendary | BUILD SUCCESSFUL | 73 | 0 |
| LegendaryDogfood | BUILD SUCCESSFUL | 20 | 0 |
| LegendaryContent | BUILD FAILED (accepted) | 0 | — |
| LegendaryHytale | BUILD FAILED (accepted) | 0 | — |
| **Green total** | | **166** | **0** |

166 + LegendaryContent's 26 = 192, matching the invariant. Note Dogfood's static
annotation count is **20**, identical to its runtime count — the 26 belongs to
LegendaryContent. A Task 5 report conflated the two and reported a phantom
six-test shortfall in Dogfood; there is none.

Append the observed per-repo executed-test counts as a comment in this file under Task 5, so Phase 2 has a concrete target rather than a remembered one.

---

# Phase 2 — Restructure

### Task 6: Land the unpushed commits

**Files:** none — git only.

- [ ] **Step 1: Confirm what is unpushed**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git -C Legendary log --oneline origin/main..feat/stormseeker-bridge
git -C LegendaryHytale log --oneline origin/main..feat/stormseeker-bridge
```

Expected: 5 commits for Legendary, 4 for LegendaryHytale.

- [ ] **Step 2: Push both branches (await authorization — this is outward-facing)**

```bash
git -C Legendary push -u origin feat/stormseeker-bridge
git -C LegendaryHytale push -u origin feat/stormseeker-bridge
```

`LegendaryHytale`'s branch has no upstream configured, which is why its status doc reported "up to date" while carrying 4 unpushed commits.

- [ ] **Step 3: Verify nothing remains unpushed**

```bash
git -C Legendary status -sb | head -1
git -C LegendaryHytale status -sb | head -1
```

Expected: no `ahead` marker on either.

---

### Task 7: Create the monorepo skeleton

**Files:**
- Modify: `Legendary/settings.gradle.kts`
- Create: `Legendary/gradle/libs.versions.toml`

**Interfaces:**
- Produces: module coordinates `:core`, `:quests:stormseeker`, `:platform:hytale`, `:harness`, and a version catalog referenced as `libs.*` by every module.

- [ ] **Step 1: Write the version catalog**

Create `Legendary/gradle/libs.versions.toml`:

```toml
[versions]
java = "25"
errorprone = "2.50.0"
errorprone-plugin = "4.4.0"
spotless = "8.2.0"
shadow = "9.3.1"
junit = "5.10.2"
palantir = "2.85.0"
gson = "2.10.1"
jetbrains-annotations = "24.1.0"

[libraries]
errorprone-core = { module = "com.google.errorprone:error_prone_core", version.ref = "errorprone" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
jetbrains-annotations = { module = "org.jetbrains:annotations", version.ref = "jetbrains-annotations" }

[plugins]
errorprone = { id = "net.ltgt.errorprone", version.ref = "errorprone-plugin" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
shadow = { id = "com.gradleup.shadow", version.ref = "shadow" }
```

JUnit is currently 5.10.2 in three repos and 5.10.0 in a fourth. The catalog makes that impossible to drift silently.

- [ ] **Step 2: Replace settings.gradle.kts**

Replace the entire contents of `Legendary/settings.gradle.kts` with:

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Legendary"
```

**No `include(...)` lines here — corrected 2026-08-24.** An earlier draft declared all
four modules in this task, on the false premise that Gradle tolerates a declared module
whose directory does not exist. It does not: Gradle 9.2 fails with
`Configuring project ':<name>' without an existing directory is not allowed`, and this
was verified to affect even a flat `include(":core")`, not just the nested paths.

**Each module task adds its own `include(...)` line when it creates that module's
directory** (Tasks 8-11). This is strictly better than declaring them up front: every
commit in the sequence is a self-consistent, buildable state, rather than four commits
of broken build followed by one that repairs it. It also avoids committing empty
placeholder directories, which git does not track anyway — so a fresh clone of any
intermediate commit would have failed.

The `includeBuild("vendor/LegendaryCore")` block and its `dependencySubstitution` are deleted here. That substitution referenced `com.example:LegendaryCore` while the dependency requested `io.github.legendaryforge:LegendaryCore`, so it never matched and resolution fell through to a stale `~/.m2` jar.

- [ ] **Step 3: Verify Gradle sees four modules**

```bash
cd Legendary
./gradlew projects > /tmp/projects.log 2>&1
grep -E "Root project|Project ':|BUILD (SUCCESSFUL|FAILED)" /tmp/projects.log
grep -c "includeBuild\|dependencySubstitution\|com.example" settings.gradle.kts
```

Expected: `BUILD SUCCESSFUL`, the root project listed with **no** subprojects, and a
count of `0` for the second command — confirming the broken `includeBuild` /
`com.example` substitution is gone. Subprojects appear as Tasks 8-11 add them.

- [ ] **Step 4: Prepare commit (await authorization)**

```bash
git add settings.gradle.kts gradle/libs.versions.toml
git commit -m "build: declare four-module structure and version catalog"
```

---

### Task 8: Create the core module and enforce the platform boundary

**Files:**
- Create: `Legendary/core/` (from `LegendaryCore/src`)
- Create: `Legendary/core/build.gradle.kts`
- Delete: `Legendary/core/src/main/java/io/github/legendaryforge/legendary/core/LegendaryCorePlugin.java`
- Delete: `Legendary/core/src/main/java/io/github/legendaryforge/legendary/core/internal/commands/ExampleCommand.java`
- Delete: `Legendary/core/src/main/resources/manifest.json`
- Delete: `Legendary/core/src/main/resources/Server/Item/Recipes/Example_Recipe.json`

**Interfaces:**
- Produces: project `:core`, package root `io.github.legendaryforge.legendary.core`, consumed by every other module as `implementation(project(":core"))`.
- Produces: task `:core:checkNoPlatformImports`, wired into `:core:check`.

- [ ] **Step 0: Declare the module in `settings.gradle.kts`**

Append `include(":core")` to `Legendary/settings.gradle.kts`. Gradle requires the
directory `core/` to exist before this line is valid, so do it in the same change
as creating the directory below — a commit containing the `include` without the
directory will not configure.

- [ ] **Step 1: Copy the sources**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
mkdir -p Legendary/core
cp -r LegendaryCore/src Legendary/core/src
```

**`LegendaryCore/buildSrc` is deliberately NOT carried over.** It contains one file,
`RunHytalePlugin.kt`, which is never applied by any build script — verified by grep
across `build.gradle.kts`, `settings.gradle`, and the testmod script. `buildSrc` also
has no build script of its own, so with no `kotlin-dsl` plugin configured that `.kt`
file is never compiled (build logs show `:buildSrc:compileJava NO-SOURCE`). Its
`runServer` task also points at a placeholder `https://example.com/hytale-server.jar`.
It is dead code; copying it would carry a non-functioning task into the new build.

- [ ] **Step 2: Write core/build.gradle.kts**

```kotlin
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java-library")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)
    implementation(libs.jetbrains.annotations)
    errorprone(libs.errorprone.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = libs.versions.java.get().toInt()

    options.errorprone.isEnabled.set(true)
    (options.errorprone as ErrorProneOptions).disableWarningsInGeneratedCode.set(true)
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-XepAllErrorsAsWarnings")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:EqualsHashCode:ERROR")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:MissingOverride:ERROR")
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

No `maven-publish`, no `shadow`, and no Hytale jar detection: `core` is now a pure
library consumed in-tree, and the Hytale detection lives in `platform/hytale`.

- [ ] **Step 3: Append the boundary check to core/build.gradle.kts**

```kotlin
val checkNoPlatformImports by tasks.registering {
    group = "verification"
    description = "Fails if core contains any com.hypixel.* import. core must stay engine-agnostic."
    val javaSources = fileTree("src") { include("**/*.java") }
    inputs.files(javaSources)
    outputs.upToDateWhen { false }
    doLast {
        val offenders = javaSources.files.filter { it.readText().contains("com.hypixel.") }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("core must remain engine-agnostic, but ${offenders.size} file(s) import com.hypixel.*:")
                    offenders.forEach { appendLine("  " + it.relativeTo(projectDir)) }
                    appendLine("Platform-specific code belongs in :platform:hytale.")
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoPlatformImports) }
```

- [ ] **Step 4: Run the check and verify it FAILS, naming exactly two files**

```bash
cd Legendary
./gradlew :core:checkNoPlatformImports > /tmp/boundary-fail.log 2>&1
grep -A4 "core must remain engine-agnostic" /tmp/boundary-fail.log
```

Expected: FAIL, listing exactly `LegendaryCorePlugin.java` and `ExampleCommand.java`.

If it passes here, the check is not working — stop and report. If it names any
*other* file, stop and report: that would mean Core has platform coupling beyond
the two known files, and the design's central premise needs revisiting.

- [ ] **Step 5: Delete the template residue**

```bash
cd Legendary
rm core/src/main/java/io/github/legendaryforge/legendary/core/LegendaryCorePlugin.java
rm core/src/main/java/io/github/legendaryforge/legendary/core/internal/commands/ExampleCommand.java
rm core/src/main/resources/manifest.json
rm core/src/main/resources/Server/Item/Recipes/Example_Recipe.json
rmdir core/src/main/java/io/github/legendaryforge/legendary/core/internal/commands 2>/dev/null || true
```

These four files are unmodified Hytale plugin-template scaffolding. `LegendaryCorePlugin`
wires nothing from Core's own API — it logs `"Hello from %s"` and registers
`ExampleCommand`. Nothing references either class except the deleted manifest.

- [ ] **Step 6: Build and verify green with the expected test count**

```bash
cd Legendary
./gradlew :core:build > /tmp/core-module.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/core-module.log
find core/build/test-results/test -name '*.xml' -exec grep -ho 'tests="[0-9]*"' {} + | awk -F'"' '{s+=$2} END {print "tests executed: " s}'
```

Expected: `BUILD SUCCESSFUL`, `tests executed: 68`. Core now compiles with no Hytale
jar on its classpath at all.

- [ ] **Step 7: Commit**

```bash
git add core/
git commit -m "refactor: add core module from LegendaryCore@377ea94, drop template residue

LegendaryCorePlugin and ExampleCommand were unmodified Hytale plugin-template
scaffolding wiring none of Core's own API. Removing them makes core a pure
library, enforced by checkNoPlatformImports."
```

---

### Task 9: Create the quests/stormseeker module

**Files:**
- Create: `Legendary/quests/stormseeker/` (from `Legendary/src`)
- Create: `Legendary/quests/stormseeker/build.gradle.kts`

**Interfaces:**
- Consumes: `project(":core")`.
- Produces: project `:quests:stormseeker`, package root `io.github.legendaryforge.legendary.mod`.

- [ ] **Step 0: Declare the module in `settings.gradle.kts`**

Append `include(":quests:stormseeker")` to `Legendary/settings.gradle.kts`. Gradle requires the
directory `quests/stormseeker/` to exist before this line is valid, so do it in the same change
as creating the directory below — a commit containing the `include` without the
directory will not configure.

**Debris check after copying (added 2026-08-24).** `cp -r` copies untracked and
gitignored files too. In Task 8 it swept in a gitignored `DefaultCoreRuntime.java.bak`
(176 lines) which reached a commit before being removed. The source repos for this task
were measured clean (0 untracked, 0 ignored under `src/`), but verify rather than assume:

```bash
find <newmodule>/src -type f ! -name '*.java' ! -name '*.json' ! -name '*.properties'
```

Expect no output. Anything listed is debris — delete it **before** committing, not after.

- [ ] **Step 1: Move the existing Legendary sources into the module**

```bash
cd Legendary
mkdir -p quests/stormseeker
git mv src quests/stormseeker/src
```

`git mv` preserves rename detection, keeping `git log --follow` useful across the restructure.

- [ ] **Step 2: Write quests/stormseeker/build.gradle.kts**

```kotlin
import net.ltgt.gradle.errorprone.ErrorProneOptions
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java-library")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":core"))
    errorprone(libs.errorprone.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = libs.versions.java.get().toInt()

    options.errorprone.isEnabled.set(true)
    (options.errorprone as ErrorProneOptions).disableWarningsInGeneratedCode.set(true)
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-XepAllErrorsAsWarnings")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:EqualsHashCode:ERROR")
    (options.errorprone as ErrorProneOptions).errorproneArgs.add("-Xep:MissingOverride:ERROR")
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

`api` rather than `implementation`: `platform/hytale` consumes Core types through this module's surface.

- [ ] **Step 3: Delete the root build script's old configuration**

The old `Legendary/build.gradle.kts` configured a single-project build. Replace its entire contents with root-level configuration only:

```kotlin
plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone) apply false
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

**Two corrections from an earlier draft (found in Task 9, 2026-08-24).** The draft wrote
`alias(libs.plugins.spotless) apply false` while also declaring a `spotless { }` block —
a contradiction: `apply false` puts the plugin on the classpath without applying it, so
its extension does not exist and the block fails with an unresolved reference. Spotless
must actually apply at the root because the root owns the `*.gradle.kts` formatting.
Errorprone correctly stays `apply false` — only the Java modules apply it. The draft also
omitted `repositories { }`, without which ktlint's own dependencies cannot resolve.

**Expect this to reformat `core/build.gradle.kts`.** The root spotless target reaches
`*/*.gradle.kts` for the first time here, and Task 8's file was written before this
config existed. Running `./gradlew spotlessApply` will wrap lines and add trailing commas
there. That is semantically inert and in scope — it is the formatting rule this task
introduces, applied to a file that predates it.

The root project no longer produces a jar; the modules do.

- [ ] **Step 4: Build and verify test count**

```bash
cd Legendary
./gradlew :quests:stormseeker:build > /tmp/quests.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/quests.log
find quests/stormseeker/build/test-results/test -name '*.xml' -exec grep -ho 'tests="[0-9]*"' {} + | awk -F'"' '{s+=$2} END {print "tests executed: " s}'
```

Expected: `BUILD SUCCESSFUL`, `tests executed: 73`.

- [ ] **Step 5: Prepare commit (await authorization)**

```bash
git add -A quests/ build.gradle.kts
git commit -m "refactor: move Stormseeker questline into quests/stormseeker module"
```

---

### Task 10: Create the platform/hytale module

**Files:**
- Create: `Legendary/platform/hytale/` (from `LegendaryHytale/src`, minus `dev.hytalemodding`)
- Create: `Legendary/platform/hytale/build.gradle.kts`
- Copy: `LegendaryHytale/assets/`, `LegendaryHytale/devserver/` if present

**Interfaces:**
- Consumes: `project(":quests:stormseeker")`, transitively `:core`.
- Produces: project `:platform:hytale`, the only module importing `com.hypixel.*`.

- [ ] **Step 0: Declare the module in `settings.gradle.kts`**

Append `include(":platform:hytale")` to `Legendary/settings.gradle.kts`. Gradle requires the
directory `platform/hytale/` to exist before this line is valid, so do it in the same change
as creating the directory below — a commit containing the `include` without the
directory will not configure.

**Debris check after copying (added 2026-08-24).** `cp -r` copies untracked and
gitignored files too. In Task 8 it swept in a gitignored `DefaultCoreRuntime.java.bak`
(176 lines) which reached a commit before being removed. The source repos for this task
were measured clean (0 untracked, 0 ignored under `src/`), but verify rather than assume:

```bash
find <newmodule>/src -type f ! -name '*.java' ! -name '*.json' ! -name '*.properties'
```

Expect no output. Anything listed is debris — delete it **before** committing, not after.

- [ ] **Step 1: Copy sources and drop the template package**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
mkdir -p Legendary/platform/hytale
cp -r LegendaryHytale/src Legendary/platform/hytale/src
rm -rf Legendary/platform/hytale/src/main/java/dev/hytalemodding
cp -r LegendaryHytale/assets Legendary/platform/hytale/assets 2>/dev/null || true
cp -r LegendaryHytale/devserver Legendary/platform/hytale/devserver 2>/dev/null || true
```

- [ ] **Step 2: Verify exactly 7 Hytale-importing files remain**

```bash
cd Legendary
grep -rl 'com\.hypixel\.hytale' platform/hytale/src | wc -l
grep -rl 'com\.hypixel\.hytale' platform/hytale/src
```

Expected: `7`. The 10 originally present minus the 3 deleted `dev.hytalemodding` files.

- [ ] **Step 3: Write platform/hytale/build.gradle.kts**

```kotlin
import java.util.zip.ZipFile

plugins {
    id("java-library")
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

// --- Hytale install detection (Linux) ---
val patchlineProp = (findProperty("patchline") as String?) ?: "release"
val hytaleHomeProp = findProperty("hytale_home") as String?

val hytaleHome =
    hytaleHomeProp ?: run {
        val flatpak = file(System.getProperty("user.home") + "/.var/app/com.hypixel.HytaleLauncher/data/Hytale")
        if (flatpak.exists()) {
            flatpak.absolutePath
        } else {
            val local = file(System.getProperty("user.home") + "/.local/share/Hytale")
            if (local.exists()) local.absolutePath else ""
        }
    }

val hytaleServerJar = file("$hytaleHome/install/$patchlineProp/package/game/latest/Server/HytaleServer.jar")
val hasHytaleServerJar = hytaleHome.isNotBlank() && hytaleServerJar.exists()

/**
 * Class-file major 65 = Java 21, 69 = Java 25. Returns null if unreadable.
 */
fun hytaleJarJavaVersion(jar: File): Int? {
    if (!jar.exists()) return null
    return runCatching {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("com/hypixel/hytale/protocol/GameMode.class") ?: return null
            zip.getInputStream(entry).use { input ->
                val header = ByteArray(8)
                if (input.read(header) < 8) return null
                (((header[6].toInt() and 0xFF) shl 8) or (header[7].toInt() and 0xFF)) - 44
            }
        }
    }.getOrNull()
}

val targetJava = libs.versions.java.get().toInt()

if (hasHytaleServerJar) {
    val jarJava = hytaleJarJavaVersion(hytaleServerJar)
    if (jarJava != null && jarJava > targetJava) {
        throw GradleException(
            """
            |Hytale server jar requires Java $jarJava but this build targets Java $targetJava.
            |The game was updated underneath the build; javac cannot read newer class files.
            |Fix: set java = "$jarJava" in gradle/libs.versions.toml (and install a matching JDK).
            |Jar: $hytaleServerJar
            """.trimMargin(),
        )
    }
} else {
    logger.lifecycle(
        "Hytale install not detected; skipping Server API jar. Set hytale_home in gradle.properties for local dev.",
    )
}

dependencies {
    api(project(":quests:stormseeker"))
    if (hasHytaleServerJar) {
        compileOnly(files(hytaleServerJar))
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJava))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = targetJava

    // Without the Hytale jar these cannot compile; skip them so the rest of the
    // module still builds on a machine with no game installed.
    if (!hasHytaleServerJar) {
        exclude("**/hytale/**")
    }
}

tasks.test {
    useJUnitPlatform()
}
```

Error Prone is deliberately omitted here: this module's sources are thin adapters over a third-party API, and its compile classpath varies by machine.

**Harden the guard here (Task 3 review finding, 2026-08-24).** The Task 3 version
keys on the single entry `com/hypixel/hytale/protocol/GameMode.class`. If a future
Hytale patch renames or removes that class, `hytaleJarJavaVersion` returns `null`,
the guard silently disarms, and the original 23-error cascade returns — the guard
failing open in exactly the scenario it exists for. `platform/hytale` is where the
guard lives permanently, so fix it here rather than in the transient Core copy:
scan for the **first** entry matching `com/hypixel/**.class` instead of a fixed
path, and if the jar contains no such entry at all, **fail** with a distinct message
(`Hytale jar contains no com/hypixel classes — cannot determine its Java version`)
rather than returning `null`. Unreadable-jar I/O errors may still return `null`.

**Kotlin DSL gotcha (found in Task 3, 2026-08-24):** the `java-library` plugin
contributes a `java { }` extension whose name **shadows the `java.*` package prefix**
inside the build script. A fully-qualified `java.util.zip.ZipFile(...)` therefore fails
to compile with `Unresolved reference 'util'`. Import `java.util.zip.ZipFile` at the top
of the file and use it unqualified, as shown above.

- [ ] **Step 4: Build and verify**

```bash
cd Legendary
./gradlew :platform:hytale:build > /tmp/platform.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/platform.log
```

Expected: `BUILD SUCCESSFUL`, 0 tests (this module has none — a known gap recorded in the spec).

- [ ] **Step 5: Prepare commit (await authorization)**

```bash
git add platform/
git commit -m "refactor: add platform/hytale from LegendaryHytale@1552465, drop template package

Hytale install detection and the class-file version guard move here from
core. This is now the only module importing com.hypixel.*."
```

---

### Task 10b: Repair Hytale API drift in platform/hytale

**Files:**
- Modify: `platform/hytale/src/main/java/io/github/legendaryforge/hytale/stormseeker/HytaleStormseekerHost.java` (import, line 66)
- Modify: `platform/hytale/src/main/java/io/github/legendaryforge/hytale/command/StormseekerStatusCommand.java` (line 55)

**Why this task exists.** Added 2026-08-24, after `platform/hytale` compiled against the
real Hytale jar for the first time. `LegendaryHytale` never built standalone (it needed
the `dev.scaffoldit` SDK), so its sources had not been compiled since February — the
2026-08-17 game update broke them and nothing noticed. An earlier claim in this plan that
there was "no Hytale API drift" was measured on LegendaryCore's 2 files only and did not
hold for this layer.

**Measured facts (via `javap` against the live jar — do not re-derive these by guessing):**

| Fact | Value |
|---|---|
| `TransformComponent.getPosition()` returns | **`org.joml.Vector3d`** (JOML, bundled in the jar) |
| `org.joml.Vector3d` accessors | public fields `x`,`y`,`z` **and** methods `x()`,`y()`,`z()` |
| `org.joml.Vector3d.getX()` | **does not exist** |
| `com.hypixel.hytale.math.vector.Vector3d` | **gone** — package survives but now holds `Vector3dUtil`, `Transform`, `Location` |
| `com.hypixel.hytale.protocol.Vector3d` | exists but is a **protocol/serialization** type whose `getX` is `static(MemorySegment)` — **NOT the right replacement** |

The protocol type is the trap: it has the right name and resolves, so an implementer
searching for `Vector3d` will find it and produce code that compiles into nonsense.
`TransformComponent` hands back JOML.

- [ ] **Step 1: Confirm the failure**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
./gradlew :platform:hytale:compileJava --console=plain > /tmp/drift-before.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/drift-before.log
grep -E "error:" /tmp/drift-before.log | sed 's|.*/src/main/java/||' | sort -u
```

Expected: `BUILD FAILED`, errors confined to the two files above.

- [ ] **Step 2: Fix `HytaleStormseekerHost.java`**

Replace `import com.hypixel.hytale.math.vector.Vector3d;` with `import org.joml.Vector3d;`.
Line 66's `Vector3d pos = transform.getPosition();` then resolves unchanged.

- [ ] **Step 3: Fix `StormseekerStatusCommand.java`**

Line 55 calls `p.getX()`, `p.getY()`, `p.getZ()` on the value of `pos.getPosition()`.
JOML has no `getX()`. Change to `p.x()`, `p.y()`, `p.z()` — the accessor form, not the
public fields, because it also satisfies the read-only `Vector3dc` interface should the
return type ever narrow.

- [ ] **Step 4: Verify against the REAL jar**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
./gradlew :platform:hytale:compileJava --console=plain > /tmp/drift-after.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/drift-after.log
grep -cE "error:" /tmp/drift-after.log
```

Expected: `BUILD SUCCESSFUL`, error count `0`. This must be run with the Hytale install
present — the whole point is compiling against the real API, so do **not** pass
`-Phytale_home`.

- [ ] **Step 5: Verify the no-install path still works**

```bash
./gradlew build --console=plain -Phytale_home=/nonexistent > /tmp/drift-skip.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/drift-skip.log
```

Expected: `BUILD SUCCESSFUL`. A machine without the game must still build the other modules.

- [ ] **Step 6: Full root build, both paths**

Natural build must now be green too — that is the deliverable. Confirm `core` = 68 and
`quests:stormseeker` = 73 executed tests.

- [ ] **Step 7: Commit**

```bash
git add platform/hytale/src
git commit -m "fix(platform): track Hytale API drift — Vector3d is org.joml, not math.vector

The 2026-08-17 game update moved Vector3d out of com.hypixel.hytale.math.vector.
TransformComponent.getPosition() returns org.joml.Vector3d, which has x()/y()/z()
and no getX(). These sources had not compiled since February because
LegendaryHytale never built standalone."
```

---

### Task 11: Create the harness module

**Files:**
- Create: `Legendary/harness/` (from `LegendaryContent/src`, `LegendaryDogfood/src/test`, `LegendaryCore/legendarycore-testmod/src`)
- Create: `Legendary/harness/build.gradle.kts`

**Interfaces:**
- Consumes: `project(":core")`, `project(":quests:stormseeker")`.
- Produces: project `:harness`, holding 51 tests.

- [ ] **Step 0: Declare the module in `settings.gradle.kts`**

Append `include(":harness")` to `Legendary/settings.gradle.kts`. Gradle requires the
directory `harness/` to exist before this line is valid, so do it in the same change
as creating the directory below — a commit containing the `include` without the
directory will not configure.

**Debris check after copying (added 2026-08-24).** `cp -r` copies untracked and
gitignored files too. In Task 8 it swept in a gitignored `DefaultCoreRuntime.java.bak`
(176 lines) which reached a commit before being removed. The source repos for this task
were measured clean (0 untracked, 0 ignored under `src/`), but verify rather than assume:

```bash
find <newmodule>/src -type f ! -name '*.java' ! -name '*.json' ! -name '*.properties'
```

Expect no output. Anything listed is debris — delete it **before** committing, not after.

- [ ] **Step 1: Copy all three sources**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
mkdir -p Legendary/harness/src/main/java Legendary/harness/src/test/java
cp -r LegendaryContent/src/main/java/* Legendary/harness/src/main/java/
cp -r LegendaryContent/src/test/java/* Legendary/harness/src/test/java/
cp -r LegendaryDogfood/src/test/java/* Legendary/harness/src/test/java/
cp -r LegendaryCore/legendarycore-testmod/src/main/java/* Legendary/harness/src/main/java/
cp -r LegendaryCore/legendarycore-testmod/src/test/java/* Legendary/harness/src/test/java/
```

Package roots differ (`legendary.content`, `legendarycontent`, `legendary.dogfood`, `legendary.testmod`) but do not collide, so all four coexist. Unifying them is deliberately out of scope — it is a rename touching 44 files and belongs in its own change.

- [ ] **Step 2: Write harness/build.gradle.kts**

```kotlin
plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":quests:stormseeker"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = Charsets.UTF_8.name()
    options.release = libs.versions.java.get().toInt()
}

tasks.test {
    useJUnitPlatform()
}
```

The old `legendarycore-testmod` had `tasks.test { dependsOn(":shadowJar") }`. That dependency is dropped — `core` no longer produces a shaded plugin jar, and the API-usage tests only need it on the compile classpath.

- [ ] **Step 3: Build and verify the test count**

```bash
cd Legendary
./gradlew :harness:build > /tmp/harness.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/harness.log
find harness/build/test-results/test -name '*.xml' -exec grep -ho 'tests="[0-9]*"' {} + | awk -F'"' '{s+=$2} END {print "tests executed: " s}'
```

Expected: `BUILD SUCCESSFUL`, `tests executed: 51` (Content 26 + Dogfood 20 + testmod 5). None of these are parameterized, so static and runtime counts agree here.

If compilation fails on imports of `io.github.legendaryforge.legendary.core.internal.*`, that is expected — Dogfood imported `internal.runtime.DefaultCoreRuntime` for wiring. Because `harness` depends on `core` as a plain project dependency with no module system in play, those imports resolve; if they do not, report rather than weakening the dependency.

- [ ] **Step 4: Prepare commit (await authorization)**

```bash
git add harness/
git commit -m "refactor: merge Content, Dogfood, and legendarycore-testmod into harness

Three implementations of consumer-side contract testing become one. Retains
Dogfood's role-switch and dual-membership coverage, which exists nowhere
else in the project."
```

---

### Task 12: Remove the submodule and purge strays

**Files:**
- Delete: `Legendary/vendor/`, `Legendary/.gitmodules`, `Legendary/pr.md`, `Legendary/test-output.txt`

- [ ] **Step 1: Deinit and remove the submodule**

```bash
cd Legendary
git submodule deinit -f vendor/LegendaryCore
git rm -f vendor/LegendaryCore
rm -rf .git/modules/vendor/LegendaryCore
rm -f .gitmodules
rmdir vendor 2>/dev/null || true
```

`vendor/LegendaryCore` was a complete second copy of Core pinned at `5b74207` (2026-01-30) while the sibling checkout was at `377ea94` — 37 duplicate test files now removed.

- [ ] **Step 2: Purge stray files**

```bash
cd Legendary
rm -f pr.md test-output.txt
```

Both are February leftovers: a stale PR draft and an untracked Gradle log.

- [ ] **Step 3: Verify no stale references remain**

```bash
cd Legendary
grep -rn "vendor/LegendaryCore\|com\.example\|mavenLocal" --include='*.kts' --include='*.gradle' --include='*.toml' . | grep -v '/build/'
```

Expected: no output. Any hit means a dependency mechanism survived the restructure.

- [ ] **Step 4: Prepare commit (await authorization)**

```bash
git add -A
git commit -m "chore: remove vendor submodule and stray files

vendor/LegendaryCore duplicated core at a 10-day-stale pin. All four
dependency mechanisms are now replaced by project(\":core\")."
```

---

### Task 13: Final verification

**Files:** none modified.

- [ ] **Step 1: Clean build of the whole monorepo**

```bash
cd Legendary
./gradlew clean build > /tmp/final.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/final.log
```

Expected: `BUILD SUCCESSFUL`. A blank result is a failure.

- [ ] **Step 2: Verify the test invariant**

```bash
cd Legendary
find . -path '*/build/test-results/test/*.xml' -exec grep -ho 'tests="[0-9]*"' {} + | awk -F'"' '{s+=$2} END {print "TOTAL tests executed: " s}'
for m in core quests/stormseeker platform/hytale harness; do
  n=$(find "$m/build/test-results/test" -name '*.xml' -exec grep -ho 'tests="[0-9]*"' {} + 2>/dev/null | awk -F'"' '{s+=$2} END {print s+0}')
  printf "  %-22s %s\n" "$m" "$n"
done
```

Expected: `TOTAL tests executed: 187`, distributed as core 63, quests/stormseeker 73, platform/hytale 0, harness 51.

A total below 187 means a module compiled but was not wired into `check` — investigate before declaring success. A total above 187 means something is being counted twice.

- [ ] **Step 3: Verify the platform boundary holds**

```bash
cd Legendary
./gradlew :core:checkNoPlatformImports > /tmp/boundary-final.log 2>&1
grep -E "BUILD (SUCCESSFUL|FAILED)" /tmp/boundary-final.log
grep -rl 'com\.hypixel' core/src | wc -l
```

Expected: `BUILD SUCCESSFUL` and `0`.

- [ ] **Step 4: Verify one dependency mechanism remains**

```bash
cd Legendary
grep -rn 'project(":' --include='*.gradle.kts' . | grep -v '/build/'
```

Expected: only `project(":core")` and `project(":quests:stormseeker")` edges. No `includeBuild`, no `dependencySubstitution`, no `mavenLocal`.

- [ ] **Step 5: Prepare the final commit (await authorization)**

```bash
git add -A
git commit -m "refactor: complete monorepo consolidation

Five repos to four modules. 187 tests preserved."
```

---

## Follow-ups — explicitly out of scope

Recorded so they are not silently lost:

- **Archive the four absorbed GitHub repos** (LegendaryCore, LegendaryContent, LegendaryDogfood, LegendaryHytale). Outward-facing action on the owner's org; requires explicit instruction.
- **Project C — documentation realignment.** Seven known contradictions, including `.planning/PROJECT.md` declaring a Kotlin stack for a zero-Kotlin codebase, and `Project_Dependencies.md` describing the submodule as a "vendored copy". Also `LegendaryHytale/README.md`, still the unmodified upstream template, and `docs/architecture/repository-structure.md`, which describes a three-repo project.
- **Project D — roadmap re-scope.** Phase 0/1/1.5 implementation vs. persistence-first. `StormseekerProgress` is in-memory only.
- **`platform/hytale` has zero tests** across 7 files — the least-tested and most game-churn-exposed module.
- **Unify harness package roots** — four roots coexist after Task 11.
- **`setPermissionGroup(GameMode)` is deprecated** and marked for removal in the current Hytale API.
- **`LegendaryCore/buildSrc/RunHytalePlugin.kt` was dropped, not ported.** It provided a
  `runServer` task for launching a dev server, but was never applied and never compiled.
  If an automated dev-server launch is wanted, it is new work belonging in
  `platform/hytale` — `LegendaryHytale/devserver/` suggests one already exists by another route.

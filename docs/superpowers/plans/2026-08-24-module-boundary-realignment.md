# Module Boundary Realignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each module's contents match its name, so adding questline #2 means adding `quests/<name>/` plus a registration line — and touching nothing else.

**Architecture:** Three moves. (1) The questline framework currently sitting inside `quests/stormseeker` moves to `core`, joining the shared encounter framework already there. (2) The questline registry stops hard-coding Stormseeker and becomes registration-based, driven from the composition root. (3) `platform/hytale` is renamed `mod/hytale`, because it is a mod entrypoint and not a platform layer — every one of its 8 files is either the plugin or Stormseeker-specific, and it implements no platform port. No new abstraction layers are invented; this is repair, not construction.

**Tech Stack:** Java 25 (`libs.versions.java`), Gradle 9.2 multi-project, JUnit 5, Error Prone 2.50.0, Spotless + palantirJavaFormat.

## Global Constraints

Derived by reading the gate (`./gradlew build`) rather than the change. Every task's requirements implicitly include this section.

- **The gate is `./gradlew build` from the repo root.** Its verdict line is `BUILD SUCCESSFUL`. An absent verdict line is a failure, not a pass.
- **Gradle caches test results.** `./gradlew build` alone may not re-run tests. Use `./gradlew build --rerun-tasks` when the test census matters, and read the census script's freshness line.
- **Test-count invariant: 187 tests, 0 failures** at plan start — core 63, quests:stormseeker 73, platform/mod:hytale 0, harness 51. Pure-move tasks must leave this number *unchanged*. Tasks that add tests must state the new expected number explicitly.
- **`:core:checkNoPlatformImports` must stay green.** It fails if any file under `core/src` contains `com.hypixel.`.
- **Run `./gradlew spotlessApply` before every commit.** Spotless is configured on `core`, `quests:stormseeker`, and root `*.gradle.kts` only — **not** on `harness` or `platform/hytale`. Files moved *into* an unformatted module will not be format-checked; files moved *into* `core` will be.
- **Error Prone runs on `core` and `quests:stormseeker` only**, with `EqualsHashCode` and `MissingOverride` at ERROR. Code moving into `core` is newly subject to it.
- **Package names are preserved wherever possible.** Only the paths listed per task change. Do not opportunistically rename classes — the 187 invariant is only meaningful if the diff is a move.
- **A move breaks unqualified same-package references, and a grep for the fully-qualified name will not find them.** Before moving any class out of a package, list every file *in that same package* that names it — those compile today with no import and fail with `cannot find symbol` once the class leaves. Learned the expensive way in Task 2: the plan's qualified-name grep missed 7 such files, the build failed with 12 errors, and each needed an explicit import added. Check with:
  ```bash
  bash -c "grep -rl '\bClassName\b' --include='*.java' . | xargs grep -l '^package <the.old.package>;'"
  ```
- **Commit per task**, not per file.

## Non-goals (stated so they are not silently absorbed)

- **No Hytale platform layer is built.** `core.api.platform.CoreRuntime` has exactly one implementation, `DefaultCoreRuntime`, inside `core` itself; nothing in Hytale implements it. Writing one is new construction and is deferred. **Trigger to revisit: a second platform target appears, or `mod/hytale` needs to run headless in tests.**
- **`mod/hytale` keeps zero tests.** It has none today and this plan adds none — a test there needs the game jar, and inventing a token test would misrepresent coverage. **Trigger: the module exceeds ~15 files, or a bug is found in it.**
- **The `legendary.*` encounter framework stays in `core`.** Operator confirmed 2026-08-24 it is shared infrastructure — reusable encounter venues for future questlines — not a game mode. It is where it belongs.
- **`harness/src/main` content split and `buildSrc` conventions are out of scope.** They belong with the existing shared-build-logic item on the roadmap.

---

### Task 1: Make the test-count invariant executable

The 187 figure is currently a sentence in a doc. Every later task verifies against it, so it needs to be a command that prints a verdict line.

**Files:**
- Create: `scripts/test-census.py`

**Interfaces:**
- Produces: `scripts/test-census.py`, run as `python3 scripts/test-census.py`. Exits 0 when total matches the expected count and failures are 0; exits 1 otherwise. Final line is always `CENSUS_VERDICT: <GREEN|RED> | <n> tests | <f> failures | newest result <age>`.

- [ ] **Step 1: Write the script**

```python
#!/usr/bin/env python3
"""Counts JUnit tests per Gradle module from test-result XML.

The 187-test invariant is load-bearing during the module boundary realignment,
so it is executable rather than remembered. Prints a verdict LINE, not just an
exit code -- exit status is positional and any wrapper swallows it.
"""
import glob
import os
import re
import sys
import time

EXPECTED = int(os.environ.get("EXPECTED_TESTS", "187"))
MODULES = ["core", "quests/stormseeker", "platform/hytale", "mod/hytale", "harness"]

total = failed = 0
newest = 0.0
for module in MODULES:
    files = glob.glob(f"{module}/build/test-results/test/TEST-*.xml")
    if not files:
        print(f"{module:22} (no results)")
        continue
    tests = bad = 0
    for path in files:
        head = open(path, encoding="utf-8", errors="replace").read(2000)
        for pattern, target in (("tests", "t"), ("failures", "f"), ("errors", "f")):
            match = re.search(rf'{pattern}="(\d+)"', head)
            value = int(match.group(1)) if match else 0
            if target == "t":
                tests += value
            else:
                bad += value
        newest = max(newest, os.path.getmtime(path))
    print(f"{module:22} tests={tests:4} failures+errors={bad}  classes={len(files)}")
    total += tests
    failed += bad

age = "never run" if newest == 0 else f"{int(time.time() - newest)}s ago"
green = total == EXPECTED and failed == 0
print(f"{'TOTAL':22} tests={total:4} failures+errors={failed}  (expected {EXPECTED})")
print(f"CENSUS_VERDICT: {'GREEN' if green else 'RED'} | {total} tests | {failed} failures | newest result {age}")
sys.exit(0 if green else 1)
```

- [ ] **Step 2: Run the gate, then the census, to establish the baseline**

```bash
./gradlew build --rerun-tasks
python3 scripts/test-census.py
```

Expected: `BUILD SUCCESSFUL`, then `CENSUS_VERDICT: GREEN | 187 tests | 0 failures | newest result <n>s ago`.

If the count is not 187, **stop and report** — the baseline is wrong and every later task's verification is meaningless.

- [ ] **Step 3: Verify the script fails loudly when it should**

```bash
EXPECTED_TESTS=999 python3 scripts/test-census.py; echo "exit=$?"
```

Expected: `CENSUS_VERDICT: RED | 187 tests | ...` and `exit=1`. A guard is only trusted after it has been observed firing.

- [ ] **Step 4: Commit**

```bash
git add scripts/test-census.py
git commit -m "test: add executable test-count census with verdict line"
```

---

### Task 2: Move the questline framework from `quests/stormseeker` to `core`

Ten files under `quests/stormseeker` are questline infrastructure, not Stormseeker. Verified by reading imports: every one imports only `core.api.*` and JDK types. This is a pure move — no behavior changes, no test-count change.

**Files:**

Move (from `quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod/` to `core/src/main/java/io/github/legendaryforge/legendary/core/api/`):

| From | To |
|---|---|
| `questline/QuestlineModule.java` | `questline/QuestlineModule.java` |
| `questline/objective/QuestObjective.java` | `questline/objective/QuestObjective.java` |
| `questline/objective/ObjectiveStatus.java` | `questline/objective/ObjectiveStatus.java` |
| `runtime/ActivePlayerProvider.java` | `questline/runtime/ActivePlayerProvider.java` |
| `runtime/LegendarySystemRegistrar.java` | `questline/runtime/LegendarySystemRegistrar.java` |
| `runtime/LegendaryTickContext.java` | `questline/runtime/LegendaryTickContext.java` |
| `runtime/PlayerRef.java` | `questline/runtime/PlayerRef.java` |
| `item/LegendaryItemIdentity.java` | `item/LegendaryItemIdentity.java` |
| `item/LegendaryItemPolicy.java` | `item/LegendaryItemPolicy.java` |
| `item/LegendaryItemRole.java` | `item/LegendaryItemRole.java` |

- Modify: every file importing the ten above — in `quests/stormseeker/src/{main,test}`, `harness/src/{main,test}`, `platform/hytale/src/main`.

**Interfaces:**
- Consumes: `scripts/test-census.py` from Task 1.
- Produces: the ten types above at their new package paths:
  - `io.github.legendaryforge.legendary.core.api.questline.QuestlineModule`
  - `io.github.legendaryforge.legendary.core.api.questline.objective.{QuestObjective, ObjectiveStatus}`
  - `io.github.legendaryforge.legendary.core.api.questline.runtime.{ActivePlayerProvider, LegendarySystemRegistrar, LegendaryTickContext, PlayerRef}`
  - `io.github.legendaryforge.legendary.core.api.item.{LegendaryItemIdentity, LegendaryItemPolicy, LegendaryItemRole}`

  Class names, method signatures and visibility are unchanged. Only `package` and `import` lines differ.

- [ ] **Step 1: Confirm the move list is still exactly these ten files**

```bash
cd quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod
for f in questline/QuestlineModule.java questline/objective/*.java item/*.java \
         runtime/ActivePlayerProvider.java runtime/LegendarySystemRegistrar.java \
         runtime/LegendaryTickContext.java runtime/PlayerRef.java; do
  printf '%-45s hits=%s\n' "$f" "$(grep -cE 'Stormseeker|Flowing|Anchored|Sigil' "$f")"
done
```

Expected: every line `hits=0` **except** `questline/QuestlineModule.java hits=1`, whose single hit is the Javadoc phrase `(e.g., "stormseeker")` on the `id()` method — a documentation example, not a dependency. If any other file has a non-zero count, **stop and report**: the list has drifted and moving that file will not compile.

- [ ] **Step 2: Create the destination directories and move the files**

```bash
cd "$(git rev-parse --show-toplevel)"
SRC=quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod
DST=core/src/main/java/io/github/legendaryforge/legendary/core/api
mkdir -p "$DST/questline/objective" "$DST/questline/runtime" "$DST/item"

git mv "$SRC/questline/QuestlineModule.java"        "$DST/questline/QuestlineModule.java"
git mv "$SRC/questline/objective/QuestObjective.java" "$DST/questline/objective/QuestObjective.java"
git mv "$SRC/questline/objective/ObjectiveStatus.java" "$DST/questline/objective/ObjectiveStatus.java"
git mv "$SRC/runtime/ActivePlayerProvider.java"     "$DST/questline/runtime/ActivePlayerProvider.java"
git mv "$SRC/runtime/LegendarySystemRegistrar.java" "$DST/questline/runtime/LegendarySystemRegistrar.java"
git mv "$SRC/runtime/LegendaryTickContext.java"     "$DST/questline/runtime/LegendaryTickContext.java"
git mv "$SRC/runtime/PlayerRef.java"                "$DST/questline/runtime/PlayerRef.java"
git mv "$SRC/item/LegendaryItemIdentity.java"       "$DST/item/LegendaryItemIdentity.java"
git mv "$SRC/item/LegendaryItemPolicy.java"         "$DST/item/LegendaryItemPolicy.java"
git mv "$SRC/item/LegendaryItemRole.java"           "$DST/item/LegendaryItemRole.java"
```

If any `git mv` refuses, **stop and report** — do not force it. A refusal means a file already exists at the destination, which would mean a duplicate FQN.

- [ ] **Step 3: Rewrite the `package` declaration in each moved file**

```bash
cd "$(git rev-parse --show-toplevel)"
D=core/src/main/java/io/github/legendaryforge/legendary/core/api
sed -i 's|^package io\.github\.legendaryforge\.legendary\.mod\.questline\.objective;|package io.github.legendaryforge.legendary.core.api.questline.objective;|' $D/questline/objective/*.java
sed -i 's|^package io\.github\.legendaryforge\.legendary\.mod\.questline;|package io.github.legendaryforge.legendary.core.api.questline;|' $D/questline/QuestlineModule.java
sed -i 's|^package io\.github\.legendaryforge\.legendary\.mod\.runtime;|package io.github.legendaryforge.legendary.core.api.questline.runtime;|' $D/questline/runtime/*.java
sed -i 's|^package io\.github\.legendaryforge\.legendary\.mod\.item;|package io.github.legendaryforge.legendary.core.api.item;|' $D/item/*.java
grep -h '^package' $D/questline/*.java $D/questline/objective/*.java $D/questline/runtime/*.java $D/item/*.java
```

Expected: ten `package io.github.legendaryforge.legendary.core.api.…;` lines, no `legendary.mod` remaining.

- [ ] **Step 4: Rewrite imports repo-wide**

`QuestlineModule` imports `LegendarySystemRegistrar`, so it needs rewriting too — it is both a moved file and a consumer.

```bash
cd "$(git rev-parse --show-toplevel)"
FILES=$(grep -rl 'legendaryforge\.legendary\.mod\.\(questline\|runtime\|item\)\.' --include='*.java' core quests platform harness)
echo "$FILES" | tr ' ' '\n' | sed '/^$/d' | wc -l   # how many files will be touched

for F in $FILES; do
  sed -i \
    -e 's|legendaryforge\.legendary\.mod\.questline\.objective\.|legendaryforge.legendary.core.api.questline.objective.|g' \
    -e 's|legendaryforge\.legendary\.mod\.questline\.QuestlineModule|legendaryforge.legendary.core.api.questline.QuestlineModule|g' \
    -e 's|legendaryforge\.legendary\.mod\.runtime\.ActivePlayerProvider|legendaryforge.legendary.core.api.questline.runtime.ActivePlayerProvider|g' \
    -e 's|legendaryforge\.legendary\.mod\.runtime\.LegendarySystemRegistrar|legendaryforge.legendary.core.api.questline.runtime.LegendarySystemRegistrar|g' \
    -e 's|legendaryforge\.legendary\.mod\.runtime\.LegendaryTickContext|legendaryforge.legendary.core.api.questline.runtime.LegendaryTickContext|g' \
    -e 's|legendaryforge\.legendary\.mod\.runtime\.PlayerRef|legendaryforge.legendary.core.api.questline.runtime.PlayerRef|g' \
    -e 's|legendaryforge\.legendary\.mod\.item\.|legendaryforge.legendary.core.api.item.|g' \
    "$F"
done
```

Note the `runtime.*` rules are **per class, not a prefix sweep** — `mod.runtime` still legitimately holds six Stormseeker files (`FlowingTrialHostTick`, `AnchoredTrialHostTick`, `AnchoredTrialHostDriver`, `FlowingTrialHostDriver`, `StormseekerHostRuntime`, `StormseekerProgressStore`) plus `FlowHintSink` and `MotionSampleSource`, which Task 4 handles. A blanket `mod.runtime.` → `core.api.questline.runtime.` rewrite would break all eight.

- [ ] **Step 5: Format, then run the gate and the census**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
python3 scripts/test-census.py
```

Expected: `BUILD SUCCESSFUL`, then `CENSUS_VERDICT: GREEN | 187 tests | 0 failures | …`.

A move must not change the count. If it did, something was deleted or a test class stopped being discovered — **stop and report**, do not adjust `EXPECTED_TESTS`.

- [ ] **Step 6: Verify no stale references survive**

```bash
grep -rn 'legendary\.mod\.\(questline\.objective\|item\)\.' --include='*.java' core quests platform harness; echo "exit=$?"
```

Expected: no output, `exit=1` (grep found nothing).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(core): move questline framework out of quests/stormseeker

Ten files -- QuestlineModule, QuestObjective, ObjectiveStatus, the four
generic runtime seams, and the three item types -- were questline
infrastructure living inside the one questline that used them. Questline #2
would have had to depend on :quests:stormseeker to reach them.

Pure move: 187 tests before and after."
```

---

### Task 3: Make the questline registry registration-based

`Questlines.ALL` is `List.of(new StormseekerQuestline())` and `LegendaryConfig.defaults()` is `Map.of(StormseekerQuestline.ID, true)`. Both name the one questline, so neither can move to `core` and neither survives contact with questline #2. This task inverts them: `core` owns the registry, the composition root says what goes in it.

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/questline/QuestlineRegistry.java`
- Create: `core/src/test/java/io/github/legendaryforge/legendary/core/api/questline/QuestlineRegistryTest.java`
- Move + rewrite: `quests/stormseeker/…/legendary/mod/LegendaryConfig.java` → `core/…/core/api/questline/LegendaryConfig.java`
- Move + rewrite: `quests/stormseeker/…/legendary/mod/LegendaryWiring.java` → `core/…/core/api/questline/LegendaryWiring.java`
- Delete: `quests/stormseeker/…/legendary/mod/questline/Questlines.java` (replaced by `QuestlineRegistry`)
- Modify: `platform/hytale/src/main/java/io/github/legendaryforge/hytale/LegendaryHytalePlugin.java` — register Stormseeker

**Interfaces:**
- Consumes: `QuestlineModule` at `core.api.questline.QuestlineModule` (Task 2).
- Produces:
  - `QuestlineRegistry` — instance class, not static. `QuestlineRegistry register(QuestlineModule module)` (returns `this`, throws `IllegalArgumentException` on duplicate `id()`, `NullPointerException` on null); `List<QuestlineModule> all()` (unmodifiable, registration order).
  - `LegendaryConfig` — record `LegendaryConfig(Map<String, Boolean> questlinesEnabled)`. `defaults()` is **removed**; new static `LegendaryConfig enablingAll(QuestlineRegistry registry)`. `allDisabled()`, `of(Map)` and `isEnabled(String)` keep their existing signatures.
  - `LegendaryWiring` — the three `registerAll*` methods each gain a `QuestlineRegistry` first parameter: `registerAllGates(QuestlineRegistry, GateService, LegendaryConfig)`, `registerAllSystems(QuestlineRegistry, LegendarySystemRegistrar, LegendaryConfig)`, `registerAllListeners(QuestlineRegistry, EventBus, LegendaryConfig)`. **The single-argument convenience overloads are removed** — they existed only to call `LegendaryConfig.defaults()`.

- [ ] **Step 1: Write the failing test**

Create `core/src/test/java/io/github/legendaryforge/legendary/core/api/questline/QuestlineRegistryTest.java`:

```java
package io.github.legendaryforge.legendary.core.api.questline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.gate.GateService;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuestlineRegistryTest {

    private static QuestlineModule module(String id) {
        return new QuestlineModule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String displayName() {
                return id;
            }

            @Override
            public void registerGates(GateService gates) {
                // no-op for registry tests
            }
        };
    }

    @Test
    void emptyRegistryHasNoQuestlines() {
        assertEquals(List.of(), new QuestlineRegistry().all());
    }

    @Test
    void registrationOrderIsPreserved() {
        QuestlineRegistry registry =
                new QuestlineRegistry().register(module("alpha")).register(module("beta"));
        assertEquals(List.of("alpha", "beta"), registry.all().stream().map(QuestlineModule::id).toList());
    }

    @Test
    void duplicateIdIsRejected() {
        QuestlineRegistry registry = new QuestlineRegistry().register(module("alpha"));
        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> registry.register(module("alpha")));
        assertTrue(thrown.getMessage().contains("alpha"));
    }

    @Test
    void nullModuleIsRejected() {
        assertThrows(NullPointerException.class, () -> new QuestlineRegistry().register(null));
    }

    @Test
    void allIsUnmodifiable() {
        QuestlineRegistry registry = new QuestlineRegistry().register(module("alpha"));
        assertThrows(UnsupportedOperationException.class, () -> registry.all().add(module("beta")));
    }

    @Test
    void enablingAllTurnsOnEveryRegisteredQuestline() {
        QuestlineRegistry registry =
                new QuestlineRegistry().register(module("alpha")).register(module("beta"));
        LegendaryConfig config = LegendaryConfig.enablingAll(registry);
        assertTrue(config.isEnabled("alpha"));
        assertTrue(config.isEnabled("beta"));
    }

    @Test
    void unregisteredQuestlineIsDisabled() {
        LegendaryConfig config = LegendaryConfig.enablingAll(new QuestlineRegistry().register(module("alpha")));
        assertTrue(!config.isEnabled("gamma"));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

```bash
./gradlew :core:test --tests '*QuestlineRegistryTest*'
```

Expected: FAIL — compilation error, `QuestlineRegistry` does not exist.

- [ ] **Step 3: Write `QuestlineRegistry`**

Create `core/src/main/java/io/github/legendaryforge/legendary/core/api/questline/QuestlineRegistry.java`:

```java
package io.github.legendaryforge.legendary.core.api.questline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Holds the questlines a running server ships.
 *
 * <p>Deliberately an instance rather than a static list: core must not name any questline, so
 * membership is decided by the composition root (the platform mod entrypoint) and passed in.
 * Adding a questline is a {@code register} call there, not an edit here.
 */
public final class QuestlineRegistry {

    private final List<QuestlineModule> modules = new ArrayList<>();

    public QuestlineRegistry register(QuestlineModule module) {
        Objects.requireNonNull(module, "module");
        String id = Objects.requireNonNull(module.id(), "module.id()");
        for (QuestlineModule existing : modules) {
            if (existing.id().equals(id)) {
                throw new IllegalArgumentException("Questline id already registered: " + id);
            }
        }
        modules.add(module);
        return this;
    }

    /** Registered questlines, in registration order. */
    public List<QuestlineModule> all() {
        return Collections.unmodifiableList(modules);
    }
}
```

- [ ] **Step 4: Move and rewrite `LegendaryConfig`**

```bash
cd "$(git rev-parse --show-toplevel)"
git mv quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod/LegendaryConfig.java \
       core/src/main/java/io/github/legendaryforge/legendary/core/api/questline/LegendaryConfig.java
```

Then replace its contents with:

```java
package io.github.legendaryforge.legendary.core.api.questline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal configuration surface for the Legendary mod.
 *
 * <p>This is intentionally in-memory only for now (no file parsing yet).
 */
public record LegendaryConfig(Map<String, Boolean> questlinesEnabled) {

    public LegendaryConfig {
        questlinesEnabled = Map.copyOf(questlinesEnabled);
    }

    /** Enables every questline in {@code registry}. Replaces the former hard-coded {@code defaults()}. */
    public static LegendaryConfig enablingAll(QuestlineRegistry registry) {
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (QuestlineModule module : registry.all()) {
            enabled.put(module.id(), true);
        }
        return new LegendaryConfig(enabled);
    }

    public static LegendaryConfig allDisabled() {
        return new LegendaryConfig(Map.of());
    }

    public static LegendaryConfig of(Map<String, Boolean> questlinesEnabled) {
        return new LegendaryConfig(questlinesEnabled);
    }

    public boolean isEnabled(String questlineId) {
        return questlinesEnabled.getOrDefault(questlineId, false);
    }
}
```

- [ ] **Step 5: Move and rewrite `LegendaryWiring`, delete `Questlines`**

```bash
cd "$(git rev-parse --show-toplevel)"
git mv quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod/LegendaryWiring.java \
       core/src/main/java/io/github/legendaryforge/legendary/core/api/questline/LegendaryWiring.java
git rm quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod/questline/Questlines.java
```

Then replace `LegendaryWiring`'s contents with:

```java
package io.github.legendaryforge.legendary.core.api.questline;

import io.github.legendaryforge.legendary.core.api.event.EventBus;
import io.github.legendaryforge.legendary.core.api.gate.GateService;
import io.github.legendaryforge.legendary.core.api.questline.runtime.LegendarySystemRegistrar;

/**
 * Mod-level wiring entrypoint for Legendary.
 *
 * <p>Questlines come from the {@link QuestlineRegistry} the caller supplies, so core never names
 * one. The former no-config overloads are gone: they called a hard-coded {@code defaults()}, which
 * is what tied this class to Stormseeker.
 */
public final class LegendaryWiring {

    private LegendaryWiring() {
        // static utility
    }

    public static void registerAllGates(QuestlineRegistry registry, GateService gates, LegendaryConfig config) {
        for (QuestlineModule questline : registry.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerGates(gates);
            }
        }
    }

    public static void registerAllSystems(
            QuestlineRegistry registry, LegendarySystemRegistrar registrar, LegendaryConfig config) {
        for (QuestlineModule questline : registry.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerSystems(registrar);
            }
        }
    }

    public static void registerAllListeners(QuestlineRegistry registry, EventBus bus, LegendaryConfig config) {
        for (QuestlineModule questline : registry.all()) {
            if (config.isEnabled(questline.id())) {
                questline.registerListeners(bus);
            }
        }
    }
}
```

- [ ] **Step 6: Update the two call sites**

The framework has exactly three call sites, all in `quests/stormseeker/src/test/.../stormseeker/harness/`. Verified 2026-08-24 by grepping `Questlines.|LegendaryWiring.|LegendaryConfig.` across all four modules. `LegendaryHytalePlugin` is **not** among them — see this task's scope note below.

In `LegendaryWiringRegistersStormseekerGatesTest.java`, replace the imports

```java
import io.github.legendaryforge.legendary.mod.LegendaryWiring;
```

with

```java
import io.github.legendaryforge.legendary.core.api.questline.LegendaryConfig;
import io.github.legendaryforge.legendary.core.api.questline.LegendaryWiring;
import io.github.legendaryforge.legendary.core.api.questline.QuestlineRegistry;
import io.github.legendaryforge.legendary.mod.questline.StormseekerQuestline;
```

and replace the single line `LegendaryWiring.registerAllGates(gates);` with

```java
        QuestlineRegistry questlines = new QuestlineRegistry().register(new StormseekerQuestline());
        LegendaryWiring.registerAllGates(questlines, gates, LegendaryConfig.enablingAll(questlines));
```

In `LegendaryWiringQuestlineToggleTest.java`, replace the imports

```java
import io.github.legendaryforge.legendary.mod.LegendaryConfig;
import io.github.legendaryforge.legendary.mod.LegendaryWiring;
```

with

```java
import io.github.legendaryforge.legendary.core.api.questline.LegendaryConfig;
import io.github.legendaryforge.legendary.core.api.questline.LegendaryWiring;
import io.github.legendaryforge.legendary.core.api.questline.QuestlineRegistry;
```

(keep its existing `import ...legendary.mod.questline.StormseekerQuestline;`) and replace

```java
        LegendaryConfig config = LegendaryConfig.of(Map.of(StormseekerQuestline.ID, false));
        LegendaryWiring.registerAllGates(gates, config);
```

with

```java
        QuestlineRegistry questlines = new QuestlineRegistry().register(new StormseekerQuestline());
        LegendaryConfig config = LegendaryConfig.of(Map.of(StormseekerQuestline.ID, false));
        LegendaryWiring.registerAllGates(questlines, gates, config);
```

Both tests keep their existing assertions unchanged — the toggle test still expects `legendarycore:gate_not_registered`, and the registration test still expects a `stormseeker`-namespaced reason code.

- [ ] **Step 7: Confirm no other caller appeared**

```bash
cd "$(git rev-parse --show-toplevel)"
grep -rn 'Questlines\.\|legendary\.mod\.LegendaryWiring\|legendary\.mod\.LegendaryConfig' --include='*.java' core quests platform harness
```

Expected: no output. If anything appears, **stop and report** — a call site existed that this task did not account for.

- [ ] **Step 8: Run the gate and the census**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
EXPECTED_TESTS=194 python3 scripts/test-census.py
```

Expected: `BUILD SUCCESSFUL`, then `CENSUS_VERDICT: GREEN | 194 tests | 0 failures | …` — 187 plus the 7 new `QuestlineRegistryTest` cases.

The two migrated tests are the behavioral check: `LegendaryWiringRegistersStormseekerGatesTest` must still find a `stormseeker`-namespaced gate, and `LegendaryWiringQuestlineToggleTest` must still get `gate_not_registered` when the questline is toggled off. If either flips, the registry inversion changed behavior — **stop and report**.

- [ ] **Step 9: Update the census baseline and commit**

Change `EXPECTED` in `scripts/test-census.py` from `187` to `194`.

```bash
git add -A
git commit -m "refactor(core): make the questline registry registration-based

Questlines.ALL hard-coded new StormseekerQuestline() and
LegendaryConfig.defaults() hard-coded its id, so neither could live in core
and neither survived a second questline. QuestlineRegistry replaces both:
core now owns the questline framework and names no questline.

Scope note: LegendaryHytalePlugin is deliberately NOT migrated. It does not
use this framework today -- it wires Stormseeker imperatively -- and routing
it through LegendaryWiring would activate scaffold paths
(StormseekerWiring.registerSystems is a no-op; registerListeners constructs
two services with a null world). See Task 8 for the specified migration.

187 -> 194 tests (7 new registry tests)."
```

#### Scope note: why the plugin is not migrated here

`LegendaryHytalePlugin` never calls `LegendaryWiring`, `Questlines`, or `LegendaryConfig`. It constructs `HytaleStormseekerHost`, `StormseekerTickSystem` and the three commands directly. Routing it through the framework in this task was considered and rejected on 2026-08-24 after reading `StormseekerWiring`:

- `StormseekerWiring.registerSystems(LegendarySystemRegistrar)` is an explicit no-op (`// Intentionally no-op in Phase C scaffold.`), so the framework has no path for the tick system that actually drives Phases 1, 1.5 and 2.
- `StormseekerWiring.registerListeners(EventBus)` is currently dormant — nothing calls it. Migrating would newly construct `StormseekerAttunementService(bus, null)` and `StormseekerTrekSystem(bus, null)`, both passing `null` where the comments say a host-provided world belongs, and would subscribe `StormseekerLifecycleBridge` to live `EncounterStartedEvent`s for the first time.
- The `AttunementCompleteEvent` handler body is a comment; the Phase 1.5 → Phase 2 handshake is unimplemented.

Migrating now would wire a coherent framework to a half-built implementation and activate dormant paths in the only module with no tests. Task 8 specifies the migration so it is ready to execute once those preconditions are met.

---

### Task 4: Repackage the two seams that are not generic

`FlowHintSink` and `MotionSampleSource` sit in the generic-sounding `mod/runtime` package but import Stormseeker types (`FlowHintIntent`, `FlowingTrialStatus`, `MotionSample`). They are Stormseeker ports and stayed behind in Task 2. Move them next to the types they speak, so the package name stops implying they are shared.

**Files:**
- Move: `quests/stormseeker/…/legendary/mod/runtime/FlowHintSink.java` → `…/legendary/mod/stormseeker/trial/flowing/FlowHintSink.java`
- Move: `quests/stormseeker/…/legendary/mod/runtime/MotionSampleSource.java` → `…/legendary/mod/stormseeker/trial/flowing/MotionSampleSource.java`
- Modify: consumers of both, in `quests/stormseeker/src/{main,test}`, `harness/src`, `platform/hytale/src`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing.{FlowHintSink, MotionSampleSource}`. Method signatures unchanged — `FlowHintSink.emit(PlayerRef, FlowHintIntent, FlowingTrialStatus)` and `MotionSampleSource`'s existing surface. Note `PlayerRef` now resolves to `core.api.questline.runtime.PlayerRef` (Task 2).

- [ ] **Step 0: Confirm no same-package caller will break**

Per the global constraint on unqualified references, check who names these two classes from inside `legendary.mod.runtime`:

```bash
cd "$(git rev-parse --show-toplevel)"
bash -c "grep -rl 'FlowHintSink\|MotionSampleSource' --include='*.java' core quests harness platform | xargs grep -l '^package io.github.legendaryforge.legendary.mod.runtime;'"
```

Expected: exactly the two files being moved, and nothing else. Verified 2026-08-24 — their four real consumers (`FlowingTrialRuntimeOrchestrator`, `AnchoredTrialRuntimeOrchestrator`, and both orchestrators' tests) sit in *sub*packages, so they already import the classes qualified and Step 3's rewrite covers them. If any third file appears, it is an unqualified same-package caller and needs an explicit import added.

- [ ] **Step 1: Move the files and fix their package lines**

```bash
cd "$(git rev-parse --show-toplevel)"
SRC=quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod
git mv "$SRC/runtime/FlowHintSink.java"       "$SRC/stormseeker/trial/flowing/FlowHintSink.java"
git mv "$SRC/runtime/MotionSampleSource.java" "$SRC/stormseeker/trial/flowing/MotionSampleSource.java"
sed -i 's|^package io\.github\.legendaryforge\.legendary\.mod\.runtime;|package io.github.legendaryforge.legendary.mod.stormseeker.trial.flowing;|' \
  "$SRC/stormseeker/trial/flowing/FlowHintSink.java" "$SRC/stormseeker/trial/flowing/MotionSampleSource.java"
```

- [ ] **Step 2: Drop the now-redundant same-package imports**

Both files imported `FlowHintIntent`, `FlowingTrialStatus`, and `MotionSample` from the package they now live in. Spotless's `removeUnusedImports()` will not remove these (they are used), but javac will reject same-package imports as redundant only under some settings — remove them by hand to keep the files clean:

```bash
cd "$(git rev-parse --show-toplevel)"
D=quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/trial/flowing
sed -i '/^import io\.github\.legendaryforge\.legendary\.mod\.stormseeker\.trial\.flowing\./d' \
  "$D/FlowHintSink.java" "$D/MotionSampleSource.java"
```

Both still need `import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;` — verify it is present in `FlowHintSink.java` and add it if Task 2's rewrite did not.

- [ ] **Step 3: Rewrite consumers**

```bash
cd "$(git rev-parse --show-toplevel)"
grep -rl 'legendary\.mod\.runtime\.\(FlowHintSink\|MotionSampleSource\)' --include='*.java' core quests platform harness \
  | xargs -r sed -i \
    -e 's|legendary\.mod\.runtime\.FlowHintSink|legendary.mod.stormseeker.trial.flowing.FlowHintSink|g' \
    -e 's|legendary\.mod\.runtime\.MotionSampleSource|legendary.mod.stormseeker.trial.flowing.MotionSampleSource|g'
```

- [ ] **Step 4: Run the gate and the census**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
python3 scripts/test-census.py
```

Expected: `BUILD SUCCESSFUL`, `CENSUS_VERDICT: GREEN | 194 tests | 0 failures | …`. A pure move — the count must not change.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(stormseeker): move FlowHintSink and MotionSampleSource beside their types

Both sat in the generic-sounding mod/runtime package while importing
Stormseeker trial types. The package name implied shared infrastructure that
neither file provides."
```

---

### Task 5: Rename `platform/hytale` to `mod/hytale`

The module holds 8 files: `LegendaryHytalePlugin` and 7 Stormseeker-specific ones (3 commands, `HytaleStormseekerHost`, `HytaleWeatherReader`, `StormseekerProgressStore`, `StormseekerTickSystem`). It implements no platform port — `core.api.platform.CoreRuntime`'s only implementation is `DefaultCoreRuntime`, inside `core`. It is a mod entrypoint. Naming it so makes its dependency on `:quests:stormseeker` honest instead of a boundary violation.

Java package names are already `io.github.legendaryforge.hytale.*` with no `platform` segment, so **no package rename is needed** — this is a directory and Gradle-path change only.

**Files:**
- Modify: `settings.gradle.kts`
- Move: `platform/hytale/` → `mod/hytale/` (whole directory)
- Modify: `mod/hytale/build.gradle.kts` — comment only
- Modify: `core/build.gradle.kts` — the `checkNoPlatformImports` failure message names `:platform:hytale`

**Interfaces:**
- Consumes: `QuestlineRegistry` registration added in Task 3 Step 7 — it moves with the file.
- Produces: Gradle project path `:mod:hytale`. No Java type changes.

- [ ] **Step 1: Move the directory and update `settings.gradle.kts`**

```bash
cd "$(git rev-parse --show-toplevel)"
mkdir -p mod
git mv platform/hytale mod/hytale
rmdir platform 2>/dev/null || echo "NOTE: platform/ not empty, inspect before removing"
sed -i 's|include(":platform:hytale")|include(":mod:hytale")|' settings.gradle.kts
cat settings.gradle.kts
```

Expected: `include(":mod:hytale")` present, no `:platform:hytale`, and `platform/` gone. If `rmdir` reported the directory is not empty, **stop and report** — something unaccounted-for lived there.

- [ ] **Step 2: Update the two places that name the old path in prose**

In `core/build.gradle.kts`, the `checkNoPlatformImports` failure message ends `"Platform-specific code belongs in :platform:hytale."` — change to `":mod:hytale"`. The task name itself stays `checkNoPlatformImports`; it describes what it checks (platform imports), not where they go.

In `mod/hytale/build.gradle.kts`, update the header comment `// --- Hytale install detection (Linux) ---` block's surrounding context only if it names the module path. Leave the detection logic, the `checkHytaleJarVersion` task, and the `api(project(":quests:stormseeker"))` dependency exactly as they are — that dependency is now correct rather than merely tolerated.

- [ ] **Step 3: Update the census script's module list**

`scripts/test-census.py` already lists both `platform/hytale` and `mod/hytale` in `MODULES`, so it keeps working. Remove the now-dead `"platform/hytale"` entry to stop it printing `(no results)` forever.

- [ ] **Step 4: Run the gate and the census**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
python3 scripts/test-census.py
./gradlew projects | grep -i hytale
```

Expected: `BUILD SUCCESSFUL`; `CENSUS_VERDICT: GREEN | 194 tests | 0 failures | …`; `./gradlew projects` shows `Project ':mod:hytale'` and no `:platform:hytale`.

- [ ] **Step 5: Verify the jar-version guard still fires from its new home**

The guard is the only thing standing between a Hytale auto-update and a 23-error cascade, and it has just moved. Confirm it is still wired:

```bash
./gradlew :mod:hytale:checkHytaleJarVersion --info 2>&1 | grep -iE 'checkHytaleJarVersion|SKIPPED|UP-TO-DATE|BUILD'
```

Expected: the task runs (or is skipped by `onlyIf` on a machine with no game installed) and the build succeeds. If the task cannot be found, **stop and report** — the rename broke the guard.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: rename platform/hytale to mod/hytale

The module never was a platform layer: all 8 files are the plugin entrypoint
or Stormseeker-specific, and it implements no core.api.platform port. Its
dependency on :quests:stormseeker was reported as a boundary violation when
the real problem was the name. No Java packages change -- they were already
io.github.legendaryforge.hytale.*.

A genuine Hytale platform layer (a CoreRuntime implementation) is deliberately
not built here; see the plan's non-goals."
```

---

### Task 6: Make the new boundary executable

`core` is now supposed to contain no questline-specific code. That is a claim, and claims that matter should fail the build rather than sit in a doc — the same reasoning behind the existing `:core:checkNoPlatformImports`.

**Files:**
- Modify: `core/build.gradle.kts`

**Interfaces:**
- Produces: Gradle task `:core:checkNoQuestlineImports`, wired into `:core:check`.

- [ ] **Step 1: Add the check**

Append to `core/build.gradle.kts`, immediately after the existing `checkNoPlatformImports` block:

```kotlin
val checkNoQuestlineImports by tasks.registering {
    group = "verification"
    description = "Fails if core references any specific questline. core hosts the questline framework, not questlines."
    val javaSources = fileTree("src") { include("**/*.java") }
    inputs.files(javaSources)
    outputs.upToDateWhen { false }
    doLast {
        val offenders = javaSources.files.filter { it.readText().contains("legendary.mod.") }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("core must not reference a specific questline, but ${offenders.size} file(s) do:")
                    offenders.forEach { appendLine("  " + it.relativeTo(projectDir)) }
                    appendLine("Questline code belongs in :quests:<name>; register it from :mod:hytale.")
                },
            )
        }
    }
}

tasks.named("check") { dependsOn(checkNoQuestlineImports) }
```

`legendary.mod.` is the marker because every questline lives under `io.github.legendaryforge.legendary.mod.*`, and after Task 2 no `core` file should mention it.

- [ ] **Step 2: Run it and confirm it passes**

```bash
./gradlew :core:checkNoQuestlineImports
```

Expected: `BUILD SUCCESSFUL`. If it fails, it has found a real leftover from Task 2 or 3 — fix the file, do not weaken the check.

- [ ] **Step 3: Observe it firing**

A guard is only trusted after it has been seen to fail.

```bash
cd "$(git rev-parse --show-toplevel)"
printf '\n// import io.github.legendaryforge.legendary.mod.questline.StormseekerQuestline;\n' \
  >> core/src/main/java/io/github/legendaryforge/legendary/core/api/questline/QuestlineRegistry.java
./gradlew :core:checkNoQuestlineImports 2>&1 | tail -6
```

Expected: `BUILD FAILED`, naming `QuestlineRegistry.java`. Note it fires on a *commented-out* reference — the check is textual, which is deliberate: it costs nothing and catches the case where someone re-enables the line later.

Then revert:

```bash
git checkout core/src/main/java/io/github/legendaryforge/legendary/core/api/questline/QuestlineRegistry.java
./gradlew :core:checkNoQuestlineImports
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the full gate and commit**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
python3 scripts/test-census.py
git add -A
git commit -m "build(core): fail the build if core references a specific questline

Mirrors checkNoPlatformImports. The boundary the realignment established is
now enforced rather than documented; verified firing before commit."
```

---

### Task 7 (optional): Remove the downward type-check in `DefaultEncounterManager`

`DefaultEncounterManager.java:92` does a fully-qualified `instanceof LegendaryEncounterDefinition` to decide whether to capture owner-party membership. Generic encounter code branching on a specific definition subtype is backwards: the definition should declare the capability. Both types live in `core`, so this is core-internal tidiness rather than a module boundary problem — run it only if the first six tasks landed cleanly.

**Files:**
- Modify: `core/src/main/java/io/github/legendaryforge/legendary/core/api/encounter/EncounterDefinition.java`
- Modify: `core/src/main/java/io/github/legendaryforge/legendary/core/api/legendary/definition/LegendaryEncounterDefinition.java`
- Modify: `core/src/main/java/io/github/legendaryforge/legendary/core/internal/encounter/DefaultEncounterManager.java:91-100`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/encounter/OwnerPartyCaptureTest.java` (create)

**Interfaces:**
- Produces: `EncounterDefinition.capturesOwnerParty()` — `default boolean`, returns `false`. `LegendaryEncounterDefinition` overrides it to return `true`. Behavior is identical to the current `instanceof`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.legendaryforge.legendary.core.internal.encounter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.legendaryforge.legendary.core.api.encounter.EncounterAccessPolicy;
import io.github.legendaryforge.legendary.core.api.encounter.EncounterDefinition;
import io.github.legendaryforge.legendary.core.api.encounter.SpectatorPolicy;
import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition;
import io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterId;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OwnerPartyCaptureTest {

    @Test
    void plainDefinitionsDoNotCaptureOwnerParty() {
        assertFalse(plain().capturesOwnerParty());
    }

    @Test
    void legendaryDefinitionsCaptureOwnerParty() {
        assertTrue(legendary().capturesOwnerParty());
    }

    private static EncounterDefinition plain() {
        return new EncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:plain");
            }

            @Override
            public String displayName() {
                return "plain";
            }

            @Override
            public EncounterAccessPolicy accessPolicy() {
                return EncounterAccessPolicy.PUBLIC;
            }

            @Override
            public SpectatorPolicy spectatorPolicy() {
                return SpectatorPolicy.ALLOW_VIEW_ONLY;
            }

            @Override
            public int maxParticipants() {
                return 4;
            }

            @Override
            public int maxSpectators() {
                return 4;
            }
        };
    }

    private static LegendaryEncounterDefinition legendary() {
        return new LegendaryEncounterDefinition() {
            @Override
            public ResourceId id() {
                return ResourceId.parse("test:legendary");
            }

            @Override
            public String displayName() {
                return "legendary";
            }

            @Override
            public EncounterAccessPolicy accessPolicy() {
                return EncounterAccessPolicy.PUBLIC;
            }

            @Override
            public SpectatorPolicy spectatorPolicy() {
                return SpectatorPolicy.ALLOW_VIEW_ONLY;
            }

            @Override
            public int maxParticipants() {
                return 4;
            }

            @Override
            public int maxSpectators() {
                return 4;
            }

            @Override
            public LegendaryEncounterId legendaryId() {
                return LEGENDARY_ID;
            }

            @Override
            public Optional<String> description() {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> completionCooldown() {
                return Optional.empty();
            }
        };
    }
}
```

**`LEGENDARY_ID` above is the one value this test cannot be written blind.** Before writing the file, read
`core/src/main/java/io/github/legendaryforge/legendary/core/api/legendary/definition/LegendaryEncounterId.java`
and construct one using whatever factory or constructor it exposes (it may be a record, an enum, or a
`parse`-style factory). Replace `LEGENDARY_ID` with that expression. The anonymous-class style above matches
the existing `def(...)` helpers in `DefaultEncounterManagerCapacityTest` and its seven siblings — follow it
rather than introducing a shared fixture, since none exists today.

- [ ] **Step 2: Run it to make sure it fails**

```bash
./gradlew :core:test --tests '*OwnerPartyCaptureTest*'
```

Expected: FAIL — `capturesOwnerParty()` does not exist.

- [ ] **Step 3: Add the capability method**

In `EncounterDefinition`:

```java
/**
 * Whether creating an instance of this encounter should capture the activating party's
 * membership at start. Default false; richer encounter models override.
 */
default boolean capturesOwnerParty() {
    return false;
}
```

In `LegendaryEncounterDefinition`:

```java
@Override
default boolean capturesOwnerParty() {
    return true;
}
```

- [ ] **Step 4: Replace the `instanceof`**

In `DefaultEncounterManager`, replace the block at lines 91-100:

```java
            if (definition
                    instanceof
                    io.github.legendaryforge.legendary.core.api.legendary.definition.LegendaryEncounterDefinition) {
                ownerPartyId = context.partyId();
```

with:

```java
            if (definition.capturesOwnerParty()) {
                ownerPartyId = context.partyId();
```

The rest of the block, including the `parties.get().members(...)` lookup, is unchanged.

- [ ] **Step 5: Run the gate and the census**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
EXPECTED_TESTS=196 python3 scripts/test-census.py
```

Expected: `BUILD SUCCESSFUL`, `CENSUS_VERDICT: GREEN | 196 tests | 0 failures | …` — 194 plus 2.

The pre-existing encounter tests are the real check here: this is a behavior-preserving change, so any of them going red means the `instanceof` and the new method are not equivalent.

- [ ] **Step 6: Update the baseline and commit**

Change `EXPECTED` in `scripts/test-census.py` to `196`.

```bash
git add -A
git commit -m "refactor(core): replace instanceof downcast with a capability method

DefaultEncounterManager branched on instanceof LegendaryEncounterDefinition
to decide owner-party capture -- generic code type-checking a specific
subtype. EncounterDefinition.capturesOwnerParty() inverts it.

194 -> 196 tests. Behavior-preserving; existing encounter tests are the check."
```

---

---

### Task 8: Specify the plugin migration (documentation only — no code)

Task 3 deliberately left `LegendaryHytalePlugin` on its hard-coded wiring. That decision is only sound if the migration is *specified* rather than remembered, so it can be executed the moment its preconditions hold. This task writes that specification. **It changes no Java and no build file.**

**Files:**
- Create: `docs/architecture/questline-framework-adoption.md`

**Interfaces:**
- Consumes: `QuestlineRegistry`, `LegendaryConfig.enablingAll`, `LegendaryWiring.registerAll*` as defined in Task 3.
- Produces: no code. A document other work reads.

- [ ] **Step 1: Write the document**

Create `docs/architecture/questline-framework-adoption.md` with exactly this content:

````markdown
# Adopting the questline framework in `mod/hytale`

**Status:** specified, not executed. Written 2026-08-24 alongside the module
boundary realignment.

## What exists

`core` owns a questline framework: `QuestlineModule` (the SPI a questline
implements), `QuestlineRegistry` (what a server ships), `LegendaryConfig`
(per-questline enable/disable), and `LegendaryWiring` (the aggregator that
walks the registry and calls each enabled questline's register hooks).

`quests/stormseeker` implements the SPI as `StormseekerQuestline`, delegating
to `StormseekerWiring`.

## What does not exist

**`mod/hytale` does not use any of it.** `LegendaryHytalePlugin.start()` wires
Stormseeker imperatively: it constructs `HytaleStormseekerHost`, registers a
`StormseekerTickSystem` with the engine, subscribes two player events, and
registers three commands. The framework is exercised only by two tests in
`quests/stormseeker/src/test/.../stormseeker/harness/`.

So "add questline #2 by registering it" is **not** true today. Adding one
means hand-editing the plugin the way Stormseeker is hand-wired.

## Why it was not migrated during the realignment

Reading `StormseekerWiring` showed the framework's Stormseeker implementation
is scaffold in three places:

1. `registerSystems(LegendarySystemRegistrar)` is an explicit no-op —
   `// Intentionally no-op in Phase C scaffold.` The tick system that actually
   drives Phases 1, 1.5 and 2 (via `StormseekerWiring.tick(host)`) has no path
   through the framework.
2. `registerListeners(EventBus)` is dormant. Nothing calls it. Calling it would
   construct `StormseekerAttunementService(bus, null)` and
   `StormseekerTrekSystem(bus, null)` — `null` where the comments say a
   host-provided world belongs — and subscribe `StormseekerLifecycleBridge` to
   live `EncounterStartedEvent`s for the first time.
3. The `AttunementCompleteEvent` handler body is a comment. The Phase 1.5 →
   Phase 2 handshake is unimplemented.

Migrating would have wired a coherent framework to a half-built implementation
and activated dormant paths in the only module with no test coverage.

## Preconditions

All three must hold before executing the migration below. Each is a real code
change, not a review:

- **P1.** `StormseekerWiring.registerSystems` registers the tick system, so the
  engine's ECS registration flows through `LegendarySystemRegistrar` instead of
  a direct `getEntityStoreRegistry().registerSystem(...)` call in the plugin.
- **P2.** The two `null` world arguments in `registerListeners` are resolved —
  either a real host-provided world reaches them, or the services are
  restructured so the argument is not needed.
- **P3.** The `AttunementCompleteEvent` handler has a body, or is removed if the
  handshake belongs elsewhere.

P1–P3 are Project D (roadmap re-scope) work, not refactoring.

## The migration, once preconditions hold

In `LegendaryHytalePlugin.start()`, before the existing Stormseeker
construction:

```java
CoreRuntime runtime = new DefaultCoreRuntime();
QuestlineRegistry questlines = new QuestlineRegistry().register(new StormseekerQuestline());
LegendaryConfig config = LegendaryConfig.enablingAll(questlines);

LegendaryWiring.registerAllGates(questlines, runtime.services().require(GateService.class), config);
LegendaryWiring.registerAllListeners(questlines, runtime.events(), config);
LegendaryWiring.registerAllSystems(questlines, system -> getEntityStoreRegistry().registerSystem(system), config);
```

`LegendarySystemRegistrar.register` takes `Object` precisely so the host adapts
it to the engine's ECS type — that lambda is the adapter, and it is why no
Hytale implementation of `core.api.platform.CoreRuntime` is required for this
step. Confirm the `registerSystem` overload accepts what
`StormseekerWiring.registerSystems` passes; cast at the lambda if not.

Then delete from the plugin whatever P1 moved behind `registerSystems`, keeping
the host, the progress store, the player-event subscriptions and the three
commands — those are Hytale adapters, not questline registration, and they stay.

## How to tell it worked

Adding questline #2 should require: a new `quests/<name>/` module, one
`include` in `settings.gradle.kts`, its Hytale adapters under
`mod/hytale/.../hytale/<name>/`, and **one `.register(new <Name>Questline())`
line**. If it requires anything else in `LegendaryHytalePlugin.start()`, the
migration is incomplete.

## Related

- `:core:checkNoQuestlineImports` keeps `core` from naming a questline. It does
  not and cannot check that the plugin uses the framework — that is what this
  document is for.
- `mod/hytale` has no tests. Executing this migration changes plugin boot
  behavior with no automated check, so it should be verified against a running
  server.
````

- [ ] **Step 2: Verify the claims in the document still hold**

The document asserts three things about `StormseekerWiring`. Confirm each before committing — a specification built on a stale reading is worse than none:

```bash
cd "$(git rev-parse --show-toplevel)"
W=quests/stormseeker/src/main/java/io/github/legendaryforge/legendary/mod/stormseeker/StormseekerWiring.java
grep -n "Intentionally no-op" "$W"
grep -n "bus, null" "$W"
grep -n "Handshake logic" "$W"
```

Expected: **1, 2, 1** respectively — `bus, null` legitimately matches twice, once for `StormseekerAttunementService` and once for `StormseekerTrekSystem`, which is why the document says *two* `null` world arguments. Verified 2026-08-24. If any count is **zero**, the scaffold has been finished since then — update the preconditions section to match reality rather than committing the stale claim.

- [ ] **Step 3: Confirm no code changed**

```bash
git status --porcelain
```

Expected: exactly one line, `?? docs/architecture/questline-framework-adoption.md`. Any other entry means this documentation-only task touched code — **stop and report**.

- [ ] **Step 4: Commit**

```bash
git add docs/architecture/questline-framework-adoption.md
git commit -m "docs: specify the questline framework adoption for mod/hytale

The realignment moved the questline framework into core but deliberately left
LegendaryHytalePlugin on its hard-coded wiring, because StormseekerWiring's
implementation is scaffold in three places. This records the preconditions and
the exact migration so it is executable work rather than a remembered
intention."
```

---

### Task 9: Reunite the item tests with the code they test

Task 2 moved the three `LegendaryItem*` classes into `core` but left their tests behind in `quests/stormseeker`. So `core`'s item types are exercised only from another module, `:core:test` alone does not cover them, and `core/src/test` contains no item tests at all. Surfaced as a Minor by the Task 2 review; acted on rather than deferred because a framework whose tests live in one of its consumers is the same category of wrong this plan exists to fix.

Verified 2026-08-24: exactly two test files qualify. Three other `quests` test files import `core.api.*` types (`StormseekerObjectiveSnapshotServiceTest`, `AnchoredTrialRuntimeOrchestratorTest`, `FlowingTrialRuntimeOrchestratorTest`) but are Stormseeker tests that *use* core, not tests *of* core — they stay where they are.

**Files:**
- Move: `quests/stormseeker/src/test/java/io/github/legendaryforge/legendary/mod/item/LegendaryItemIdentityTest.java` → `core/src/test/java/io/github/legendaryforge/legendary/core/api/item/LegendaryItemIdentityTest.java`
- Move: `quests/stormseeker/src/test/java/io/github/legendaryforge/legendary/mod/item/LegendaryItemPolicyTest.java` → `core/src/test/java/io/github/legendaryforge/legendary/core/api/item/LegendaryItemPolicyTest.java`

**Interfaces:**
- Consumes: `core.api.item.{LegendaryItemIdentity, LegendaryItemPolicy, LegendaryItemRole}` as placed by Task 2.
- Produces: no production code. Three test cases move from the `quests:stormseeker` module to the `core` module.

- [ ] **Step 1: Confirm the scope is still exactly two files**

```bash
cd "$(git rev-parse --show-toplevel)"
bash -c "grep -rl 'core\.api\.item\.' --include='*.java' quests/stormseeker/src/test | sort"
```

Expected: exactly `LegendaryItemIdentityTest.java` and `LegendaryItemPolicyTest.java`, both under `.../legendary/mod/item/`. If a third file appears, **stop and report** — decide per file whether it tests core or merely uses it.

- [ ] **Step 2: Move both files and rewrite their package lines**

```bash
cd "$(git rev-parse --show-toplevel)"
SRC=quests/stormseeker/src/test/java/io/github/legendaryforge/legendary/mod/item
DST=core/src/test/java/io/github/legendaryforge/legendary/core/api/item
mkdir -p "$DST"
git mv "$SRC/LegendaryItemIdentityTest.java" "$DST/LegendaryItemIdentityTest.java"
git mv "$SRC/LegendaryItemPolicyTest.java"   "$DST/LegendaryItemPolicyTest.java"
rmdir "$SRC" 2>/dev/null || echo "NOTE: $SRC not empty, inspect"
sed -i 's|^package io\.github\.legendaryforge\.legendary\.mod\.item;|package io.github.legendaryforge.legendary.core.api.item;|' "$DST"/*.java
grep -h '^package' "$DST"/*.java
```

Expected: two `package io.github.legendaryforge.legendary.core.api.item;` lines, and `$SRC` removed.

- [ ] **Step 3: Drop the now-redundant same-package imports**

Both files import `LegendaryItemIdentity`, `LegendaryItemPolicy` and `LegendaryItemRole` from the package they now live in.

```bash
cd "$(git rev-parse --show-toplevel)"
DST=core/src/test/java/io/github/legendaryforge/legendary/core/api/item
sed -i '/^import io\.github\.legendaryforge\.legendary\.core\.api\.item\./d' "$DST"/*.java
grep -c '^import' "$DST"/*.java
```

Both files keep their `import static org.junit.jupiter.api.Assertions.*`, `java.util.UUID`, and `org.junit.jupiter.api.Test` imports. Only the three `core.api.item.*` lines go.

- [ ] **Step 4: Run the gate and the census**

```bash
./gradlew spotlessApply
./gradlew build --rerun-tasks
EXPECTED_TESTS=198 python3 scripts/test-census.py
```

Expected: `BUILD SUCCESSFUL`, then `CENSUS_VERDICT: GREEN | 198 tests | 0 failures | …`.

**The total is unchanged, but the distribution shifts** — `core` gains 3 and `quests/stormseeker` loses 3. Read the per-module lines, not just the total: if `core` did not gain exactly 3, the tests were not discovered at their new location.

These files are newly subject to `core`'s Spotless and Error Prone config, which `quests` also applies — so no new violations are expected, but `spotlessApply` before the build is not optional.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(core): move the item tests into the module that owns the code

Task 2 moved LegendaryItemIdentity/Policy/Role into core but left their tests
in quests/stormseeker, so core's item types were covered only from a consumer
module and :core:test alone did not exercise them.

Total unchanged at 198; core +3, quests/stormseeker -3."
```

## After this plan

The structure becomes:

```
core/          primitives + shared encounter framework (arena, access, rewards,
               penalties, start) + questline framework (SPI, registry, objectives,
               runtime seams, items)
quests/<name>/ one per questline, engine-agnostic, depends on core and nothing else
mod/hytale/    the Hytale mod: plugin entrypoint, commands, per-questline adapters
               (still wires Stormseeker imperatively -- see Task 8)
harness/       consumer-side tests
```

`core` owns the questline framework and names no questline; `:core:checkNoQuestlineImports` enforces that.

**What this plan does not yet buy you.** Adding questline #2 is *not* yet one registration line. `LegendaryHytalePlugin` does not use the framework — it wires Stormseeker imperatively — so a second questline still means hand-editing the plugin. Task 8 specifies the migration that closes this, and names the three scaffold preconditions in `StormseekerWiring` that must be finished first. Until then the framework is correctly *placed* but not yet *adopted*, and the distinction is deliberate rather than overlooked.

**Known remaining gaps**, unchanged by this plan and tracked in `Hytale_Session_Status.md`:

- `mod/hytale` has zero tests across 8 files — the module most exposed to game churn.
- No shared build logic; four near-duplicate module scripts, with Spotless and Error Prone on `core`/`quests` but not `mod`/`harness`.
- No CI; every guard in this plan fires only on a developer's machine.
- `harness/src/main` holds 20 files of demo content (`toystorm`, `ToyLightning`) rather than test scaffolding, and carries two package roots (`legendary.content` and `legendarycontent`) left over from the merge.
- `StormseekerProgressStore` exists twice — a port interface in `quests/stormseeker` and an unrelated Properties-file class in `mod/hytale` that does not implement it. Same shape as the `PerceptionToggleHandler` duplicate deleted during consolidation.

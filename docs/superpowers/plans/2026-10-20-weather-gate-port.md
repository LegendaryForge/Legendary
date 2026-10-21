# Weather Override Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `WeatherGate` (read) and `PlayerWeatherController` (claim/release) in `core`, implemented over Hytale's `WeatherTracker` by one adapter in `mod/hytale`, and migrate the existing weather readers onto them.

**Architecture:** All decisions live in `core` where tests can reach them; `mod/hytale` is a translator. `core` speaks weather **ids**, never engine indices — the ordinal never leaves the adapter. Ownership of a per-player override is arbitrated by `core` against an explicit value-keyed `OverrideOwner`, because the engine compares owners by reference identity on an arbitrary claimant `Ref` and therefore cannot distinguish our own subsystems from each other.

**Tech Stack:** Java 25, JUnit 5 (Jupiter), Gradle multi-project, Spotless via `check`.

**Spec:** `docs/superpowers/specs/2026-10-20-weather-gate-port-design.md` (on `main` at `8281036`)

## Global Constraints

- **Java 25.** Toolchain pinned via committed `gradle/gradle-daemon-jvm.properties`.
- **`core` must not import `com.hypixel.`** — `:core:checkNoPlatformImports` fails the build. `core` therefore cannot name the engine's `PlayerRef` at all.
- **`core` must not depend on any `:quests:*`** — `:core:checkNoQuestlineDependency`.
- **Package paths mirror module paths.** `:core` → `io.github.legendaryforge.legendary.core.*`; `:mod:hytale` → `…legendary.mod.hytale.*`; `:harness` → `…legendary.harness.*`.
- **`api/` holds interfaces and records** (host-implemented SPIs included — precedent: `api/questline/runtime/ActivePlayerProvider`). **`internal/` holds `Default*` / `Simple*` implementations** (precedent: `internal/gate/DefaultGateService`, `internal/event/SimpleEventBus`).
- **`PlayerRef` means `core.api.questline.runtime.PlayerRef`** — `record PlayerRef(String id)` — in every signature in this plan. The engine's identically-named `com.hypixel.hytale.server.core.universe.PlayerRef` appears **only** inside `mod/hytale`.
- **`mod/hytale` has no tests and cannot have them** — CI runners lack the proprietary jar, so the module is `EXEMPT` on every CI run. Nothing in Tasks 6–7 is machine-verifiable on CI; they are verified by the build gates on this machine plus a live probe re-run.
- **Verdict lines are read by name, never by position:** `grep CENSUS_VERDICT`, `grep COVERAGE_VERDICT`. On this machine read **`0 exempt`**, not the census — `CENSUS_VERDICT` is structurally blind to a compile failure in `mod/hytale`, which has no tests.
- **`main` is protected.** Every change goes on a branch cut fresh from `main`, pushed, PR'd, then merged with **`--merge`** (never squash or rebase). `git checkout main` **before** `gh pr merge`.
- **Existing behaviour to preserve:** `WeatherKinds.isStorm` must keep matching on `contains("storm")` and `isThunderStorm` on `contains("thunder_storm") || contains("thunderstorm")`, lowercased. These are the current `HytaleWeatherReader` heuristics; this port moves them somewhere testable without changing what they match.

---

> **REVISED 2026-10-20, after `javap` and `/weatherportprobe`.** An earlier draft of Tasks 3, 4 and
> 6 was written from guessed engine signatures and three of them were wrong. What changed:
> `effectiveWeather` is **gone from the SPI** — `WeatherTracker` has no override getter, so the
> adapter cannot answer it and `WeatherGate` computes it from a shared `OverrideRegistry`; the SPI
> gains `onTick`, because a `Store<EntityStore>` is reachable only from a tick system and the
> adapter must cache it (measured stable across invocations). Tasks 1, 2 and 5 are unaffected.

## File Structure

### `core` — new

| File | Responsibility |
|---|---|
| `core/src/main/java/…/core/api/weather/WeatherId.java` | validated weather identity; wraps the engine's stable string id |
| `core/src/main/java/…/core/api/weather/WeatherResolution.java` | `OK` / `UNKNOWN` / `SENTINEL` — how the adapter reports an id without exposing the ordinal |
| `core/src/main/java/…/core/api/weather/WeatherKinds.java` | storm predicates, tested |
| `core/src/main/java/…/core/api/weather/OverrideOwner.java` | value-keyed owner identity |
| `core/src/main/java/…/core/api/weather/WeatherClaimStatus.java` | six claim outcomes |
| `core/src/main/java/…/core/api/weather/WeatherClaimResult.java` | status + optional holder |
| `core/src/main/java/…/core/api/weather/WeatherReleaseStatus.java` | four release outcomes |
| `core/src/main/java/…/core/api/weather/WeatherBackend.java` | SPI; the only thing `mod/hytale` implements |
| `core/src/main/java/…/core/api/weather/WeatherGate.java` | read port |
| `core/src/main/java/…/core/api/weather/PlayerWeatherController.java` | write port |
| `core/src/main/java/…/core/internal/weather/OverrideRegistry.java` | owner + claimed weather per subject; shared by gate and controller |
| `core/src/main/java/…/core/internal/weather/DefaultWeatherGate.java` | `effective` = registry claim if held, else backend unoverridden |
| `core/src/main/java/…/core/internal/weather/DefaultPlayerWeatherController.java` | claim registry + every arbitration rule |

### `core` — new tests

| File | Responsibility |
|---|---|
| `core/src/test/java/…/core/api/weather/WeatherIdTest.java` | validation |
| `core/src/test/java/…/core/api/weather/WeatherKindsTest.java` | predicates, both directions |
| `core/src/test/java/…/core/api/weather/WeatherClaimResultTest.java` | result construction |
| `core/src/test/java/…/core/internal/weather/FakeWeatherBackend.java` | programmable test double |
| `core/src/test/java/…/core/internal/weather/DefaultWeatherGateTest.java` | read behaviour |
| `core/src/test/java/…/core/internal/weather/DefaultPlayerWeatherControllerTest.java` | all six claim statuses, release, forget |

### `harness` — new test

| File | Responsibility |
|---|---|
| `harness/src/test/java/…/harness/weather/RecordingWeatherBackend.java` | harness-owned double (precedent: `RecordingStormseekerHostRuntime`) |
| `harness/src/test/java/…/harness/weather/WeatherPortContractTest.java` | consumer-side invariants |

> **Why the harness gets its own double** rather than reusing `core`'s: test sources are not published between modules here, and `harness` already follows this pattern with `RecordingStormseekerHostRuntime`. This resolves spec §9 open question 2 by precedent — no `java-test-fixtures` plugin is introduced.

### `mod/hytale` — new and changed

| File | Responsibility |
|---|---|
| Create `mod/hytale/src/main/java/…/mod/hytale/weather/HytaleWeatherBackend.java` | the only platform code; absorbs `HytaleWeatherReader` |
| Modify `mod/hytale/src/main/java/…/mod/hytale/LegendaryHytalePlugin.java` | construct the port; drive the id map and `forget` from connect/disconnect |
| Modify `mod/hytale/src/main/java/…/mod/hytale/command/StormseekerStatusCommand.java` | read through the port |
| **Delete** `mod/hytale/src/main/java/…/mod/hytale/stormseeker/HytaleWeatherReader.java` | superseded |

---

### Task 1: Weather value types

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherId.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherResolution.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherKinds.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/weather/WeatherIdTest.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/weather/WeatherKindsTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `WeatherId(String value)` with accessor `value()`; `enum WeatherResolution { OK, UNKNOWN, SENTINEL }`; `WeatherKinds.isStorm(WeatherId)` and `WeatherKinds.isThunderStorm(WeatherId)`, both `static boolean`, both null-tolerant returning `false`.

- [ ] **Step 1: Write the failing tests**

`WeatherIdTest.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WeatherIdTest {

    @Test
    void holds_its_value() {
        assertEquals("Zone1_Rain", new WeatherId("Zone1_Rain").value());
    }

    @Test
    void rejects_null() {
        assertThrows(NullPointerException.class, () -> new WeatherId(null));
    }

    @Test
    void rejects_blank() {
        assertThrows(IllegalArgumentException.class, () -> new WeatherId("   "));
    }

    @Test
    void equality_is_by_value() {
        assertEquals(new WeatherId("Zone1_Rain"), new WeatherId("Zone1_Rain"));
        assertNotEquals(new WeatherId("Zone1_Rain"), new WeatherId("Zone1_Cloudy_Medium"));
    }
}
```

`WeatherKindsTest.java` — note the negative cases, which the heuristics being replaced never had:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WeatherKindsTest {

    @Test
    void isStorm_matches_storm_substring_case_insensitively() {
        assertTrue(WeatherKinds.isStorm(new WeatherId("Zone4_Thunder_Storm")));
        assertTrue(WeatherKinds.isStorm(new WeatherId("zone1_storm")));
        assertTrue(WeatherKinds.isStorm(new WeatherId("STORM")));
    }

    @Test
    void isStorm_rejects_non_storms() {
        assertFalse(WeatherKinds.isStorm(new WeatherId("Zone1_Rain")));
        assertFalse(WeatherKinds.isStorm(new WeatherId("Zone1_Cloudy_Medium")));
    }

    @Test
    void isThunderStorm_matches_both_spellings() {
        assertTrue(WeatherKinds.isThunderStorm(new WeatherId("Zone4_Thunder_Storm_Heavy")));
        assertTrue(WeatherKinds.isThunderStorm(new WeatherId("ThunderStorm")));
    }

    @Test
    void isThunderStorm_is_narrower_than_isStorm() {
        WeatherId plainStorm = new WeatherId("Zone1_Storm");
        assertTrue(WeatherKinds.isStorm(plainStorm));
        assertFalse(WeatherKinds.isThunderStorm(plainStorm));
    }

    @Test
    void null_is_not_any_kind() {
        assertFalse(WeatherKinds.isStorm(null));
        assertFalse(WeatherKinds.isThunderStorm(null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests 'io.github.legendaryforge.legendary.core.api.weather.*'`
Expected: FAIL — compilation error, `WeatherId` / `WeatherKinds` do not exist.

- [ ] **Step 3: Write the implementations**

`WeatherId.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import java.util.Objects;

/**
 * A weather identity, as the engine's stable string id (e.g. {@code Zone1_Rain}).
 *
 * <p>Deliberately NOT an index. A weather index is an asset-map ordinal; holding one in
 * {@code core} would mean carrying an ordinal across game updates with no way to notice it
 * shifted. The ordinal stays inside the adapter.
 */
public record WeatherId(String value) {

    public WeatherId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("WeatherId value must not be blank");
        }
    }
}
```

`WeatherResolution.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

/**
 * How a {@link WeatherId} resolves against the running engine's asset map.
 *
 * <p>{@code SENTINEL} exists because index 0 is a sentinel meaning "no override", and
 * {@code tryClaimOverride(ref, 0)} returns <b>true and does nothing</b> — measured 2026-10-20. The
 * port must reject it before the engine sees the call, or a caller receives a success it did not
 * get. Reporting it as a status rather than a number is what keeps the ordinal out of {@code core}.
 */
public enum WeatherResolution {
    OK,
    UNKNOWN,
    SENTINEL
}
```

`WeatherKinds.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import java.util.Locale;

/**
 * Storm classification over a {@link WeatherId}.
 *
 * <p>These predicates were string-{@code contains} heuristics inside {@code mod/hytale}, where
 * nothing can test them — that module compiles nothing on CI. They are moved here unchanged in
 * behaviour and tested in both directions.
 */
public final class WeatherKinds {

    private WeatherKinds() {}

    public static boolean isStorm(WeatherId weather) {
        return lower(weather).contains("storm");
    }

    public static boolean isThunderStorm(WeatherId weather) {
        String value = lower(weather);
        return value.contains("thunder_storm") || value.contains("thunderstorm");
    }

    private static String lower(WeatherId weather) {
        return weather == null ? "" : weather.value().toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests 'io.github.legendaryforge.legendary.core.api.weather.*'`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git checkout main && git pull --ff-only
git checkout -b feat/weather-port
git add core/src/main/java/io/github/legendaryforge/legendary/core/api/weather core/src/test/java/io/github/legendaryforge/legendary/core/api/weather
git commit -m "feat(core): weather value types for the override port

WeatherId wraps the engine's stable string id rather than an index, so no
asset-map ordinal is carried in core across game updates. WeatherResolution
reports the index-0 sentinel as a status instead of a number, which is what lets
core reject it without seeing the ordinal.

WeatherKinds moves the storm predicates out of mod/hytale, where nothing could
test them -- that module compiles nothing on CI. Behaviour is unchanged; the
negative cases are new.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Claim and release result types

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/OverrideOwner.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherClaimStatus.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherClaimResult.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherReleaseStatus.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/weather/WeatherClaimResultTest.java`
- Modify: `docs/superpowers/specs/2026-10-20-weather-gate-port-design.md` (§4 component table, §5)

**Interfaces:**
- Consumes: `ResourceId` from `core.api.id`.
- Produces: `OverrideOwner(ResourceId id)`; `enum WeatherClaimStatus { GRANTED, UPDATED, REFUSED_HELD, UNKNOWN_WEATHER, UNSUPPORTED_WEATHER, SUBJECT_UNAVAILABLE }` with `isSuccess()`; `WeatherClaimResult(WeatherClaimStatus status, Optional<OverrideOwner> holder)` with factories `of(WeatherClaimStatus)` and `refusedHeldBy(OverrideOwner)` and `isSuccess()`; `enum WeatherReleaseStatus { RELEASED, NOT_HELD, REFUSED_NOT_OWNER, SUBJECT_UNAVAILABLE }`.

> **This task refines the spec.** The spec named only `WeatherClaimResult`/`WeatherClaimStatus`. Release has its own four outcomes and reusing claim statuses for it would read wrongly (`GRANTED` for a release). Step 6 amends the spec in the same commit so the two cannot drift.

- [ ] **Step 1: Write the failing test**

```java
package io.github.legendaryforge.legendary.core.api.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeatherClaimResultTest {

    private static final OverrideOwner OWNER = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));

    @Test
    void of_carries_no_holder() {
        WeatherClaimResult result = WeatherClaimResult.of(WeatherClaimStatus.GRANTED);
        assertEquals(WeatherClaimStatus.GRANTED, result.status());
        assertEquals(Optional.empty(), result.holder());
    }

    @Test
    void refusedHeldBy_names_the_holder() {
        WeatherClaimResult result = WeatherClaimResult.refusedHeldBy(OWNER);
        assertEquals(WeatherClaimStatus.REFUSED_HELD, result.status());
        assertEquals(Optional.of(OWNER), result.holder());
    }

    @Test
    void granted_and_updated_are_the_only_successes() {
        assertTrue(WeatherClaimResult.of(WeatherClaimStatus.GRANTED).isSuccess());
        assertTrue(WeatherClaimResult.of(WeatherClaimStatus.UPDATED).isSuccess());
        assertFalse(WeatherClaimResult.of(WeatherClaimStatus.UNKNOWN_WEATHER).isSuccess());
        assertFalse(WeatherClaimResult.of(WeatherClaimStatus.UNSUPPORTED_WEATHER).isSuccess());
        assertFalse(WeatherClaimResult.of(WeatherClaimStatus.SUBJECT_UNAVAILABLE).isSuccess());
        assertFalse(WeatherClaimResult.refusedHeldBy(OWNER).isSuccess());
    }

    @Test
    void owner_equality_is_by_value_not_identity() {
        OverrideOwner same = new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
        assertEquals(OWNER, same);
        assertNotSame(OWNER, same);
    }

    @Test
    void rejects_nulls() {
        assertThrows(NullPointerException.class, () -> new OverrideOwner(null));
        assertThrows(NullPointerException.class, () -> new WeatherClaimResult(null, Optional.empty()));
        assertThrows(
                NullPointerException.class, () -> new WeatherClaimResult(WeatherClaimStatus.GRANTED, null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests '*WeatherClaimResultTest'`
Expected: FAIL — compilation error, `OverrideOwner` does not exist.

- [ ] **Step 3: Write the implementations**

`OverrideOwner.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import java.util.Objects;

/**
 * Identity of whatever holds a per-player weather override.
 *
 * <p>Keyed by <b>value</b>, and that is the whole point. The engine compares override owners by
 * reference identity ({@code if_acmpeq}) on a claimant {@code Ref} that is arbitrary — distinct
 * from the subject — so the natural implementation passes the player's own {@code Ref} as claimant
 * everywhere. Measured 2026-10-20: a same-claimant re-claim is then a <b>silent update, not a
 * refusal</b>, so two of our own subsystems gating one player would overwrite each other with no
 * error. Our owner identity has to be finer than the engine's, which is what this type provides.
 */
public record OverrideOwner(ResourceId id) {

    public OverrideOwner {
        Objects.requireNonNull(id, "id");
    }
}
```

`WeatherClaimStatus.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

/** Outcome of a {@link PlayerWeatherController#claim} attempt. */
public enum WeatherClaimStatus {

    /** No owner held the subject; the override is now ours. */
    GRANTED,

    /** We already held it; the weather was changed. Mirrors the engine's measured behaviour. */
    UPDATED,

    /** Another owner holds it. The held weather is untouched. */
    REFUSED_HELD,

    /** The backend does not recognise the weather id. */
    UNKNOWN_WEATHER,

    /** The id resolves to the index-0 sentinel, which cannot be claimed. */
    UNSUPPORTED_WEATHER,

    /** The subject has no weather tracker — absent, invalid, or not connected. */
    SUBJECT_UNAVAILABLE;

    public boolean isSuccess() {
        return this == GRANTED || this == UPDATED;
    }
}
```

`WeatherClaimResult.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of a claim attempt. A record rather than a boolean, following
 * {@code ActivationAttemptResult}/{@code ActivationAttemptStatus} and {@code GateDecision}.
 *
 * @param holder present only for {@link WeatherClaimStatus#REFUSED_HELD}, naming who holds it
 */
public record WeatherClaimResult(WeatherClaimStatus status, Optional<OverrideOwner> holder) {

    public WeatherClaimResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(holder, "holder");
    }

    public static WeatherClaimResult of(WeatherClaimStatus status) {
        return new WeatherClaimResult(status, Optional.empty());
    }

    public static WeatherClaimResult refusedHeldBy(OverrideOwner holder) {
        return new WeatherClaimResult(WeatherClaimStatus.REFUSED_HELD, Optional.of(holder));
    }

    public boolean isSuccess() {
        return status.isSuccess();
    }
}
```

`WeatherReleaseStatus.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

/** Outcome of a {@link PlayerWeatherController#release} attempt. */
public enum WeatherReleaseStatus {

    /** We held it and the override is cleared. */
    RELEASED,

    /** Nobody held an override for this subject. */
    NOT_HELD,

    /** Someone else holds it; nothing was cleared. */
    REFUSED_NOT_OWNER,

    /**
     * We held it but the subject has no tracker. The registry entry is dropped anyway, so a
     * departed player cannot strand an owner.
     */
    SUBJECT_UNAVAILABLE
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests '*WeatherClaimResultTest'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Amend the spec so it matches**

In `docs/superpowers/specs/2026-10-20-weather-gate-port-design.md`, §4's `core/api/weather/` table, add one row after the `WeatherClaimResult` row:

```markdown
| `WeatherReleaseStatus` | `enum { RELEASED, NOT_HELD, REFUSED_NOT_OWNER, SUBJECT_UNAVAILABLE }` | release has its own outcomes; reusing claim statuses would read wrongly (`GRANTED` for a release) |
```

And in §5, replace the sentence `` `release(subject, owner)` refuses anyone but the recorded owner. `` with:

```markdown
`release(subject, owner)` returns a `WeatherReleaseStatus` and refuses anyone but the recorded
owner. A release by the true owner whose subject has since lost its tracker still drops the registry
entry, so a departed player cannot strand an owner.
```

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git add core/src/main/java/io/github/legendaryforge/legendary/core/api/weather core/src/test/java/io/github/legendaryforge/legendary/core/api/weather docs/superpowers/specs/2026-10-20-weather-gate-port-design.md
git commit -m "feat(core): claim and release result types for the weather port

OverrideOwner is keyed by value because the engine's own ownership check cannot
distinguish our subsystems: it compares by reference identity on an arbitrary
claimant Ref, and a same-claimant re-claim was measured to be a silent update
rather than a refusal.

Adds WeatherReleaseStatus, which the spec did not name. Release has four outcomes
of its own and reusing claim statuses for it would read wrongly. The spec is
amended in this commit rather than later, so the two cannot drift.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `WeatherBackend` SPI, `WeatherGate`, and the fake

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherBackend.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/WeatherGate.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/internal/weather/DefaultWeatherGate.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/weather/FakeWeatherBackend.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/weather/DefaultWeatherGateTest.java`

**Interfaces:**
- Consumes: `WeatherId`, `WeatherResolution` (Task 1); `PlayerRef` from `core.api.questline.runtime`.
- Produces: `WeatherBackend` with `hasTracker(PlayerRef)`, `unoverriddenWeather(PlayerRef)`, `resolve(WeatherId)`, `applyOverride(PlayerRef, WeatherId)`, `clearOverride(PlayerRef)`, `onTick(Object)`. `WeatherGate` with `effective(PlayerRef)` and `unoverridden(PlayerRef)`, both `Optional<WeatherId>`. `OverrideRegistry` with `claimedWeather(PlayerRef) -> Optional<WeatherId>`, `owner(PlayerRef) -> Optional<OverrideOwner>`, `put(PlayerRef, OverrideOwner, WeatherId)`, `remove(PlayerRef)`. `DefaultWeatherGate(WeatherBackend, OverrideRegistry)`. `FakeWeatherBackend` with `withTracker(String id)`, `withoutTracker(String id)`, `setEffective(String id, WeatherId)`, `setUnoverridden(String id, WeatherId)`, `register(WeatherId, WeatherResolution)`, `overrideOf(String id)`, `applyCalls()`, `clearCalls()`, `failNextApply()`.

- [ ] **Step 1: Write the failing test**

`DefaultWeatherGateTest.java`:

```java
package io.github.legendaryforge.legendary.core.internal.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultWeatherGateTest {

    private static final PlayerRef ALICE = new PlayerRef("alice");
    private static final WeatherId RAIN = new WeatherId("Zone1_Rain");
    private static final WeatherId CLOUDY = new WeatherId("Zone1_Cloudy_Medium");

    private static final OverrideOwner OWNER =
            new OverrideOwner(ResourceId.parse("legendary:stormseeker"));

    @Test
    void with_no_claim_effective_is_what_the_world_gives_them() {
        FakeWeatherBackend backend = new FakeWeatherBackend();
        backend.withTracker("alice");
        backend.setUnoverridden("alice", CLOUDY);

        WeatherGate gate = new DefaultWeatherGate(backend, new OverrideRegistry());

        assertEquals(Optional.of(CLOUDY), gate.effective(ALICE));
    }

    @Test
    void a_claim_overrides_effective_but_never_unoverridden() {
        FakeWeatherBackend backend = new FakeWeatherBackend();
        backend.withTracker("alice");
        backend.setUnoverridden("alice", CLOUDY);
        OverrideRegistry registry = new OverrideRegistry();
        registry.put(ALICE, OWNER, RAIN);

        WeatherGate gate = new DefaultWeatherGate(backend, registry);

        assertEquals(Optional.of(RAIN), gate.effective(ALICE), "the claim wins");
        assertEquals(
                Optional.of(CLOUDY), gate.unoverridden(ALICE), "the world's own weather is untouched");
    }

    @Test
    void a_subject_with_no_tracker_reports_empty_even_with_a_claim() {
        FakeWeatherBackend backend = new FakeWeatherBackend();
        backend.withoutTracker("alice");
        OverrideRegistry registry = new OverrideRegistry();
        registry.put(ALICE, OWNER, RAIN);

        WeatherGate gate = new DefaultWeatherGate(backend, registry);

        assertEquals(Optional.empty(), gate.effective(ALICE));
        assertEquals(Optional.empty(), gate.unoverridden(ALICE));
    }

    @Test
    void rejects_nulls() {
        WeatherGate gate = new DefaultWeatherGate(new FakeWeatherBackend(), new OverrideRegistry());
        assertThrows(NullPointerException.class, () -> gate.effective(null));
        assertThrows(NullPointerException.class, () -> gate.unoverridden(null));
        assertThrows(
                NullPointerException.class, () -> new DefaultWeatherGate(null, new OverrideRegistry()));
        assertThrows(
                NullPointerException.class, () -> new DefaultWeatherGate(new FakeWeatherBackend(), null));
    }
}
```

`FakeWeatherBackend.java` — programmable, and it counts calls so later tasks can assert the engine was or was not touched:

```java
package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Programmable {@link WeatherBackend} for core tests. No engine, no platform types. */
final class FakeWeatherBackend implements WeatherBackend {

    private final Set<String> tracked = new HashSet<>();
    private final Map<String, WeatherId> effective = new HashMap<>();
    private final Map<String, WeatherId> unoverridden = new HashMap<>();
    private final Map<WeatherId, WeatherResolution> resolutions = new HashMap<>();
    private final Map<String, WeatherId> applied = new HashMap<>();

    private int applyCalls;
    private int clearCalls;
    private boolean failNextApply;

    void withTracker(String id) {
        tracked.add(id);
    }

    void withoutTracker(String id) {
        tracked.remove(id);
    }

    void setEffective(String id, WeatherId weather) {
        effective.put(id, weather);
    }

    void setUnoverridden(String id, WeatherId weather) {
        unoverridden.put(id, weather);
    }

    /** Anything not registered resolves UNKNOWN, so a test must opt in to a usable weather. */
    void register(WeatherId weather, WeatherResolution resolution) {
        resolutions.put(weather, resolution);
    }

    void failNextApply() {
        failNextApply = true;
    }

    WeatherId overrideOf(String id) {
        return applied.get(id);
    }

    int applyCalls() {
        return applyCalls;
    }

    int clearCalls() {
        return clearCalls;
    }

    @Override
    public boolean hasTracker(PlayerRef subject) {
        return tracked.contains(subject.id());
    }

    @Override
    public Optional<WeatherId> unoverriddenWeather(PlayerRef subject) {
        return Optional.ofNullable(unoverridden.get(subject.id()));
    }

    @Override
    public WeatherResolution resolve(WeatherId weather) {
        return resolutions.getOrDefault(weather, WeatherResolution.UNKNOWN);
    }

    @Override
    public boolean applyOverride(PlayerRef subject, WeatherId weather) {
        applyCalls++;
        if (failNextApply) {
            failNextApply = false;
            return false;
        }
        applied.put(subject.id(), weather);
        return true;
    }

    @Override
    public boolean clearOverride(PlayerRef subject) {
        clearCalls++;
        applied.remove(subject.id());
        return true;
    }

    @Override
    public void onTick(Object worldStore) {
        // no-op: this fake needs no store
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests '*DefaultWeatherGateTest'`
Expected: FAIL — compilation error, `WeatherBackend` and `DefaultWeatherGate` do not exist.

- [ ] **Step 3: Write the implementations**

`WeatherBackend.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import java.util.Optional;

/**
 * Host-implemented SPI for one engine's weather system. The only interface {@code mod/hytale}
 * implements for this port.
 *
 * <p><b>No method here mentions an index.</b> {@link #resolve} is what lets {@code core} reject the
 * index-0 sentinel while the ordinal stays inside the adapter.
 *
 * <p><b>Reads must not use the engine's last-sent value.</b> On Hytale,
 * {@code WeatherTracker.getWeatherIndex()} is a send-log, not a resolver — measured 2026-10-20: a
 * read immediately after a claim returns the pre-claim value. Implementations resolve through the
 * engine's effective/unoverridden accessors instead.
 *
 * <p><b>This interface is internal to the port.</b> Hytale's
 * {@code setOverrideWeatherIndex}/{@code clearOverrideWeatherIndex} are public and perform no
 * ownership check at all — measured: an unclaimed caller set an override and a non-owner cleared
 * one. {@link #applyOverride} and {@link #clearOverride} are their unchecked equivalents and are
 * never exposed on {@link PlayerWeatherController}.
 */
public interface WeatherBackend {

    boolean hasTracker(PlayerRef subject);

    Optional<WeatherId> unoverriddenWeather(PlayerRef subject);

    WeatherResolution resolve(WeatherId weather);

    /** @return false if the engine refused or the subject vanished between check and call */
    boolean applyOverride(PlayerRef subject, WeatherId weather);

    boolean clearOverride(PlayerRef subject);

    /**
     * Hand the adapter the per-world store it needs, once per tick.
     *
     * <p>{@code Object} because {@code core} cannot name {@code Store<EntityStore>} --
     * {@code checkNoPlatformImports} fails the build on it. The adapter casts. This is the single
     * place the port pays for the module boundary.
     *
     * <p>Required because {@code resolveUnoverriddenWeatherIndex} needs a store and neither
     * {@code PluginBase} nor {@code JavaPlugin} exposes a route to one. Measured
     * 2026-10-20: the store and its WeatherResource are the same instances across invocations, so
     * caching them is safe within a boot.
     */
    void onTick(Object worldStore);
}
```

`WeatherGate.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import java.util.Optional;

/**
 * Read side of per-player weather.
 *
 * <p>{@link #effective} is what the player is actually experiencing. Precedence is
 * <b>per-player override &gt; world-forced &gt; environment</b>, measured 2026-10-20, and is the
 * engine's — this port does not change it.
 */
public interface WeatherGate {

    /** Weather the subject is experiencing now, or empty if they have no tracker. */
    Optional<WeatherId> effective(PlayerRef subject);

    /** Weather the subject would experience with no override applied. */
    Optional<WeatherId> unoverridden(PlayerRef subject);
}
```

`OverrideRegistry.java` — the shared state the missing engine getter forces on us:

```java
package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who holds a per-player override, and what they claimed.
 *
 * <p>Shared by {@link DefaultWeatherGate} and {@code DefaultPlayerWeatherController}, and that
 * sharing is forced rather than chosen: {@code WeatherTracker} has no override getter, so the only
 * way to answer "what weather is this player actually seeing" is to remember what we set. The
 * controller is the only writer.
 */
public final class OverrideRegistry {

    private record Entry(OverrideOwner owner, WeatherId weather) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public Optional<OverrideOwner> owner(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return Optional.ofNullable(entries.get(subject.id())).map(Entry::owner);
    }

    public Optional<WeatherId> claimedWeather(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return Optional.ofNullable(entries.get(subject.id())).map(Entry::weather);
    }

    public void put(PlayerRef subject, OverrideOwner owner, WeatherId weather) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(weather, "weather");
        entries.put(subject.id(), new Entry(owner, weather));
    }

    public void remove(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        entries.remove(subject.id());
    }
}
```

`DefaultWeatherGate.java`:

```java
package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import java.util.Objects;
import java.util.Optional;

public final class DefaultWeatherGate implements WeatherGate {

    private final WeatherBackend backend;
    private final OverrideRegistry registry;

    public DefaultWeatherGate(WeatherBackend backend, OverrideRegistry registry) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Computed, not read. The engine has no override getter, so the claimed weather comes from our
     * own registry; without a claim the player sees what the world gives them. Sound because the
     * port owns every legitimate claim -- it is blind only to a caller using the engine's unchecked
     * setters directly, which is what the port exists to prevent.
     */
    @Override
    public Optional<WeatherId> effective(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        if (!backend.hasTracker(subject)) {
            return Optional.empty();
        }
        Optional<WeatherId> claimed = registry.claimedWeather(subject);
        return claimed.isPresent() ? claimed : backend.unoverriddenWeather(subject);
    }

    @Override
    public Optional<WeatherId> unoverridden(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return backend.hasTracker(subject) ? backend.unoverriddenWeather(subject) : Optional.empty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests '*DefaultWeatherGateTest'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git add core/src/main/java/io/github/legendaryforge/legendary/core/api/weather core/src/main/java/io/github/legendaryforge/legendary/core/internal/weather core/src/test/java/io/github/legendaryforge/legendary/core/internal/weather
git commit -m "feat(core): WeatherBackend SPI and the WeatherGate read side

No method on the SPI mentions an index -- resolve() reports the index-0 sentinel
as a status, so core can reject it without the ordinal leaving the adapter.

The SPI is internal to the port on purpose: applyOverride/clearOverride are the
engine's unchecked set/clear, which were measured to perform no ownership check at
all, and they are never exposed on the public controller.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `PlayerWeatherController` and the arbitration rules

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/PlayerWeatherController.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/internal/weather/DefaultPlayerWeatherController.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/weather/DefaultPlayerWeatherControllerTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–3, including `FakeWeatherBackend`.
- Produces: `PlayerWeatherController` with `claim(PlayerRef, WeatherId, OverrideOwner) -> WeatherClaimResult`, `release(PlayerRef, OverrideOwner) -> WeatherReleaseStatus`, `holder(PlayerRef) -> Optional<OverrideOwner>`, `forget(PlayerRef) -> void`. `DefaultPlayerWeatherController(WeatherBackend, OverrideRegistry)` — **the registry must be the same instance the gate holds**, or `effective()` cannot see claims.

- [ ] **Step 1: Write the failing test**

```java
package io.github.legendaryforge.legendary.core.internal.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimResult;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherReleaseStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultPlayerWeatherControllerTest {

    private static final PlayerRef ALICE = new PlayerRef("alice");
    private static final WeatherId STORM = new WeatherId("Zone4_Thunder_Storm");
    private static final WeatherId RAIN = new WeatherId("Zone1_Rain");
    private static final WeatherId NOT_A_WEATHER = new WeatherId("Nonexistent_Weather");
    private static final WeatherId SENTINEL_WEATHER = new WeatherId("Index_Zero");

    private static final OverrideOwner STORMSEEKER =
            new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
    private static final OverrideOwner ENCOUNTER =
            new OverrideOwner(ResourceId.parse("legendary:encounter"));

    private FakeWeatherBackend backend;
    private PlayerWeatherController controller;

    @BeforeEach
    void setUp() {
        backend = new FakeWeatherBackend();
        backend.withTracker("alice");
        backend.register(STORM, WeatherResolution.OK);
        backend.register(RAIN, WeatherResolution.OK);
        backend.register(SENTINEL_WEATHER, WeatherResolution.SENTINEL);
        controller = new DefaultPlayerWeatherController(backend, new OverrideRegistry());
    }

    @Test
    void granted_when_nobody_holds_it() {
        WeatherClaimResult result = controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherClaimStatus.GRANTED, result.status());
        assertTrue(result.isSuccess());
        assertEquals(STORM, backend.overrideOf("alice"));
        assertEquals(Optional.of(STORMSEEKER), controller.holder(ALICE));
    }

    @Test
    void updated_when_the_same_owner_reclaims() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        WeatherClaimResult result = controller.claim(ALICE, RAIN, STORMSEEKER);

        assertEquals(WeatherClaimStatus.UPDATED, result.status());
        assertTrue(result.isSuccess());
        assertEquals(RAIN, backend.overrideOf("alice"));
    }

    @Test
    void refused_held_when_a_different_owner_claims_and_the_weather_is_untouched() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        WeatherClaimResult result = controller.claim(ALICE, RAIN, ENCOUNTER);

        assertEquals(WeatherClaimStatus.REFUSED_HELD, result.status());
        assertEquals(Optional.of(STORMSEEKER), result.holder());
        assertEquals(STORM, backend.overrideOf("alice"), "the held weather must not change");
        assertEquals(1, backend.applyCalls(), "a refused claim must not reach the engine");
    }

    @Test
    void unknown_weather_is_rejected_before_the_engine_is_touched() {
        WeatherClaimResult result = controller.claim(ALICE, NOT_A_WEATHER, STORMSEEKER);

        assertEquals(WeatherClaimStatus.UNKNOWN_WEATHER, result.status());
        assertEquals(0, backend.applyCalls());
        assertEquals(Optional.empty(), controller.holder(ALICE));
    }

    @Test
    void the_sentinel_is_rejected_before_the_engine_is_touched() {
        WeatherClaimResult result = controller.claim(ALICE, SENTINEL_WEATHER, STORMSEEKER);

        assertEquals(WeatherClaimStatus.UNSUPPORTED_WEATHER, result.status());
        assertEquals(0, backend.applyCalls(), "claiming the sentinel returns true and no-ops; never send it");
        assertEquals(Optional.empty(), controller.holder(ALICE));
    }

    @Test
    void subject_unavailable_when_there_is_no_tracker() {
        backend.withoutTracker("alice");

        WeatherClaimResult result = controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherClaimStatus.SUBJECT_UNAVAILABLE, result.status());
        assertEquals(0, backend.applyCalls());
    }

    @Test
    void a_failed_engine_apply_does_not_record_an_owner() {
        backend.failNextApply();

        WeatherClaimResult result = controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherClaimStatus.SUBJECT_UNAVAILABLE, result.status());
        assertEquals(Optional.empty(), controller.holder(ALICE), "no owner may be recorded for a failed apply");
    }

    @Test
    void the_owner_can_release() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherReleaseStatus.RELEASED, controller.release(ALICE, STORMSEEKER));
        assertEquals(1, backend.clearCalls());
        assertEquals(Optional.empty(), controller.holder(ALICE));
    }

    @Test
    void a_foreign_release_is_refused_and_clears_nothing() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        assertEquals(WeatherReleaseStatus.REFUSED_NOT_OWNER, controller.release(ALICE, ENCOUNTER));
        assertEquals(0, backend.clearCalls());
        assertEquals(Optional.of(STORMSEEKER), controller.holder(ALICE));
    }

    @Test
    void releasing_an_unheld_subject_reports_not_held() {
        assertEquals(WeatherReleaseStatus.NOT_HELD, controller.release(ALICE, STORMSEEKER));
        assertEquals(0, backend.clearCalls());
    }

    @Test
    void the_owner_releasing_a_vanished_subject_still_drops_the_registry_entry() {
        controller.claim(ALICE, STORM, STORMSEEKER);
        backend.withoutTracker("alice");

        assertEquals(WeatherReleaseStatus.SUBJECT_UNAVAILABLE, controller.release(ALICE, STORMSEEKER));
        assertEquals(Optional.empty(), controller.holder(ALICE), "a departed player must not strand an owner");
    }

    @Test
    void forget_drops_the_owner_so_the_next_claim_is_granted() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        controller.forget(ALICE);

        assertEquals(Optional.empty(), controller.holder(ALICE));
        assertEquals(WeatherClaimStatus.GRANTED, controller.claim(ALICE, RAIN, ENCOUNTER).status());
    }

    @Test
    void forget_does_not_touch_the_engine() {
        controller.claim(ALICE, STORM, STORMSEEKER);

        controller.forget(ALICE);

        assertEquals(0, backend.clearCalls(), "forget is registry-only; the engine self-heals a stale owner");
    }

    @Test
    void rejects_nulls() {
        assertThrows(
                NullPointerException.class, () -> new DefaultPlayerWeatherController(null, new OverrideRegistry()));
        assertThrows(NullPointerException.class, () -> controller.claim(null, STORM, STORMSEEKER));
        assertThrows(NullPointerException.class, () -> controller.claim(ALICE, null, STORMSEEKER));
        assertThrows(NullPointerException.class, () -> controller.claim(ALICE, STORM, null));
        assertThrows(NullPointerException.class, () -> controller.release(null, STORMSEEKER));
        assertThrows(NullPointerException.class, () -> controller.release(ALICE, null));
        assertThrows(NullPointerException.class, () -> controller.forget(null));
        assertThrows(NullPointerException.class, () -> controller.holder(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests '*DefaultPlayerWeatherControllerTest'`
Expected: FAIL — compilation error, `PlayerWeatherController` does not exist.

- [ ] **Step 3: Write the implementations**

`PlayerWeatherController.java`:

```java
package io.github.legendaryforge.legendary.core.api.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import java.util.Optional;

/**
 * Write side of per-player weather: claim an override for a subject, release it, see who holds it.
 *
 * <p>One owner per subject, because the engine supports one override per player.
 *
 * <p><b>Arbitration is ours, not the engine's.</b> The engine compares owners by reference identity
 * on an arbitrary claimant {@code Ref}, so our own subsystems are indistinguishable to it and a
 * same-claimant re-claim is a silent overwrite. Refusal and the foreign-release check therefore
 * happen here, before the engine sees the call.
 *
 * <p>Deliberately absent: any equivalent of the engine's unchecked
 * {@code setOverrideWeatherIndex}/{@code clearOverrideWeatherIndex}. Those exist only behind
 * {@link WeatherBackend}.
 */
public interface PlayerWeatherController {

    WeatherClaimResult claim(PlayerRef subject, WeatherId weather, OverrideOwner owner);

    WeatherReleaseStatus release(PlayerRef subject, OverrideOwner owner);

    Optional<OverrideOwner> holder(PlayerRef subject);

    /**
     * Drop any recorded owner for this subject without touching the engine.
     *
     * <p>Called when a player leaves. The engine self-heals a stranded override once the claimant
     * {@code Ref} goes invalid, but this registry is a plain map and would otherwise leak one owner
     * per departed player — after which the next claim against a recycled id is wrongly refused.
     */
    void forget(PlayerRef subject);
}
```

`DefaultPlayerWeatherController.java`:

```java
package io.github.legendaryforge.legendary.core.internal.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimResult;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherReleaseStatus;
import java.util.Objects;
import java.util.Optional;

public final class DefaultPlayerWeatherController implements PlayerWeatherController {

    private final WeatherBackend backend;
    private final OverrideRegistry registry;

    public DefaultPlayerWeatherController(WeatherBackend backend, OverrideRegistry registry) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public WeatherClaimResult claim(PlayerRef subject, WeatherId weather, OverrideOwner owner) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(weather, "weather");
        Objects.requireNonNull(owner, "owner");

        if (!backend.hasTracker(subject)) {
            return WeatherClaimResult.of(WeatherClaimStatus.SUBJECT_UNAVAILABLE);
        }

        // Rejected before the engine is touched. Claiming the sentinel returns true and does
        // nothing, so sending it would hand the caller a success it did not get.
        switch (backend.resolve(weather)) {
            case UNKNOWN -> {
                return WeatherClaimResult.of(WeatherClaimStatus.UNKNOWN_WEATHER);
            }
            case SENTINEL -> {
                return WeatherClaimResult.of(WeatherClaimStatus.UNSUPPORTED_WEATHER);
            }
            case OK -> {
                // proceed
            }
        }

        OverrideOwner current = registry.owner(subject).orElse(null);
        if (current != null && !current.equals(owner)) {
            return WeatherClaimResult.refusedHeldBy(current);
        }

        if (!backend.applyOverride(subject, weather)) {
            return WeatherClaimResult.of(WeatherClaimStatus.SUBJECT_UNAVAILABLE);
        }

        registry.put(subject, owner, weather);
        return WeatherClaimResult.of(
                current == null ? WeatherClaimStatus.GRANTED : WeatherClaimStatus.UPDATED);
    }

    @Override
    public WeatherReleaseStatus release(PlayerRef subject, OverrideOwner owner) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(owner, "owner");

        OverrideOwner current = registry.owner(subject).orElse(null);
        if (current == null) {
            return WeatherReleaseStatus.NOT_HELD;
        }
        if (!current.equals(owner)) {
            return WeatherReleaseStatus.REFUSED_NOT_OWNER;
        }
        if (!backend.hasTracker(subject)) {
            registry.remove(subject);
            return WeatherReleaseStatus.SUBJECT_UNAVAILABLE;
        }

        backend.clearOverride(subject);
        registry.remove(subject);
        return WeatherReleaseStatus.RELEASED;
    }

    @Override
    public Optional<OverrideOwner> holder(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        return registry.owner(subject);
    }

    @Override
    public void forget(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        registry.remove(subject);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :core:test --tests '*DefaultPlayerWeatherControllerTest'`
Expected: PASS, 14 tests.

- [ ] **Step 5: Mutation-check the two load-bearing predicates**

Standing caution: *for a load-bearing predicate, delete it and re-run the suite.* Do this by hand, reverting after each:

1. In `claim`, delete the `case SENTINEL` branch so the sentinel falls through to `OK`.
   Expected: `the_sentinel_is_rejected_before_the_engine_is_touched` FAILS. Revert.
2. In `claim`, change `current != null && !current.equals(owner)` to `current != null && current != owner`.
   Expected: `updated_when_the_same_owner_reclaims` FAILS — reference identity is exactly the engine bug this type exists to avoid, so a test must catch it. Revert.

If either mutation leaves the suite green, the test is not testing what it claims; fix the test before continuing.

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git add core/src/main/java/io/github/legendaryforge/legendary/core/api/weather/PlayerWeatherController.java core/src/main/java/io/github/legendaryforge/legendary/core/internal/weather/DefaultPlayerWeatherController.java core/src/test/java/io/github/legendaryforge/legendary/core/internal/weather/DefaultPlayerWeatherControllerTest.java
git commit -m "feat(core): per-player weather override arbitration

One owner per subject, arbitrated here rather than by the engine. The engine
compares owners by reference identity on an arbitrary claimant Ref, so our
subsystems are indistinguishable to it and a same-claimant re-claim was measured
to be a silent overwrite.

Both pre-engine rejections are asserted by call count, not just by status: an
unknown weather and the index-0 sentinel must never reach the engine, because
claiming the sentinel returns true and does nothing.

Verified by mutation as well as assertion -- deleting the sentinel branch and
swapping value equality for reference identity each turn the suite red.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Harness contract test

**Files:**
- Create: `harness/src/test/java/io/github/legendaryforge/legendary/harness/weather/RecordingWeatherBackend.java`
- Create: `harness/src/test/java/io/github/legendaryforge/legendary/harness/weather/WeatherPortContractTest.java`

**Interfaces:**
- Consumes: the full `core.api.weather` surface plus `DefaultWeatherGate` and `DefaultPlayerWeatherController`.
- Produces: nothing other tasks depend on.

`harness` already depends on `:core` and owns its own doubles (`RecordingStormseekerHostRuntime`), so this needs no build change.

- [ ] **Step 1: Write the failing test**

`RecordingWeatherBackend.java`:

```java
package io.github.legendaryforge.legendary.harness.weather;

import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Harness-owned {@link WeatherBackend} that records the calls a consumer causes.
 *
 * <p>Separate from core's FakeWeatherBackend on purpose: test sources are not shared between
 * modules here, and this module already owns its doubles. A consumer-side test should also be free
 * to assert on call ORDER, which is what this records and core's fake does not.
 */
public final class RecordingWeatherBackend implements WeatherBackend {

    private final List<String> calls = new ArrayList<>();
    private final Map<String, WeatherId> applied = new HashMap<>();
    private final Map<String, WeatherId> environment = new HashMap<>();

    public void setEnvironmentWeather(String playerId, WeatherId weather) {
        environment.put(playerId, weather);
    }

    public List<String> calls() {
        return List.copyOf(calls);
    }

    @Override
    public boolean hasTracker(PlayerRef subject) {
        calls.add("hasTracker:" + subject.id());
        return true;
    }

    @Override
    public Optional<WeatherId> unoverriddenWeather(PlayerRef subject) {
        calls.add("unoverriddenWeather:" + subject.id());
        return Optional.ofNullable(environment.get(subject.id()));
    }

    @Override
    public WeatherResolution resolve(WeatherId weather) {
        calls.add("resolve:" + weather.value());
        return WeatherResolution.OK;
    }

    @Override
    public boolean applyOverride(PlayerRef subject, WeatherId weather) {
        calls.add("applyOverride:" + subject.id() + ":" + weather.value());
        applied.put(subject.id(), weather);
        return true;
    }

    @Override
    public boolean clearOverride(PlayerRef subject) {
        calls.add("clearOverride:" + subject.id());
        applied.remove(subject.id());
        return true;
    }

    @Override
    public void onTick(Object worldStore) {
        calls.add("onTick");
    }
}
```

`WeatherPortContractTest.java`:

```java
package io.github.legendaryforge.legendary.harness.weather;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef;
import io.github.legendaryforge.legendary.core.api.weather.OverrideOwner;
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherClaimStatus;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherKinds;
import io.github.legendaryforge.legendary.core.api.weather.WeatherReleaseStatus;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultPlayerWeatherController;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultWeatherGate;
import io.github.legendaryforge.legendary.core.internal.weather.OverrideRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consumer-side contract: what a questline may rely on from the weather port. */
class WeatherPortContractTest {

    private static final PlayerRef ALICE = new PlayerRef("alice");
    private static final WeatherId CALM = new WeatherId("Zone1_Cloudy_Medium");
    private static final WeatherId STORM = new WeatherId("Zone4_Thunder_Storm");
    private static final OverrideOwner QUESTLINE =
            new OverrideOwner(ResourceId.parse("legendary:stormseeker"));
    private static final OverrideOwner OTHER =
            new OverrideOwner(ResourceId.parse("legendary:other_questline"));

    private RecordingWeatherBackend backend;
    private WeatherGate gate;
    private PlayerWeatherController controller;

    @BeforeEach
    void setUp() {
        backend = new RecordingWeatherBackend();
        backend.setEnvironmentWeather("alice", CALM);
        OverrideRegistry registry = new OverrideRegistry();
        gate = new DefaultWeatherGate(backend, registry);
        controller = new DefaultPlayerWeatherController(backend, registry);
    }

    @Test
    void a_questline_can_storm_gate_one_player_without_touching_the_world() {
        assertFalse(WeatherKinds.isStorm(gate.effective(ALICE).orElseThrow()));

        assertEquals(WeatherClaimStatus.GRANTED, controller.claim(ALICE, STORM, QUESTLINE).status());

        assertTrue(WeatherKinds.isStorm(gate.effective(ALICE).orElseThrow()));
        assertFalse(
                WeatherKinds.isStorm(gate.unoverridden(ALICE).orElseThrow()),
                "the world's own weather must be unchanged");
    }

    @Test
    void releasing_restores_what_the_player_would_otherwise_see() {
        controller.claim(ALICE, STORM, QUESTLINE);

        assertEquals(WeatherReleaseStatus.RELEASED, controller.release(ALICE, QUESTLINE));
        assertEquals(Optional.of(CALM), gate.effective(ALICE));
    }

    @Test
    void two_questlines_cannot_silently_overwrite_each_other() {
        controller.claim(ALICE, STORM, QUESTLINE);

        var refused = controller.claim(ALICE, CALM, OTHER);

        assertEquals(WeatherClaimStatus.REFUSED_HELD, refused.status());
        assertEquals(Optional.of(QUESTLINE), refused.holder());
        assertTrue(
                WeatherKinds.isStorm(gate.effective(ALICE).orElseThrow()),
                "the first questline's gate must survive the second's attempt");
    }

    @Test
    void the_port_never_asks_the_engine_for_a_last_sent_value() {
        controller.claim(ALICE, STORM, QUESTLINE);
        gate.effective(ALICE);
        gate.unoverridden(ALICE);

        assertTrue(
                backend.calls().stream().noneMatch(call -> call.startsWith("weatherIndex")),
                "reads must resolve, never read the send-log");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :harness:test --tests '*WeatherPortContractTest'`
Expected: FAIL — compilation error, `RecordingWeatherBackend` does not exist until Step 1's first file is saved; if both files are saved it should pass, so run it before writing the double to see it fail, or accept compile failure as the failing state.

- [ ] **Step 3: Run test to verify it passes**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :harness:test --tests '*WeatherPortContractTest'`
Expected: PASS, 4 tests.

- [ ] **Step 4: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git add harness/src/test/java/io/github/legendaryforge/legendary/harness/weather
git commit -m "test(harness): consumer-side contract for the weather port

States what a questline may rely on: it can storm-gate one player without
changing the world, a release restores what the player would otherwise see, and
two questlines cannot silently overwrite each other's gate.

The harness owns its own recording double rather than reusing core's fake -- test
sources are not shared between modules and this module already follows that
pattern with RecordingStormseekerHostRuntime. It records call order, which a
consumer-side test wants and core's fake does not provide. Resolves spec open
question 2 by precedent; no java-test-fixtures plugin introduced.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: `HytaleWeatherBackend` and plugin wiring

**Files:**
- Create: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/weather/HytaleWeatherBackend.java`
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/LegendaryHytalePlugin.java`

**Interfaces:**
- Consumes: `WeatherBackend`, `WeatherId`, `WeatherResolution`, `PlayerRef` (core's), `DefaultWeatherGate`, `DefaultPlayerWeatherController`.
- Produces: `HytaleWeatherBackend` with the SPI methods plus three non-SPI members used only inside `mod/hytale` — `track(String playerId, com.hypixel…PlayerRef)`, `untrack(String playerId)`, and `rawIndex(String playerId)` returning `int` (`-1` when unavailable). `LegendaryHytalePlugin` exposes `weatherGate()` and `playerWeather()` accessors.

> **No tests, and none are possible.** `mod/hytale` compiles nothing on CI. Verification here is the build gates on this machine plus the live probe in Task 7.

- [ ] **Step 1: Write the adapter**

Every engine call below was read off the jar with `javap` and, where behavioural, confirmed by
`/weatherportprobe`. Do not substitute a plausible-looking alternative — an earlier draft guessed
three of these and all three were wrong.

```java
package io.github.legendaryforge.legendary.mod.hytale.weather;

import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.core.api.weather.WeatherBackend;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherResolution;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The only platform code in this port. Supersedes {@code HytaleWeatherReader}.
 *
 * <p>Deliberately NOT in the {@code stormseeker} package, where its predecessor lived: reading
 * weather is generic, and a second questline must not inherit a dependency on a package named for
 * the first.
 *
 * <p><b>Never calls {@code getWeatherIndex()}.</b> That is a send-log -- it returns the last value
 * actually sent to the client, so a read immediately after a claim returns the pre-claim value
 * (measured 2026-10-20). The predecessor read it in all three of its accessors.
 *
 * <p><b>Cannot answer "what weather is this player seeing".</b> {@link WeatherTracker} has no
 * override getter, so {@code WeatherGate.effective()} is computed in {@code core} from the port's
 * own registry. This class answers only the unoverridden reading.
 *
 * <p><b>Needs a store, which only a tick can supply.</b> {@code PluginBase} and {@code JavaPlugin}
 * expose no route to a {@code Store<EntityStore>}; {@code TickingSystem.tick} does.
 * {@link #onTick} caches it. Measured: the store and its {@link WeatherResource} are the same
 * instances across invocations, so caching is safe within a boot.
 *
 * <p>{@code getComponentConcurrent}, not {@code getComponent}: 0.7.2 deprecated the latter, whose
 * bytecode reads the {@code holder} field twice, so a concurrent null between check and use is an
 * NPE. Do not "simplify" this back.
 */
public final class HytaleWeatherBackend implements WeatherBackend {

    /**
     * Engine refs by player id. Its own map rather than borrowing
     * {@code HytaleStormseekerHost}'s, which is questline-scoped state. Driven from the plugin's
     * connect/disconnect handlers -- the same hook that calls {@code forget}, so the two lifecycles
     * cannot drift apart.
     */
    private final Map<String, com.hypixel.hytale.server.core.universe.PlayerRef> refs =
            new ConcurrentHashMap<>();

    private volatile Store<EntityStore> store;

    public void track(String playerId, com.hypixel.hytale.server.core.universe.PlayerRef ref) {
        refs.put(playerId, ref);
    }

    public void untrack(String playerId) {
        refs.remove(playerId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onTick(Object worldStore) {
        this.store = (Store<EntityStore>) worldStore;
    }

    /**
     * Raw engine index for debug output only -- not on the SPI, because {@code core} must never
     * hold an asset-map ordinal. Returns -1 when unavailable.
     */
    public int rawIndex(String playerId) {
        return unoverriddenIndex(playerId);
    }

    @Override
    public boolean hasTracker(
            io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject) {
        return store != null && trackerFor(subject.id()) != null;
    }

    @Override
    public Optional<WeatherId> unoverriddenWeather(
            io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject) {
        int index = unoverriddenIndex(subject.id());
        return index < 0 ? Optional.empty() : weatherIdOf(index);
    }

    @Override
    public WeatherResolution resolve(WeatherId weather) {
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return WeatherResolution.UNKNOWN;
        }
        // Measured: a missing key returns Integer.MIN_VALUE, not 0 -- which is the only reason
        // UNKNOWN and the index-0 sentinel are distinguishable here.
        int index = map.getIndex(weather.value());
        if (index < 0) {
            return WeatherResolution.UNKNOWN;
        }
        return index == 0 ? WeatherResolution.SENTINEL : WeatherResolution.OK;
    }

    @Override
    public boolean applyOverride(
            io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject,
            WeatherId weather) {
        WeatherTracker tracker = trackerFor(subject.id());
        Ref<EntityStore> claimant = claimantFor(subject.id());
        if (tracker == null || claimant == null) {
            return false;
        }
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return false;
        }
        int index = map.getIndex(weather.value());
        if (index <= 0) {
            return false;
        }
        return tracker.tryClaimOverride(claimant, index);
    }

    @Override
    public boolean clearOverride(
            io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef subject) {
        WeatherTracker tracker = trackerFor(subject.id());
        Ref<EntityStore> claimant = claimantFor(subject.id());
        if (tracker == null || claimant == null) {
            return false;
        }
        return tracker.releaseOverride(claimant);
    }

    /** -1 when unavailable. Resolves; never reads the send-log. */
    private int unoverriddenIndex(String playerId) {
        Store<EntityStore> current = store;
        WeatherTracker tracker = trackerFor(playerId);
        if (current == null || tracker == null) {
            return -1;
        }
        WeatherResource resource = current.getResource(WeatherResource.getResourceType());
        Ref<EntityStore> self = claimantFor(playerId);
        if (resource == null || self == null) {
            return -1;
        }
        // Nullable by design: resolveUnoverriddenWeatherIndex tolerates a null transform and
        // reuses the last known environment, which the override probe relies on too.
        TransformComponent transform =
                current.getComponent(self, TransformComponent.getComponentType());
        return tracker.resolveUnoverriddenWeatherIndex(resource, transform, current);
    }

    private WeatherTracker trackerFor(String playerId) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = refs.get(playerId);
        if (ref == null) {
            return null;
        }
        try {
            return ref.getComponentConcurrent(WeatherTracker.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ownership is reference identity on the engine side, and {@code getReference()} returns a
     * cached field (measured), so re-deriving here and on release yields the same instance the
     * engine compares.
     */
    private Ref<EntityStore> claimantFor(String playerId) {
        com.hypixel.hytale.server.core.universe.PlayerRef ref = refs.get(playerId);
        return ref == null ? null : ref.getReference();
    }

    private static Optional<WeatherId> weatherIdOf(int index) {
        IndexedLookupTableAssetMap<String, Weather> map = Weather.getAssetMap();
        if (map == null) {
            return Optional.empty();
        }
        Weather weather = map.getAsset(index);
        return weather == null ? Optional.empty() : Optional.of(new WeatherId(weather.getId()));
    }
}
```

Then feed the store from the tick system. In `StormseekerTickSystem`, which currently **discards**
its `store` parameter entirely:

```java
public final class StormseekerTickSystem extends TickingSystem<EntityStore> {

    private final HytaleStormseekerHost host;
    private final HytaleWeatherBackend weatherBackend;

    public StormseekerTickSystem(HytaleStormseekerHost host, HytaleWeatherBackend weatherBackend) {
        this.host = host;
        this.weatherBackend = weatherBackend;
    }

    @Override
    public void tick(float dt, int tickCount, Store<EntityStore> store) {
        // The only route to a Store<EntityStore> in this process. Without it the weather port
        // cannot resolve the unoverridden reading at all.
        weatherBackend.onTick(store);
        host.updateAllPositions();
        StormseekerWiring.tick(host);
    }
}
```

> **Naming note worth raising at review:** a questline-named tick system now feeds a generic port.
> That is acceptable for one questline and wrong for two. When questline #2 arrives this should
> become a small `WeatherTickSystem` registered independently; it is not worth the extra class
> today, but it IS worth the comment so the next reader sees the seam.

- [ ] **Step 2: Compile the module**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew :mod:hytale:compileJava`
Expected: BUILD SUCCESSFUL. Every signature above is measured, so a failure here means the game
updated under us. Read the real signature with
`javap -classpath "$HYTALE_JAR" com.hypixel.hytale.builtin.weather.components.WeatherTracker`
and treat it as API drift worth recording, not a typo to patch past.

- [ ] **Step 3: Wire it into the plugin**

In `LegendaryHytalePlugin.java`, add imports:

```java
import io.github.legendaryforge.legendary.core.api.weather.PlayerWeatherController;
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultPlayerWeatherController;
import io.github.legendaryforge.legendary.core.internal.weather.DefaultWeatherGate;
import io.github.legendaryforge.legendary.core.internal.weather.OverrideRegistry;
import io.github.legendaryforge.legendary.mod.hytale.weather.HytaleWeatherBackend;
```

Add fields beside `stormseekerHost`:

```java
private HytaleWeatherBackend weatherBackend;
private WeatherGate weatherGate;
private PlayerWeatherController playerWeather;
```

In `start()`, immediately before the `StormseekerTickSystem` registration — the tick system now takes the backend, so construct the port first:

```java
// Construct the port BEFORE the tick system, which now takes the backend.
weatherBackend = new HytaleWeatherBackend();
// One registry instance shared by both halves. If these ever get separate registries,
// effective() silently stops seeing claims -- and nothing fails, it just reads wrong.
OverrideRegistry overrideRegistry = new OverrideRegistry();
weatherGate = new DefaultWeatherGate(weatherBackend, overrideRegistry);
playerWeather = new DefaultPlayerWeatherController(weatherBackend, overrideRegistry);
getLogger().atInfo().log("Weather port wired (WeatherGate + PlayerWeatherController).");
```

Then change the existing tick-system construction a few lines below from
`new StormseekerTickSystem(stormseekerHost)` to
`new StormseekerTickSystem(stormseekerHost, weatherBackend)`.

Add accessors beside the existing ones:

```java
public WeatherGate weatherGate() {
    return weatherGate;
}

public PlayerWeatherController playerWeather() {
    return playerWeather;
}
```

In `onPlayerConnect`, after `stormseekerHost.addPlayer(playerId, playerRef);`:

```java
weatherBackend.track(playerId, playerRef);
```

In `onPlayerDisconnect`, after `stormseekerHost.removePlayer(playerId);`:

```java
// Both halves, together, because they are one lifecycle: untrack drops the engine ref and
// forget drops our owner registry entry. Dropping only the first leaks an owner per departed
// player, after which the next claim against a recycled id is wrongly refused.
weatherBackend.untrack(playerId);
playerWeather.forget(
        new io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef(playerId));
```

- [ ] **Step 4: Compile and run the build**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale/Legendary && ./gradlew build`
Expected: BUILD SUCCESSFUL, all seven guards pass.

- [ ] **Step 5: Read the verdict lines by name**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
python3 scripts/test-census.py | grep CENSUS_VERDICT
python3 scripts/coverage-census.py | grep COVERAGE_VERDICT
```

Expected: both `GREEN`, and `COVERAGE_VERDICT` showing **`0 exempt`** — which is what proves `mod/hytale` actually compiled against the real jar. A `1 exempt` here means the new adapter was not compiled and this task is unverified.

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git add mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/weather mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/LegendaryHytalePlugin.java
git commit -m "feat(mod): Hytale weather backend and port wiring

The only platform code in this port. Lives in mod/hytale/.../weather/ rather than
the stormseeker package its predecessor occupied: reading weather is generic, and
a second questline must not inherit a dependency on a package named for the first.

Never calls getWeatherIndex(). That is a send-log, so a read immediately after a
claim returns the pre-claim value; reads resolve through getEffectiveWeatherIndex
and resolveUnoverriddenWeatherIndex instead.

Disconnect drops both the engine ref and the owner registry entry, because they
are one lifecycle. Dropping only the first leaks an owner per departed player,
after which the next claim against a recycled id is wrongly refused.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Migrate the consumer, delete the predecessor, verify live

**Files:**
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/command/StormseekerStatusCommand.java:63-72`
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/LegendaryHytalePlugin.java` (command construction)
- Delete: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/stormseeker/HytaleWeatherReader.java`

**Interfaces:**
- Consumes: `weatherGate()` and `HytaleWeatherBackend.rawIndex(String)` from Task 6.
- Produces: nothing.

- [ ] **Step 1: Give the command the port**

`StormseekerStatusCommand`'s constructor currently takes only the host. Add the two it now needs, and update the single construction site in `LegendaryHytalePlugin.start()`:

```java
// LegendaryHytalePlugin.start() -- replace the existing registration line
getCommandRegistry().registerCommand(new StormseekerStatusCommand(stormseekerHost, weatherGate, weatherBackend));
```

In `StormseekerStatusCommand`, add fields and widen the constructor:

```java
private final WeatherGate weatherGate;
private final HytaleWeatherBackend weatherBackend;

public StormseekerStatusCommand(
        HytaleStormseekerHost host, WeatherGate weatherGate, HytaleWeatherBackend weatherBackend) {
    // ... keep whatever the existing constructor body does with host ...
    this.weatherGate = weatherGate;
    this.weatherBackend = weatherBackend;
}
```

- [ ] **Step 2: Replace the weather block**

Replace lines 63–72 (the `// Weather` block) with:

```java
// Weather -- through the port. The raw index comes from the adapter directly because
// core deliberately holds no asset-map ordinal; it is debug output, not a contract.
var weather = weatherGate.effective(
        new io.github.legendaryforge.legendary.core.api.questline.runtime.PlayerRef(playerId));
sb.append("Weather: ")
        .append(weather.map(WeatherId::value).orElse("unavailable"))
        .append(" (index=")
        .append(weatherBackend.rawIndex(playerId))
        .append(")");
sb.append(weather.map(WeatherKinds::isStorm).orElse(false) ? " [STORM]" : "").append("\n");
```

Add imports:

```java
import io.github.legendaryforge.legendary.core.api.weather.WeatherGate;
import io.github.legendaryforge.legendary.core.api.weather.WeatherId;
import io.github.legendaryforge.legendary.core.api.weather.WeatherKinds;
import io.github.legendaryforge.legendary.mod.hytale.weather.HytaleWeatherBackend;
```

Remove the now-unused import:

```java
import io.github.legendaryforge.legendary.mod.hytale.stormseeker.HytaleWeatherReader;
```

- [ ] **Step 3: Delete the predecessor and prove nothing references it**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git rm mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/stormseeker/HytaleWeatherReader.java
grep -rn 'HytaleWeatherReader' --include='*.java' . ; echo "exit=$?"
```

Expected: no matches, `exit=1`. A match means a caller was missed — fix it before continuing.

> Note the explicit `--include='*.java' .` and the absence of `2>/dev/null`: a recursive grep here must not be allowed to return a clean empty result for the wrong reason.

- [ ] **Step 4: Build and read the verdict lines**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
./gradlew build
python3 scripts/test-census.py | grep CENSUS_VERDICT
python3 scripts/coverage-census.py | grep COVERAGE_VERDICT
```

Expected: BUILD SUCCESSFUL; both verdicts `GREEN`; `0 exempt`.

- [ ] **Step 5: Verify live — the port against the real engine**

The build cannot tell you the adapter is correct, only that it compiles. Run the probe that measured the engine, then the migrated command:

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
./gradlew :mod:hytale:shadowJar
cp mod/hytale/build/libs/*-all.jar .scratch/hytale-server/mods/
bash scripts/live-probes/drive-weatheroverrideprobe-client.sh
```

Expected: the probe still passes 23/23 — it exercises the engine directly, so a failure means the engine moved, not that the port is wrong.

Also run `/weatherportprobe` **with the client attached**, which is the only way to close its test 3: that no public read path returns a claimed override index, that `resolveUnoverriddenWeatherIndex` ignores an active override, and that the same reading moves when world weather is forced. Headless it reports `SKIP`, which is no run. Test 3 is the behavioural confirmation of the one design decision taken on static evidence alone. Then, with a client attached, run `/stormseeker` in-game and confirm the `Weather:` line shows a weather id, an index, and `[STORM]` when appropriate.

Record the outcome. If the probe cannot be run in this session, say so explicitly rather than reporting the task verified — the build gates do not cover this module.

- [ ] **Step 6: Commit, PR, merge**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale/Legendary
git add mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/command/StormseekerStatusCommand.java mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/LegendaryHytalePlugin.java
git commit -m "refactor(mod): read weather through the port, delete HytaleWeatherReader

Gives the port a live call site, which is the difference between a mechanism and a
design -- the questline framework in core is the standing example of the latter.

Also fixes a real defect rather than just relocating code. All three of
HytaleWeatherReader's accessors read getWeatherIndex(), which is a send-log, so
every caller was reading the last value sent to the client rather than the
player's current weather.

The raw index stays in the debug output but comes from the adapter directly, since
core holds no asset-map ordinal by design.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>"

git push -u origin feat/weather-port
gh pr create --base main --title "feat: per-player weather override port (capability rank 1)"
git checkout main
gh pr merge <n> --merge --delete-branch
git pull --ff-only
```

---

## Self-Review

**Spec coverage.** Every section of the spec maps to a task:

| Spec section | Task |
|---|---|
| §1 scope, migration in scope | 7 |
| §2 logic in core / reuse / package correction | 3, 4, 6 |
| §3 ids not indices | 1 (`WeatherId`, `WeatherResolution`), 6 (`indexOf` confined to the adapter) |
| §4 component inventory | 1, 2, 3, 4, 6 |
| §4 `PlayerRef` disambiguation | Global Constraints; 6 uses fully-qualified names at the boundary |
| §5 six claim statuses | 4 |
| §5 three probe-forced rules | 4 (sentinel, no unchecked setters exposed), 3 + 6 (never `getWeatherIndex()`) |
| §5 `forget` | 4 (behaviour), 6 (the call site) |
| §5 arbitration is not precedence | 3 (`WeatherGate` javadoc) |
| §6 `WeatherBackend` contract | 3 |
| §7 testing | 4, 5, 6 step 5, 7 step 4 |
| §8 measured facts | cited inline in the javadoc of each type that depends on one |
| §9 open question 2 (fake's home) | 5 — resolved by precedent |

Spec §9 questions 1 and 3 are deliberately left open; neither blocks implementation.

**Placeholder scan.** No `TBD`/`TODO`/"handle edge cases"/"similar to Task N". Task 6 carries one honest unknown — two engine method names not covered by the probe — and names the exact `javap` command plus the step that discovers it, rather than leaving it to judgement.

**Type consistency.** Checked across tasks: `WeatherId.value()`, `WeatherResolution.{OK,UNKNOWN,SENTINEL}`, `WeatherClaimStatus` six constants, `WeatherClaimResult.of`/`refusedHeldBy`/`status()`/`holder()`/`isSuccess()`, `WeatherReleaseStatus` four constants, `WeatherBackend`'s six methods, `WeatherGate.effective`/`unoverridden`, `PlayerWeatherController.claim`/`release`/`holder`/`forget`, `FakeWeatherBackend`'s helpers, `HytaleWeatherBackend.track`/`untrack`/`rawIndex`. The Task 5 harness double implements the same six SPI methods as Task 3 defines.

One deliberate deviation from the spec is recorded in-plan and amended into the spec in Task 2 Step 5: `WeatherReleaseStatus`.

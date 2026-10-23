# Residue Anchor Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Derive residue anchors from terrain — per-element volume strength over a full world column, an absolute floor, and uniform-radius local-winner selection — and report per-element anchor spacing over a real region so the operator can judge crystal density.

**Architecture:** Pure `:core` logic behind the existing injected `TerrainSample` port, so every rule is testable with no game jar. Strength is a **count** of sample points satisfying an element's predicate across the whole 320-block column (§2 of the anchor-density spec), not a weighted sum. A candidate becomes an anchor when it clears an absolute floor **and** nothing within one uniform suppression radius beats it on `(strength, hash)`.

**The per-element difference is whether the element is a substance or a condition at a surface.** That is not a dial; it is what each element *is*, and it decides what gets counted:

| Element | What it is | Counted |
|---|---|---|
| Fire, Water, Earth | a **substance** filling volume | points holding it, through the whole column |
| Wind | a **condition at a surface** | points that are open **with solid directly below**, gated on environment and sky |
| **Lightning** | **its own treatment, not yet designed** | **nothing — out of scope here** |

Measured, not assumed. Earth's absolute count runs from **20%** of the column over an ocean floor to **80%** over a peak — a 4× range, so "a lot of rock" means "the ground is high here" and volume already discriminates it. Counting open air instead gives **62.5% over a plain against 20% over a peak**, which ranks a valley above a summit — inverted against §2's *"a high plateau or ridge system — not a lone crag."* Wind is therefore an area measure: exposed high terrain.

**Lightning gets its own treatment and this plan does not give it one** (operator, 2026-10-22). An earlier draft moved it to the surface measure by analogy with Wind, reasoning that lightning strikes ground. That was inventing a design for the one element with content depending on it — Stormseeker needs Lightning — so it is withdrawn rather than kept as a default. `VolumeStrength` **throws** if asked to score a `Weather` grounding, and the probe skips it and prints its row as pending. A number known to be wrong is worse than no number, and a silent zero is worse than both.

The surface predicate costs nothing. At step 8, "solid directly below" is the next sample down in the same column, which the scan already visits — so ordering the per-column walk makes it free, with no extra reads, no new port method, and no per-element window height.

**The hash stays as the tie-break it was designed to be.** In mid-ocean every point is water and every candidate ties, so the hash deciding is honest — there is no better place. What it is *not* is the mechanism Earth and Wind depend on; both have a real absolute measure. Anchors are invisible anyway (§7 removed nexus as a placed entity; §5 has players read crystal density), so arbitrary placement was permissible — the operator confirmed it on 2026-10-22 — but it is not needed, and a real measure gives a network with more character for the same cost.

The remaining per-element work is in **drawing the currents**, which this plan does not build: a least-cost path weighted by element affinity is vacuous for Earth and Wind because their affinity is everywhere, so each needs a y-profile — Earth deep in the mass, Wind hugging high ground. §5's near-earth crystal gate already does most of that work.

**Tech Stack:** Java 25, JUnit 5, Gradle multi-project. `:core` has no platform imports (enforced by `:core:checkNoPlatformImports`); the engine adapter lives in `:mod:hytale`.

## Global Constraints

- **`:core` must not import `com.hypixel.*`** — `:core:checkNoPlatformImports` fails the build. All engine reads go through `TerrainSample`.
- **`elementId` stays an opaque `String`.** No enum, no roster list, no validation, no default — this is a standing decision (`2026-10-21-residue-roster-decision.md`), and it is what makes a roster change a documentation edit.
- **Determinism is total.** Output must be a pure function of `(worldSeed, position, parameters)`. Use `ResidueRandom` (SplitMix64 finaliser), never `Math.random`, `Date.now`, or `hashCode()` of a record.
- **Suppression radius is uniform across elements**: `512` blocks. Influence radius `w = 8`, crossing tolerance `D = 8`, measurement radius `128`, sample step `8` — from `2026-10-22-residue-sizing-decision.md`.
- **World is 320 blocks tall, y = 0..319**, confirmed by the engine's `canHoldY` predicate. A "full column" means `0..319`, not `±span` around a surface.
- **Spotless + Error Prone run on `check`.** `palantirJavaFormat` reformats; `MissingOverride` and `EqualsHashCode` are ERROR.
- **Read verdict lines by name, never by position or exit code.** `grep CENSUS_VERDICT`, `grep COVERAGE_VERDICT`.
- **Never run `./gradlew` with anything appended, piped, or chained.** Redirect to a file, then read the file as a separate command.
- **Every Bash call opens with its own absolute `cd /home/stephaneb/Workspace/Projects/Hytale`.**

## File Structure

| File | Responsibility |
|---|---|
| `core/src/main/java/.../core/api/residue/WorldPoint3d.java` | **Create.** Immutable 3D world position. Anchors carry their own y (§4), which `WorldPoint2d` cannot express. |
| `core/src/main/java/.../core/api/residue/Anchor.java` | **Create.** One selected anchor: element, 3D position, strength count, sample denominator. |
| `core/src/main/java/.../core/api/residue/AnchorParameters.java` | **Create.** The new dials. Replaces `LatticeParameters`' scoring weights, which the volume model does not use. |
| `core/src/main/java/.../core/api/residue/TerrainSample.java` | **Modify.** Add `isSolidAt`. Earth's and Wind's §2 predicates are unanswerable without it. |
| `core/src/main/java/.../core/api/residue/ElementGrounding.java` | **Modify.** `Geometric` gains `Occupancy` (`SOLID`/`OPEN`) so one predicate serves Earth and Wind without a per-element branch. |
| `core/src/main/java/.../core/internal/residue/VolumeStrength.java` | **Create.** Per-element predicate + full-column count + modal-y. The measurement. |
| `core/src/main/java/.../core/internal/residue/AnchorSelector.java` | **Create.** Floor + uniform-radius local-winner with `(strength, hash)`. The selection. |
| `mod/hytale/src/main/java/.../mod/hytale/residue/SectionReaderTerrainSample.java` | **Modify.** Implement `isSolidAt` via the engine's `getBlock`. |
| `mod/hytale/src/main/java/.../mod/hytale/residue/HytaleDerivationRules.java` | **Modify.** Supply `Occupancy` for Earth and Wind. |
| `mod/hytale/src/main/java/.../mod/hytale/command/ResidueAnchorsCommand.java` | **Create.** `/residueanchors` — reports per-element anchor count and spacing. Prints `ANCHORS_VERDICT:`. |
| `mod/hytale/src/main/java/.../mod/hytale/LegendaryHytalePlugin.java` | **Modify.** Register the command and add it to the boot log. |

Measurement and selection are deliberately **two files**. Strength is a terrain question answerable with a fake sample; selection is a geometry question over already-scored candidates and needs no terrain at all. Keeping them apart means the selection tests never build a fake world.

---

### Task 1: `WorldPoint3d` and `Anchor`

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/WorldPoint3d.java`
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/Anchor.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/WorldPoint3dTest.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/AnchorTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `WorldPoint3d(double x, double y, double z)` with `double distanceTo(WorldPoint3d)` and `double horizontalDistanceTo(WorldPoint3d)`. `Anchor(String elementId, WorldPoint3d position, int strength, int samplesTaken)` with `double strengthFraction()`.

`horizontalDistanceTo` exists because suppression is a horizontal question — the world is 320 tall against a 512 radius, so any two points in one column are within the radius vertically and vertical distance cannot separate them.

- [ ] **Step 1: Write the failing tests**

```java
// WorldPoint3dTest.java
package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldPoint3dTest {

    @Test
    void distanceIsEuclideanInThreeAxes() {
        WorldPoint3d a = new WorldPoint3d(0, 0, 0);
        WorldPoint3d b = new WorldPoint3d(3, 4, 12);
        assertEquals(13.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void horizontalDistanceIgnoresY() {
        WorldPoint3d low = new WorldPoint3d(0, 10, 0);
        WorldPoint3d high = new WorldPoint3d(3, 300, 4);
        assertEquals(5.0, low.horizontalDistanceTo(high), 1e-9);
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint3d(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint3d(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPoint3d(0, 0, Double.NEGATIVE_INFINITY));
    }
}
```

```java
// AnchorTest.java
package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnchorTest {

    private static final WorldPoint3d POS = new WorldPoint3d(10, 64, 20);

    @Test
    void strengthFractionIsStrengthOverSamples() {
        Anchor anchor = new Anchor("water", POS, 250, 1000);
        assertEquals(0.25, anchor.strengthFraction(), 1e-9);
    }

    @Test
    void strengthFractionIsZeroWhenNoSamplesTaken() {
        Anchor anchor = new Anchor("water", POS, 0, 0);
        assertEquals(0.0, anchor.strengthFraction(), 1e-9);
    }

    @Test
    void rejectsBlankElementId() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor("  ", POS, 1, 10));
    }

    @Test
    void rejectsStrengthExceedingSamples() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor("water", POS, 11, 10));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Anchor("water", POS, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Anchor("water", POS, 0, -1));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests '*WorldPoint3dTest' --tests '*AnchorTest' > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: compilation failure — `cannot find symbol: class WorldPoint3d`.

- [ ] **Step 3: Write the implementations**

```java
// WorldPoint3d.java
package io.github.legendaryforge.legendary.core.api.residue;

/**
 * An immutable world position with elevation.
 *
 * <p>Anchors carry their own y (anchor-density design §4), which retires the shared-reference-plane
 * defect: every candidate in a run used to be scored against one {@code referencePlaneY}, so the
 * same candidate scored differently depending on where the player stood.
 */
public record WorldPoint3d(double x, double y, double z) {

    public WorldPoint3d {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException(
                    "WorldPoint3d requires finite coordinates, got " + x + "," + y + "," + z);
        }
    }

    /** Euclidean distance in all three axes. */
    public double distanceTo(WorldPoint3d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Euclidean distance ignoring elevation.
     *
     * <p>Suppression is a horizontal question and this is the method it uses. The world is 320
     * blocks tall against a 512-block suppression radius, so any two points in one column are
     * already within the radius vertically — vertical distance cannot separate two anchors, and
     * including it would only weaken a comparison that has to be made on x/z.
     */
    public double horizontalDistanceTo(WorldPoint3d other) {
        return Math.hypot(x - other.x, z - other.z);
    }
}
```

```java
// Anchor.java
package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Objects;

/**
 * One selected anchor: a place with a decent concentration of one element.
 *
 * <p>{@code strength} is a <strong>count of sample points</strong> satisfying the element's
 * predicate, not a weighted score — anchor-density design §2, "strength is volume". {@code
 * samplesTaken} is carried alongside it so the count is interpretable: 250 means nothing without
 * the denominator, and the denominator changes with the measurement radius and sample step.
 *
 * @param elementId the element, opaque to {@code :core}
 * @param position where the anchor sits, including its own elevation
 * @param strength satisfying sample points found in the measurement window
 * @param samplesTaken loaded sample points examined, the denominator for {@code strength}
 */
public record Anchor(String elementId, WorldPoint3d position, int strength, int samplesTaken) {

    public Anchor {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(position, "position");
        if (elementId.isBlank()) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
        if (strength < 0) {
            throw new IllegalArgumentException("strength must be non-negative, got " + strength);
        }
        if (samplesTaken < 0) {
            throw new IllegalArgumentException("samplesTaken must be non-negative, got " + samplesTaken);
        }
        if (strength > samplesTaken) {
            throw new IllegalArgumentException(
                    "strength " + strength + " exceeds samplesTaken " + samplesTaken + ", which cannot happen");
        }
    }

    /** Satisfying fraction of the window, or 0 when nothing was sampled. */
    public double strengthFraction() {
        return samplesTaken == 0 ? 0.0 : strength / (double) samplesTaken;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*WorldPoint3dTest' --tests '*AnchorTest' > /tmp/t.log 2>&1`
Then: `grep -E "tests completed|BUILD" /tmp/t.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/WorldPoint3d.java \
        core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/Anchor.java \
        core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/WorldPoint3dTest.java \
        core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/AnchorTest.java
git commit -m "feat(core): WorldPoint3d and Anchor — anchors carry their own elevation"
```

---

### Task 2: `AnchorParameters`

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/AnchorParameters.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/AnchorParametersTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `AnchorParameters(int cellSize, double jitterFraction, int suppressionRadius, int measurementRadius, int sampleStep, double minimumStrengthFraction, int influenceRadius, int crossingTolerance)`, plus `static AnchorParameters defaults()` and `int worldHeight()` returning `320`.

`minimumStrengthFraction` is the floor, expressed as a **fraction of samples taken** rather than an absolute count. That is deliberate: an absolute count silently changes meaning when `measurementRadius` or `sampleStep` moves, and both are expected to move. A fraction survives both.

- [ ] **Step 1: Write the failing test**

```java
package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnchorParametersTest {

    @Test
    void defaultsMatchTheSizingDecision() {
        AnchorParameters p = AnchorParameters.defaults();
        assertEquals(512, p.suppressionRadius());
        assertEquals(128, p.measurementRadius());
        assertEquals(8, p.sampleStep());
        assertEquals(8, p.influenceRadius());
        assertEquals(8, p.crossingTolerance());
    }

    @Test
    void crossingToleranceMayNotExceedInfluenceRadius() {
        AnchorParameters p = AnchorParameters.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnchorParameters(
                        p.cellSize(), p.jitterFraction(), p.suppressionRadius(), p.measurementRadius(),
                        p.sampleStep(), p.minimumStrengthFraction(), 8, 9));
    }

    @Test
    void cellSizeMayNotExceedSuppressionRadius() {
        AnchorParameters p = AnchorParameters.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnchorParameters(
                        1024, p.jitterFraction(), 512, p.measurementRadius(),
                        p.sampleStep(), p.minimumStrengthFraction(), p.influenceRadius(), p.crossingTolerance()));
    }

    @Test
    void floorMustBeAFraction() {
        AnchorParameters p = AnchorParameters.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnchorParameters(
                        p.cellSize(), p.jitterFraction(), p.suppressionRadius(), p.measurementRadius(),
                        p.sampleStep(), 1.5, p.influenceRadius(), p.crossingTolerance()));
    }

    @Test
    void worldHeightIsThreeHundredAndTwenty() {
        assertEquals(320, AnchorParameters.defaults().worldHeight());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*AnchorParametersTest' > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: `cannot find symbol: class AnchorParameters`.

- [ ] **Step 3: Write the implementation**

```java
package io.github.legendaryforge.legendary.core.api.residue;

/**
 * The anchor model's dials.
 *
 * <p>Separate from {@link LatticeParameters} rather than an extension of it, because the volume
 * model does not use that record's scoring weights at all — {@code fluidWeight},
 * {@code environmentWeight}, {@code skyWeight} and {@code qualifyingScore} belong to the
 * weighted-sum scoring the anchor-density design §2 replaced with counting.
 *
 * <p><strong>One suppression radius, not five.</strong> The per-element differences the operator
 * described — Fire's rarity, Earth's and Wind's abundance — come from
 * {@code minimumStrengthFraction} and from each grounding's predicate, not from a radius tuned per
 * element. Uniform spacing is what makes currents connectable and crystal density predictable, and
 * it is the only dial that sets how far a player walks between crystals.
 *
 * @param cellSize lattice cell edge in blocks; one candidate per cell. Must not exceed
 *     {@code suppressionRadius}, or candidates would be sparser than anchors and selection would
 *     have nothing to choose between
 * @param jitterFraction how far a candidate may sit from its cell centre, as a fraction of
 *     {@code cellSize} per axis, in {@code [0, 0.5]}
 * @param suppressionRadius a site must beat everything within this horizontal distance to become an
 *     anchor. Sets anchor density at roughly one per {@code suppressionRadius²} of qualifying terrain
 * @param measurementRadius horizontal half-width of the window strength is counted over
 * @param sampleStep spacing between sampled positions in all three axes
 * @param minimumStrengthFraction absolute floor, as a fraction of samples taken. A region whose
 *     best candidate falls below this gets <strong>no anchor for that element</strong>, which is how
 *     rarity handles itself: nowhere near lava produces no Fire anchor, with nothing tuned to say so
 * @param influenceRadius {@code w} — crystals may spawn within this distance of a current
 * @param crossingTolerance {@code D} — two currents passing within this distance cross. Bounded by
 *     {@code influenceRadius}: beyond it a geode could form outside both crystal bands, in ground
 *     showing nothing that explains it
 */
public record AnchorParameters(
        int cellSize,
        double jitterFraction,
        int suppressionRadius,
        int measurementRadius,
        int sampleStep,
        double minimumStrengthFraction,
        int influenceRadius,
        int crossingTolerance) {

    /** Hytale's world height, y = 0..319, confirmed by the engine's own {@code canHoldY} predicate. */
    private static final int WORLD_HEIGHT = 320;

    public AnchorParameters {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive, got " + cellSize);
        }
        if (jitterFraction < 0 || jitterFraction > 0.5) {
            throw new IllegalArgumentException("jitterFraction must be in [0, 0.5], got " + jitterFraction);
        }
        if (suppressionRadius <= 0) {
            throw new IllegalArgumentException("suppressionRadius must be positive, got " + suppressionRadius);
        }
        if (cellSize > suppressionRadius) {
            throw new IllegalArgumentException("cellSize " + cellSize
                    + " exceeds suppressionRadius " + suppressionRadius
                    + ", so candidates would be sparser than anchors and selection would have no choice");
        }
        if (measurementRadius < 0) {
            throw new IllegalArgumentException("measurementRadius must be non-negative, got " + measurementRadius);
        }
        if (sampleStep <= 0) {
            throw new IllegalArgumentException("sampleStep must be positive, got " + sampleStep);
        }
        if (!(minimumStrengthFraction >= 0) || minimumStrengthFraction > 1) {
            throw new IllegalArgumentException(
                    "minimumStrengthFraction must be in [0,1], got " + minimumStrengthFraction);
        }
        if (influenceRadius <= 0) {
            throw new IllegalArgumentException("influenceRadius must be positive, got " + influenceRadius);
        }
        if (crossingTolerance <= 0) {
            throw new IllegalArgumentException("crossingTolerance must be positive, got " + crossingTolerance);
        }
        if (crossingTolerance > influenceRadius) {
            throw new IllegalArgumentException("crossingTolerance " + crossingTolerance
                    + " exceeds influenceRadius " + influenceRadius
                    + ", which would admit a geode outside both crystal bands");
        }
    }

    /** Hytale's world height in blocks. A full-column window spans {@code 0 .. worldHeight() - 1}. */
    public int worldHeight() {
        return WORLD_HEIGHT;
    }

    /**
     * The values decided in {@code 2026-10-22-residue-sizing-decision.md}.
     *
     * <p>{@code suppressionRadius} 512 derives from one provisional operator judgement — about 15%
     * of the world near some current — through {@code R = 10w/C}. {@code minimumStrengthFraction} is
     * the one value here with no stated basis: it exists to stop a region with a trace of an element
     * minting an anchor, and 0.01 is a starting guess to be moved once the probe shows what real
     * terrain produces.
     */
    public static AnchorParameters defaults() {
        return new AnchorParameters(256, 0.35, 512, 128, 8, 0.01, 8, 8);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*AnchorParametersTest' > /tmp/t.log 2>&1`
Then: `grep -E "tests completed|BUILD" /tmp/t.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/AnchorParameters.java \
        core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/AnchorParametersTest.java
git commit -m "feat(core): AnchorParameters — one suppression radius, a fractional floor"
```

---

### Task 3: `TerrainSample.isSolidAt` and `Geometric.Occupancy`

This task changes two published interfaces, so it must fix every implementor and caller in the same commit or the build breaks.

**Files:**
- Modify: `core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/TerrainSample.java`
- Modify: `core/src/main/java/io/github/legendaryforge/legendary/core/api/residue/ElementGrounding.java`
- Modify: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/residue/SiteScorerTest.java` (its `FakeTerrain` implements the port; add the method, and fix `Geometric` constructor calls)
- Modify: `core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/ElementIdentityTest.java` (fix `Geometric` constructor calls if present)
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/residue/HytaleDerivationRules.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/api/residue/ElementGroundingOccupancyTest.java` (create)

**Interfaces:**
- Consumes: nothing.
- Produces: `boolean TerrainSample.isSolidAt(int x, int y, int z)`. `ElementGrounding.Occupancy` enum with constants `SOLID` and `OPEN`. `Geometric(String elementId, Set<String> environments, int minSkyLight, int maxSkyLight, Occupancy occupancy)` — a **fifth** component; every existing 4-argument call site must be updated.

**Before starting, find every call site:**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
grep -rn "new ElementGrounding.Geometric\|implements TerrainSample" --include="*.java" . | grep -v "/build/"
```

- [ ] **Step 1: Write the failing test**

```java
package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Earth and Wind are both {@code Geometric} and score by one predicate, so the thing that must
 * differ between them is what counts as present: rock for Earth, open air for Wind. Encoding that
 * as {@code Occupancy} keeps it one rule rather than a per-element branch.
 */
class ElementGroundingOccupancyTest {

    @Test
    void earthCountsSolidAndWindCountsOpen() {
        ElementGrounding.Geometric earth =
                new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, ElementGrounding.Occupancy.SOLID);
        ElementGrounding.Geometric wind =
                new ElementGrounding.Geometric("wind", Set.of("Mountains"), 12, 15, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID);
        assertEquals(ElementGrounding.Occupancy.SOLID, earth.occupancy());
        assertEquals(ElementGrounding.Occupancy.OPEN_ABOVE_SOLID, wind.occupancy());
    }

    @Test
    void occupancyIsRequired() {
        assertThrows(
                NullPointerException.class,
                () -> new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*ElementGroundingOccupancyTest' > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: `cannot find symbol: class Occupancy`.

- [ ] **Step 3: Add `Occupancy` and the fifth component**

In `ElementGrounding.java`, add the enum inside the sealed interface, immediately before `record Physical`:

```java
    /**
     * What counts as the element being present at a sampled point, for a {@link Geometric}
     * grounding.
     *
     * <p>Earth and Wind are the same grounding scored by the same rule, and the only thing that
     * differs is this. Anchor-density design §2: Earth counts points where "the point is rock",
     * Wind counts points that are "open air in a matching environment". Without this the scorer
     * needs an {@code elementId} branch, which is exactly the per-element special-casing the sealed
     * hierarchy exists to prevent.
     *
     * <p><strong>{@code SOLID} is expected to tie nearly everywhere, and that is the intent.</strong>
     * Rock fills most of every column, so Earth's strength barely varies and the {@code (strength,
     * hash)} tie-break decides — producing an even spread rather than a terrain-driven one. For a
     * substance that is everywhere there is no better place, and an anchor is invisible, so there is
     * nothing for a player to find unmotivating.
     */
    enum Occupancy {
        /**
         * The point counts when it is solid. A concentration of earth is a lot of rock, and that
         * already discriminates: a column is 20% solid over an ocean floor and 80% over a peak.
         */
        SOLID,
        /**
         * The point counts when it is open <strong>and the point one sample below it is solid</strong>
         * — the exposed surface layer.
         *
         * <p>Not plain "open", which is inverted: counting all open air in a column scores 62.5%
         * over a plain against 20% over a peak, ranking a valley above a summit. Wind and Lightning
         * are conditions at a surface rather than substances filling volume, so what they measure is
         * exposed <em>area</em>: high terrain under open sky, and storm belt where lightning lands.
         */
        OPEN_ABOVE_SOLID
    }
```

Replace the `Geometric` record with:

```java
    record Geometric(
            String elementId, Set<String> environments, int minSkyLight, int maxSkyLight, Occupancy occupancy)
            implements ElementGrounding {
        public Geometric {
            requireElementId(elementId);
            environments = Set.copyOf(Objects.requireNonNull(environments, "environments"));
            requireSkyRange(minSkyLight, maxSkyLight);
            Objects.requireNonNull(occupancy, "occupancy");
            if (environments.isEmpty()) {
                throw new IllegalArgumentException(
                        "Geometric grounding for " + elementId + " names no environment, so only its sky range "
                                + "would decide — which qualifies every column in the world at that exposure.");
            }
        }
    }
```

Keep the existing javadoc block above `record Geometric` unchanged.

- [ ] **Step 4: Add `isSolidAt` to the port**

In `TerrainSample.java`, add after `skyLightAt`:

```java
    /**
     * Whether this position is occupied by a solid block, {@code false} when unloaded.
     *
     * <p>Added because §2's Earth and Wind predicates — "the point is rock" and "the point is open
     * air" — were not answerable through this port at all. Fluid, environment, sky light, surface
     * height and storm probability between them cannot distinguish stone from air, so a volume
     * count for either element was impossible before this.
     *
     * <p>Returns {@code false} for an unloaded position, which is indistinguishable from real air
     * without {@link #isLoaded} — the same contract {@link #skyLightAt} documents, and the reason
     * {@code isLoaded} is checked before every read rather than after.
     *
     * <p><strong>Solidity is a resolution, so it belongs on this side of the port.</strong> The
     * engine answers in block-type ids whose meaning only the adapter can know; a module that
     * cannot import Hytale cannot classify them. The adapter decides; {@code :core} asks a question
     * in its own vocabulary.
     */
    boolean isSolidAt(int x, int y, int z);
```

- [ ] **Step 5: Fix every implementor and call site**

In `SiteScorerTest.java`'s `FakeTerrain`, add a solid-position set and the override:

```java
        private final java.util.Set<String> solids = new java.util.HashSet<>();

        FakeTerrain solid(int x, int y, int z) {
            solids.add(key(x, y, z));
            return this;
        }

        @Override
        public boolean isSolidAt(int x, int y, int z) {
            return everythingLoaded && solids.contains(key(x, y, z));
        }
```

In the same file, add `, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID` to the `WIND` constant and `, ElementGrounding.Occupancy.SOLID` to the `EARTH` constant:

```java
    private static final ElementGrounding WIND =
            new ElementGrounding.Geometric(
                    "wind", Set.of("Mountains", "Plateaus"), 12, 15, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID);
    private static final ElementGrounding EARTH =
            new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, ElementGrounding.Occupancy.SOLID);
```

In `HytaleDerivationRules.java`, update both `Geometric` constructions:

```java
                new ElementGrounding.Geometric(
                        "wind",
                        Set.of("Mountains", "Peaks", "Highlands", "Plateaus", "Cliffs"),
                        12,
                        15,
                        ElementGrounding.Occupancy.OPEN_ABOVE_SOLID),

                new ElementGrounding.Geometric(
                        "earth",
                        Set.of("Caves", "Deserts", "Badlands", "Mineshafts"),
                        0,
                        6,
                        ElementGrounding.Occupancy.SOLID),
```

**Earth's environment set and sky band must then come off, and this is the substantive rule change in the task.** They are leftovers from the rejected cave framing: requiring a `Caves`/`Deserts`/`Badlands`/`Mineshafts` environment and sky light 0–6 excludes **a mountain's bulk**, which is the one case §2 names as strong Earth. Rock does not need the game to label the area a cave, and it certainly does not need darkness. Since `Geometric` requires a non-empty environment set by construction, express "rock is rock" by widening the gates to accept everything rather than by weakening the record's invariant:

```java
                // Earth is rock, and rock needs no environment label and no darkness. The cave
                // environment set and the 0..6 sky band this once carried were the inverted framing
                // §2 rejected — they excluded a mountain's bulk, the clearest case of a lot of earth.
                // Sky 0..15 and a substring that every environment id contains keep the record's
                // non-empty invariant while gating on occupancy alone.
                new ElementGrounding.Geometric(
                        "earth", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.SOLID),
```

Wind keeps both gates, because exposure *is* what Wind is: open air, high up, under sky. There the environment set and the 12–15 band are the definition rather than decoration.

Apply the same fifth argument to any other call site the Step-0 grep found. In `SectionReaderTerrainSample.java`, add a temporary implementation so the module compiles; Task 6 replaces it:

```java
    @Override
    public boolean isSolidAt(int x, int y, int z) {
        // Replaced in the adapter task, which must first measure how the engine reports air.
        // Throwing rather than returning a guess: a wrong constant inverts Earth and Wind, and a
        // guess that compiles is how that ships unnoticed.
        throw new UnsupportedOperationException("isSolidAt not implemented yet — see Task 6");
    }
```

- [ ] **Step 6: Run the full core and harness suites**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test :harness:test > /tmp/t.log 2>&1`
Then: `grep -E "error:|tests completed|BUILD" /tmp/t.log`
Expected: `BUILD SUCCESSFUL`. If a `Geometric` call site was missed, the error names the file and line.

- [ ] **Step 7: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add -A core/src mod/hytale/src
git commit -m "feat(core): TerrainSample.isSolidAt and Geometric.Occupancy

§2's Earth and Wind predicates were unanswerable through the port: fluid,
environment, sky light, surface height and storm probability cannot
distinguish stone from air. Occupancy makes one Geometric rule serve both
without an elementId branch."
```

---

### Task 4: `VolumeStrength`

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/internal/residue/VolumeStrength.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/residue/VolumeStrengthTest.java`

**Interfaces:**
- Consumes: `AnchorParameters`, `ElementGrounding`, `TerrainSample`, `WorldPoint2d`, `WorldPoint3d`, `Anchor`.
- Produces: `public static Optional<Anchor> measure(TerrainSample sample, AnchorParameters parameters, ElementGrounding grounding, WorldPoint2d candidate)`. Returns empty when nothing was loaded or nothing satisfied the predicate. The returned `Anchor` has **not** been through the floor or suppression — it is a measurement, not a selection.

The window is the full world column: `y` from `0` to `worldHeight() - 1` by `sampleStep`, `x` and `z` across `±measurementRadius` by `sampleStep`. The anchor's y is the **modal sampled level** — the y with the most satisfying samples, lowest y winning a tie. That resolves §4's ambiguity: a column holding a surface lake and an underground river has two fluid surfaces and §4 names one, so the modal level picks the denser body instead of landing between them.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Volume counting, tested against a fake sample. A lake must out-score a puddle, and the anchor's
 * elevation must land in the body rather than between two of them.
 */
class VolumeStrengthTest {

    /** Small radius and coarse step keep the fakes legible: 3x3 columns, 4 sampled levels. */
    private static final AnchorParameters P =
            new AnchorParameters(256, 0.35, 512, 80, 80, 0.01, 8, 8);

    private static final ElementGrounding WATER =
            new ElementGrounding.Physical("water", Set.of("Water_Source"), Set.of("Shores"));
    private static final ElementGrounding EARTH =
            new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, ElementGrounding.Occupancy.SOLID);
    private static final ElementGrounding WIND =
            new ElementGrounding.Geometric("wind", Set.of("Mountains"), 12, 15, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID);

    private static final class FakeTerrain implements TerrainSample {
        private final Map<String, String> fluids = new HashMap<>();
        private final Set<String> solids = new HashSet<>();
        private String environment = "Env_Zone1_Shores";
        private int sky = 15;

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }

        FakeTerrain fluidEverywhereAtLevel(int y, String id) {
            for (int x = -80; x <= 80; x += 80) {
                for (int z = -80; z <= 80; z += 80) {
                    fluids.put(key(x, y, z), id);
                }
            }
            return this;
        }

        FakeTerrain fluidAtOnePoint(int x, int y, int z, String id) {
            fluids.put(key(x, y, z), id);
            return this;
        }

        FakeTerrain solidEverywhereAtLevel(int y) {
            for (int x = -80; x <= 80; x += 80) {
                for (int z = -80; z <= 80; z += 80) {
                    solids.add(key(x, y, z));
                }
            }
            return this;
        }

        FakeTerrain environment(String id) {
            this.environment = id;
            return this;
        }

        FakeTerrain sky(int value) {
            this.sky = value;
            return this;
        }

        @Override
        public boolean isLoaded(int x, int y, int z) {
            return true;
        }

        @Override
        public Optional<String> fluidAt(int x, int y, int z) {
            return Optional.ofNullable(fluids.get(key(x, y, z)));
        }

        @Override
        public Optional<String> environmentAt(int x, int y, int z) {
            return Optional.ofNullable(environment);
        }

        @Override
        public int skyLightAt(int x, int y, int z) {
            return sky;
        }

        @Override
        public boolean isSolidAt(int x, int y, int z) {
            return solids.contains(key(x, y, z));
        }

        @Override
        public OptionalInt surfaceHeightAt(int x, int z) {
            return OptionalInt.of(64);
        }

        @Override
        public OptionalDouble stormProbabilityAt(int x, int y, int z) {
            return OptionalDouble.of(0.0);
        }
    }

    private static final WorldPoint2d ORIGIN = new WorldPoint2d(0, 0);

    @Test
    void aFullLevelOutScoresASinglePoint() {
        Anchor lake = VolumeStrength.measure(
                        new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source"), P, WATER, ORIGIN)
                .orElseThrow();
        Anchor puddle = VolumeStrength.measure(
                        new FakeTerrain().fluidAtOnePoint(0, 80, 0, "Water_Source"), P, WATER, ORIGIN)
                .orElseThrow();
        assertEquals(9, lake.strength());
        assertEquals(1, puddle.strength());
        assertTrue(lake.strength() > puddle.strength());
    }

    @Test
    void emptyWhenNothingSatisfiesThePredicate() {
        assertTrue(VolumeStrength.measure(new FakeTerrain(), P, WATER, ORIGIN).isEmpty());
    }

    @Test
    void anchorElevationIsTheModalLevelNotTheMidpoint() {
        // A dense body at y=80 and one stray point at y=240. The anchor must sit at 80, not at 160.
        FakeTerrain twoBodies = new FakeTerrain()
                .fluidEverywhereAtLevel(80, "Water_Source")
                .fluidAtOnePoint(0, 240, 0, "Water_Source");
        Anchor anchor = VolumeStrength.measure(twoBodies, P, WATER, ORIGIN).orElseThrow();
        assertEquals(10, anchor.strength());
        assertEquals(80.0, anchor.position().y(), 1e-9);
    }

    @Test
    void samplesTakenIsTheWholeColumnWindow() {
        // x,z in {-80,0,80} = 3x3; y in {0,80,160,240} = 4 levels (320 is out of bounds).
        Anchor anchor = VolumeStrength.measure(
                        new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source"), P, WATER, ORIGIN)
                .orElseThrow();
        assertEquals(36, anchor.samplesTaken());
    }

    @Test
    void earthCountsRockAndWindCountsTheExposedSurface() {
        FakeTerrain rock = new FakeTerrain().solidEverywhereAtLevel(80).environment("Env_Zone1_Caves").sky(0);
        Anchor earth = VolumeStrength.measure(rock, P, EARTH, ORIGIN).orElseThrow();
        assertEquals(9, earth.strength());

        // Same world read as Wind. Only y=160 counts: it is open and the sample one step below
        // (y=80) is solid. y=0 is open with nothing below, y=240's neighbour below is open air.
        // Counting every open level instead would give 27 and rank a valley above a summit.
        FakeTerrain ridge = new FakeTerrain().solidEverywhereAtLevel(80).environment("Env_Zone1_Mountains").sky(15);
        Anchor wind = VolumeStrength.measure(ridge, P, WIND, ORIGIN).orElseThrow();
        assertEquals(9, wind.strength());
        assertEquals(160.0, wind.position().y(), 1e-9, "the Wind anchor sits on the exposed surface");
    }

    @Test
    void wideGatesCountSolidPointsUnderAnyEnvironmentAndAnySky() {
        // The mechanism Earth relies on in production: gates widened to accept everything, so
        // occupancy alone decides and a mountain's bulk is not excluded for lacking a cave label.
        ElementGrounding rockIsRock =
                new ElementGrounding.Geometric("earth", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.SOLID);
        FakeTerrain mountain =
                new FakeTerrain().solidEverywhereAtLevel(80).environment("Env_Zone1_Mountains").sky(15);
        Anchor earth = VolumeStrength.measure(mountain, P, rockIsRock, ORIGIN).orElseThrow();
        assertEquals(9, earth.strength(), "rock under a Mountains environment at full sky must still count");
    }

    @Test
    void geometricRequiresBothEnvironmentAndSkyBand() {
        FakeTerrain wrongSky = new FakeTerrain().solidEverywhereAtLevel(80).environment("Env_Zone1_Caves").sky(15);
        assertTrue(VolumeStrength.measure(wrongSky, P, EARTH, ORIGIN).isEmpty());

        FakeTerrain wrongEnvironment =
                new FakeTerrain().solidEverywhereAtLevel(80).environment("Env_Zone1_Plains").sky(0);
        assertTrue(VolumeStrength.measure(wrongEnvironment, P, EARTH, ORIGIN).isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*VolumeStrengthTest' > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: `cannot find symbol: class VolumeStrength`.

- [ ] **Step 3: Write the implementation**

```java
package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint3d;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How much of one element is in a candidate's window — anchor-density design §2, "strength is
 * volume".
 *
 * <p>A <strong>count</strong> of sample points satisfying the element's predicate, not a weighted
 * sum of matched signals. The difference is the whole point: the weighted scorer returned the same
 * answer for a trickle and a caldera, so "strong" had nothing to grip.
 *
 * <p><strong>The window is the full world column.</strong> Anchors may sit above or below ground, so
 * a span chosen around a surface would decide in advance where they can be — and underground rivers
 * and deep lava lakes are exactly the features such a span would hide. The V1 column chunk shape
 * means a loaded column is loaded for its whole height, so this costs reads rather than loads.
 *
 * <p><strong>The anchor's elevation is the modal sampled level</strong>, lowest y winning a tie.
 * §4 says a {@code Physical} anchor takes y from "the fluid surface in the window" — singular, which
 * a column holding both a surface lake and an underground river does not have. The modal level picks
 * the denser body; a mean or a midpoint would place the anchor in the rock between them.
 */
public final class VolumeStrength {

    private VolumeStrength() {}

    /**
     * Measure one element at one candidate, before any floor or suppression is applied.
     *
     * <p>Empty when no sampled point satisfied the predicate, which covers both "no qualifying
     * terrain" and "nothing loaded" — the caller distinguishes them through {@link Anchor#samplesTaken}
     * on a non-empty result, and a probe should report a zero-sample window as unknown rather than
     * as absent.
     */
    public static Optional<Anchor> measure(
            TerrainSample sample,
            AnchorParameters parameters,
            ElementGrounding grounding,
            WorldPoint2d candidate) {
        int centreX = (int) Math.floor(candidate.x());
        int centreZ = (int) Math.floor(candidate.z());
        int radius = parameters.measurementRadius();
        int step = parameters.sampleStep();
        int top = parameters.worldHeight() - 1;

        int satisfying = 0;
        int sampled = 0;
        Map<Integer, Integer> perLevel = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                for (int y = 0; y <= top; y += step) {
                    int x = centreX + dx;
                    int z = centreZ + dz;
                    if (!sample.isLoaded(x, y, z)) {
                        continue;
                    }
                    sampled++;
                    if (satisfies(sample, grounding, parameters, x, y, z)) {
                        satisfying++;
                        perLevel.merge(y, 1, Integer::sum);
                    }
                }
            }
        }

        if (satisfying == 0) {
            return Optional.empty();
        }
        int anchorY = modalLevel(perLevel);
        WorldPoint3d position = new WorldPoint3d(candidate.x(), anchorY, candidate.z());
        return Optional.of(new Anchor(grounding.elementId(), position, satisfying, sampled));
    }

    /**
     * Whether one point counts as the element being present.
     *
     * <p>Switches on the sealed hierarchy so a new grounding cannot be added without the compiler
     * demanding a rule for it — which is why the hierarchy is sealed.
     */
    private static boolean satisfies(
            TerrainSample sample,
            ElementGrounding grounding,
            AnchorParameters parameters,
            int x,
            int y,
            int z) {
        return switch (grounding) {
            case ElementGrounding.Physical physical ->
                sample.fluidAt(x, y, z).filter(physical.requiredFluids()::contains).isPresent();
            case ElementGrounding.Geometric geometric -> satisfiesGeometric(sample, geometric, parameters, x, y, z);
            case ElementGrounding.Weather weather -> satisfiesWeather(weather);
        };
    }

    /**
     * Both halves are required, as they were in the weighted scorer.
     *
     * <p>Sky light alone would qualify every open column in the world, and an environment match
     * alone is a mountain's interior rather than its ridge. {@code Occupancy} is the third
     * condition and the one that separates Earth from Wind.
     */
    private static boolean satisfiesGeometric(
            TerrainSample sample,
            ElementGrounding.Geometric geometric,
            AnchorParameters parameters,
            int x,
            int y,
            int z) {
        Optional<String> environment = sample.environmentAt(x, y, z);
        if (environment.isEmpty()
                || !ElementGrounding.environmentMatches(environment.get(), geometric.environments())) {
            return false;
        }
        int sky = sample.skyLightAt(x, y, z);
        if (sky < geometric.minSkyLight() || sky > geometric.maxSkyLight()) {
            return false;
        }
        return occupied(sample, geometric.occupancy(), parameters, x, y, z);
    }

    /**
     * Whether the point satisfies the grounding's occupancy requirement.
     *
     * <p>{@code OPEN_ABOVE_SOLID} looks one sample step down rather than one block: at the sampling
     * resolution that is the adjacent sample the scan already visits, so the surface test is free.
     * Looking one <em>block</em> down would read a position the scan never samples and would miss a
     * surface whenever the step exceeds 1.
     */
    private static boolean occupied(
            TerrainSample sample,
            ElementGrounding.Occupancy occupancy,
            AnchorParameters parameters,
            int x,
            int y,
            int z) {
        boolean solid = sample.isSolidAt(x, y, z);
        if (occupancy == ElementGrounding.Occupancy.SOLID) {
            return solid;
        }
        if (solid) {
            return false;
        }
        int below = y - parameters.sampleStep();
        return below >= 0 && sample.isLoaded(x, below, z) && sample.isSolidAt(x, below, z);
    }

    /**
     * Not scored here. Lightning gets its own treatment, which is not designed yet.
     *
     * <p>Throwing rather than returning a count or an empty result, deliberately. Under volume
     * counting the existing storm-plus-sky rule would count all exposed air in a storm region,
     * which carries the same valley-above-summit inversion {@code OPEN_ABOVE_SOLID} exists to fix —
     * so any number produced here would be known-wrong. An empty result would be worse: it reads as
     * "no qualifying terrain", which is the silent-zero failure this port's {@code isLoaded} exists
     * to prevent, one level up.
     *
     * <p>Callers that iterate every grounding must skip {@code Weather} explicitly. That is the
     * intent — a skip someone wrote is visible in a diff, and a default is not.
     */
    private static boolean satisfiesWeather(ElementGrounding.Weather weather) {
        throw new UnsupportedOperationException("Lightning (" + weather.elementId()
                + ") awaits its own treatment; volume counting would score exposed air and invert "
                + "valley against summit. Skip Weather groundings rather than scoring them.");
    }

    /** The level with the most satisfying samples; the lowest such level wins a tie, for determinism. */
    private static int modalLevel(Map<Integer, Integer> perLevel) {
        int bestLevel = Integer.MAX_VALUE;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : perLevel.entrySet()) {
            int level = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount || (count == bestCount && level < bestLevel)) {
                bestCount = count;
                bestLevel = level;
            }
        }
        return bestLevel;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*VolumeStrengthTest' > /tmp/t.log 2>&1`
Then: `grep -E "tests completed|BUILD" /tmp/t.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Mutation-test the two load-bearing predicates**

This project's standing discipline: a guard or predicate that was green on first write proves nothing. For each mutation below, make the edit, run `:core:test`, confirm **RED**, then revert.

1. In `occupied`, drop the surface test — return `!solid` for `OPEN_ABOVE_SOLID`. Expected: `earthCountsRockAndWindCountsTheExposedSurface` fails with 27 against 9. This is the inversion that ranks a valley above a summit, so it is the single most important mutation here. If it passes, the volume/surface split is not actually tested.
2. In `modalLevel`, change the tie-break to `level > bestLevel`. Expected: a test fails only if a tie exists in the fixtures — **if nothing fails, add a fixture with two equally dense levels**, because the tie-break is otherwise unverified and determinism depends on it.
3. In `measure`, drop the `isLoaded` check. Expected: no current test fails — **add one** with a fake whose `isLoaded` returns false for part of the window, asserting `samplesTaken` excludes it. An unloaded point counted as a real negative is the exact failure `isLoaded` exists to prevent.

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add core/src/main/java/io/github/legendaryforge/legendary/core/internal/residue/VolumeStrength.java \
        core/src/test/java/io/github/legendaryforge/legendary/core/internal/residue/VolumeStrengthTest.java
git commit -m "feat(core): VolumeStrength — count the element over the full world column

Strength is volume per anchor-density §2, and the window is the whole 320-block
column so anchors can sit above or below ground. The anchor's y is the modal
sampled level, which resolves §4's ambiguity when a column holds two bodies."
```

---

### Task 5: `AnchorSelector`

**Files:**
- Create: `core/src/main/java/io/github/legendaryforge/legendary/core/internal/residue/AnchorSelector.java`
- Test: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/residue/AnchorSelectorTest.java`

**Interfaces:**
- Consumes: `Anchor`, `AnchorParameters`, `ResidueRandom`, `WorldPoint3d`.
- Produces: `public static List<Anchor> select(long worldSeed, AnchorParameters parameters, List<Anchor> measured)`. Input is every measured candidate **for one element**, including candidates outside the region of interest but within one suppression radius of it. Output is the subset that cleared the floor and won their neighbourhood, in input order.

Selection needs no terrain, which is why it is a separate file: these tests construct `Anchor` records directly.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint3d;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Selection is geometry over already-measured candidates, so these tests build no world. The two
 * properties that matter: abundance must not change how many anchors exist, and the floor must make
 * a rare element simply absent rather than weakly present.
 */
class AnchorSelectorTest {

    private static final AnchorParameters P = AnchorParameters.defaults();
    private static final long SEED = 0x5EEDL;

    private static Anchor at(double x, double z, int strength) {
        return new Anchor("water", new WorldPoint3d(x, 64, z), strength, 1000);
    }

    @Test
    void theStrongestInANeighbourhoodWins() {
        List<Anchor> selected = AnchorSelector.select(
                SEED, P, List.of(at(0, 0, 100), at(100, 0, 500), at(200, 0, 300)));
        assertEquals(1, selected.size());
        assertEquals(500, selected.get(0).strength());
    }

    @Test
    void candidatesBeyondTheSuppressionRadiusDoNotCompete() {
        // 600 blocks apart, suppression radius 512: both survive.
        List<Anchor> selected = AnchorSelector.select(SEED, P, List.of(at(0, 0, 100), at(600, 0, 500)));
        assertEquals(2, selected.size());
    }

    @Test
    void theFloorRemovesTraceAmounts() {
        // minimumStrengthFraction 0.01 of 1000 samples = 10. Nine is below the floor.
        assertTrue(AnchorSelector.select(SEED, P, List.of(at(0, 0, 9))).isEmpty());
        assertEquals(1, AnchorSelector.select(SEED, P, List.of(at(0, 0, 10))).size());
    }

    @Test
    void tiesAreBrokenByHashNotByOrder() {
        List<Anchor> forward = AnchorSelector.select(SEED, P, List.of(at(0, 0, 500), at(100, 0, 500)));
        List<Anchor> reversed = AnchorSelector.select(SEED, P, List.of(at(100, 0, 500), at(0, 0, 500)));
        assertEquals(1, forward.size());
        assertEquals(1, reversed.size());
        assertEquals(
                forward.get(0).position(),
                reversed.get(0).position(),
                "a tie must resolve to the same winner regardless of input order");
    }

    @Test
    void abundanceDoesNotChangeHowManyAnchorsExist() {
        // Nine tied candidates in one neighbourhood — a uniform ocean. Exactly one survives.
        List<Anchor> uniform = List.of(
                at(0, 0, 900), at(100, 0, 900), at(200, 0, 900),
                at(0, 100, 900), at(100, 100, 900), at(200, 100, 900),
                at(0, 200, 900), at(100, 200, 900), at(200, 200, 900));
        assertEquals(1, AnchorSelector.select(SEED, P, uniform).size());
    }

    @Test
    void suppressionIsHorizontalSoElevationCannotSeparateTwoAnchors() {
        Anchor deep = new Anchor("water", new WorldPoint3d(0, 10, 0), 100, 1000);
        Anchor high = new Anchor("water", new WorldPoint3d(0, 300, 0), 500, 1000);
        List<Anchor> selected = AnchorSelector.select(SEED, P, List.of(deep, high));
        assertEquals(1, selected.size());
        assertEquals(500, selected.get(0).strength());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*AnchorSelectorTest' > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: `cannot find symbol: class AnchorSelector`.

- [ ] **Step 3: Write the implementation**

```java
package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import java.util.ArrayList;
import java.util.List;

/**
 * Which measured candidates become anchors — anchor-density design §3.
 *
 * <p>Two rules, and the per-element behaviour the operator described falls out of them rather than
 * being configured:
 *
 * <ul>
 *   <li><strong>An absolute floor.</strong> A candidate below {@code minimumStrengthFraction} of its
 *       own sample count is not an anchor at any strength ranking. This is how rarity handles
 *       itself: a region with no lava yields no Fire anchor, and nothing is tuned to say so.
 *   <li><strong>A uniform-radius local winner.</strong> A candidate is an anchor when nothing within
 *       one suppression radius beats it on {@code (strength, hash)}. This is how overabundance
 *       handles itself: in a uniform ocean every strength ties, the hash decides, and the result is
 *       one anchor per radius — the same count an equal area of varied coastline yields.
 * </ul>
 *
 * <p><strong>The hash tie-break is not a detail.</strong> Without it a tie means nothing is strictly
 * stronger, so every point in the ocean qualifies and the plateau defect returns.
 *
 * <p><strong>Callers must supply a margin.</strong> A candidate within one suppression radius of the
 * region of interest can suppress a candidate inside it, so a caller selecting over a region must
 * pass measurements from the region expanded by {@code suppressionRadius}. Omitting the margin mints
 * spurious anchors along every edge.
 */
public final class AnchorSelector {

    private AnchorSelector() {}

    /** The subset of {@code measured} that clears the floor and wins its neighbourhood, in input order. */
    public static List<Anchor> select(long worldSeed, AnchorParameters parameters, List<Anchor> measured) {
        List<Anchor> eligible = new ArrayList<>();
        for (Anchor anchor : measured) {
            if (anchor.strengthFraction() >= parameters.minimumStrengthFraction()) {
                eligible.add(anchor);
            }
        }

        double radius = parameters.suppressionRadius();
        List<Anchor> selected = new ArrayList<>();
        for (Anchor anchor : eligible) {
            boolean beaten = false;
            for (Anchor rival : eligible) {
                if (rival == anchor) {
                    continue;
                }
                if (anchor.position().horizontalDistanceTo(rival.position()) > radius) {
                    continue;
                }
                if (beats(worldSeed, rival, anchor)) {
                    beaten = true;
                    break;
                }
            }
            if (!beaten) {
                selected.add(anchor);
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Whether {@code rival} outranks {@code anchor} on {@code (strength, hash)}.
     *
     * <p>The hash is taken over the rounded position so it is a property of the place rather than of
     * the double's exact bits, which keeps the comparison stable if a candidate's coordinates are
     * ever recomputed at a different precision.
     */
    private static boolean beats(long worldSeed, Anchor rival, Anchor anchor) {
        if (rival.strength() != anchor.strength()) {
            return rival.strength() > anchor.strength();
        }
        long rivalHash = positionHash(worldSeed, rival);
        long anchorHash = positionHash(worldSeed, anchor);
        if (rivalHash != anchorHash) {
            return rivalHash > anchorHash;
        }
        // Astronomically unlikely, but a total order must be total: fall back to coordinates so the
        // outcome cannot depend on iteration order.
        if (rival.position().x() != anchor.position().x()) {
            return rival.position().x() > anchor.position().x();
        }
        return rival.position().z() > anchor.position().z();
    }

    private static long positionHash(long worldSeed, Anchor anchor) {
        long x = Math.round(anchor.position().x());
        long z = Math.round(anchor.position().z());
        return ResidueRandom.mix(worldSeed, x, z);
    }
}
```

`ResidueRandom` is package-private and `AnchorSelector` is in the same package, so `mix` is reachable without widening its visibility.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :core:test --tests '*AnchorSelectorTest' > /tmp/t.log 2>&1`
Then: `grep -E "tests completed|BUILD" /tmp/t.log`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Mutation-test the selection rules**

For each, make the edit, run `:core:test`, confirm **RED**, revert.

1. Delete the floor filter (make `eligible` a copy of `measured`). Expected: `theFloorRemovesTraceAmounts` fails.
2. Change `> radius` to `>= radius` in the distance skip. Expected: a test fails only if a fixture sits at exactly 512 — **if nothing fails, add a pair exactly `suppressionRadius` apart**, since the boundary is where an off-by-one lives.
3. Remove the hash comparison from `beats`, leaving only the strength comparison. Expected: `abundanceDoesNotChangeHowManyAnchorsExist` fails, because nine tied candidates all survive. This is the plateau defect, and this mutation is the one that proves the test catches it.
4. Change `horizontalDistanceTo` to `distanceTo`. Expected: `suppressionIsHorizontalSoElevationCannotSeparateTwoAnchors` fails.

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add core/src/main/java/io/github/legendaryforge/legendary/core/internal/residue/AnchorSelector.java \
        core/src/test/java/io/github/legendaryforge/legendary/core/internal/residue/AnchorSelectorTest.java
git commit -m "feat(core): AnchorSelector — floor plus uniform-radius local winner

Rarity and overabundance both handle themselves: the floor makes a region with
none of an element yield no anchor, and the (strength, hash) tie-break makes a
uniform ocean yield the same count as equal-area varied coastline."
```

---

### Task 6: Adapter — `isSolidAt`, measured before implemented

**Files:**
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/residue/SectionReaderTerrainSample.java`
- Create: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/command/BlockIdProbeCommand.java`
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/LegendaryHytalePlugin.java`
- Modify: `docs/integration/hytale-engine-api.md`

**Interfaces:**
- Consumes: `TerrainSample.isSolidAt` from Task 3.
- Produces: a working `isSolidAt` in the adapter, and a new row in the engine API reference recording how air is identified.

**This task starts with a measurement, and the implementation is blocked on its result.** The engine's `SectionReader.getBlock(x, y, z)` returns an `int` whose id→name resolution is **unverified** — `docs/integration/hytale-engine-api.md` lists the accessor with no notes, and records a namespace trap where a block-type list and an asset map share names but not ids. Guessing which id means air inverts Earth and Wind simultaneously, and both would still compile and still produce plausible-looking anchors.

- [ ] **Step 1: Write the probe command**

```java
package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/**
 * Debug command: {@code /blockidprobe}
 *
 * <p>Prints {@code getBlock} at a vertical sweep through the player's column, so the id that means
 * air can be read off ground truth rather than assumed. Earth counts solid points and Wind counts
 * open ones, so a wrong air id inverts both at once while still compiling and still producing
 * plausible anchors — which is why this runs before {@code isSolidAt} is written.
 *
 * <p>Anchored on a connected player because a headless server holds no loaded region at any offset
 * from 0 to 4096 (measured 2026-10-21), so every read would be a cheap miss.
 *
 * <p>Prints {@code BLOCKID_VERDICT:} as its last line.
 */
public class BlockIdProbeCommand extends AbstractWorldCommand {

    public BlockIdProbeCommand() {
        super("blockidprobe", "Prints getBlock through the player's column, to identify the air block id");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- block id probe ---\n");
        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players connected; a headless server loads no region.\n")
                    .append("BLOCKID_VERDICT: INCONCLUSIVE | no players\n");
            send(context, sb.toString());
            return;
        }

        SectionReader reader = new SectionReader(world.getChunkStore());
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            Vector3d pos = transform.getPosition();
            int x = MathUtil.floor(pos.x);
            int z = MathUtil.floor(pos.z);
            int feet = MathUtil.floor(pos.y);
            sb.append("player ").append(playerRef.getUsername()).append(" at ")
                    .append(x).append(',').append(feet).append(',').append(z).append('\n');
            sb.append("  y   getBlock  getFluidId  skyLight\n");
            for (int y = 0; y <= 319; y += 16) {
                sb.append(String.format(
                        "  %3d %9d %11d %9d%n",
                        y, reader.getBlock(x, y, z), reader.getFluidId(x, y, z), reader.getSkyLight(x, y, z)));
            }
            // Three readings whose answers are known from standing position: well above the player
            // is air, the player's own feet level is air, and well below is ground.
            sb.append("  expected: y=319 air, y=").append(feet).append(" air, y=8 solid\n");
            sb.append("BLOCKID_VERDICT: READ | compare the three expected rows\n");
            send(context, sb.toString());
            return;
        }
        sb.append("no player had a TransformComponent; cannot anchor\n")
                .append("BLOCKID_VERDICT: INCONCLUSIVE | no transform\n");
        send(context, sb.toString());
    }
}
```

- [ ] **Step 2: Register it and verify the command guard passes**

In `LegendaryHytalePlugin.java`, add `registerCommand(new BlockIdProbeCommand());` alongside the existing registrations, and add `blockidprobe` to the boot-log command list. `checkCommandRegistration` asserts the boot log names exactly the commands registered and that no two share a name — it will fail if either half is missed.

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :mod:hytale:checkCommandRegistration > /tmp/t.log 2>&1`
Then: `grep -E "COMMANDS_VERDICT|BUILD" /tmp/t.log`
Expected: `COMMANDS_VERDICT: GREEN` with the count up by one.

- [ ] **Step 3: Run the probe on a live server and record the result**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
bash scripts/live-probes/server-up.sh
```

Confirm `HOLDER alive` appears. Connect a client, stand on solid ground in open daylight, then from the console: `/blockidprobe`. Read the output in `live-session.log`.

Record in `docs/integration/hytale-engine-api.md`'s accessor table: the `getBlock` row gains the measured air id, the build stamp, and tier **measured**. If `getBlock` returns something that does not separate air from stone, **stop and report** — do not proceed to Step 4 with a guess.

- [ ] **Step 4: Implement `isSolidAt` against the measured id**

Replace the `UnsupportedOperationException` stub. `AIR_BLOCK_ID` is the value measured in Step 3 — substitute it; do not leave the placeholder name.

```java
    /**
     * The block id that means "nothing here", measured via {@code /blockidprobe} on 0.7.3 — see the
     * {@code getBlock} row in {@code docs/integration/hytale-engine-api.md}.
     *
     * <p>Measured rather than assumed because Earth counts solid points and Wind counts open ones,
     * so a wrong value inverts both elements at once and produces anchors that look plausible.
     */
    private static final int AIR_BLOCK_ID = /* measured in Step 3 */ 0;

    @Override
    public boolean isSolidAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return false;
        }
        return reader.getBlock(x, y, z) != AIR_BLOCK_ID;
    }
```

Use whatever field name the class already holds its `SectionReader` in — read the file rather than assuming `reader`.

- [ ] **Step 5: Verify the module compiles against the real jar**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :mod:hytale:compileJava > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: `BUILD SUCCESSFUL`.

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && python3 scripts/coverage-census.py > /tmp/c.log 2>&1`
Then: `grep COVERAGE_VERDICT /tmp/c.log`
Expected: `0 exempt` — proving the module compiled against the jar rather than being skipped.

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add -A mod/hytale/src docs/integration/hytale-engine-api.md
git commit -m "feat(mod): isSolidAt, with the air block id measured not assumed

getBlock's id resolution was unverified and a wrong air id inverts Earth and
Wind at once while still compiling. /blockidprobe reads the column against
three positions whose answers are known from standing there."
```

---

### Task 7: `/residueanchors` probe, and amend the sizing decision

**Files:**
- Modify: `core/src/main/java/io/github/legendaryforge/legendary/core/internal/residue/CandidateLattice.java`
- Modify: `core/src/test/java/io/github/legendaryforge/legendary/core/internal/residue/CandidateLatticeTest.java`
- Create: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/command/ResidueAnchorsCommand.java`
- Modify: `mod/hytale/src/main/java/io/github/legendaryforge/legendary/mod/hytale/LegendaryHytalePlugin.java`
- Modify: `docs/superpowers/specs/2026-10-22-residue-sizing-decision.md`

**Interfaces:**
- Consumes: `VolumeStrength.measure` (Task 4), `AnchorSelector.select` (Task 5), `AnchorParameters.defaults()` (Task 2), `Anchor` (Task 1), and the existing `HytaleDerivationRules.cosmologicalFive()` — note the name, it is **not** `groundings()`.
- Produces: `CandidateLattice.candidatesWithin(long worldSeed, int cellSize, double jitterFraction, double minX, double minZ, double maxX, double maxZ)` and the matching `candidateInCell(long, int, double, long, long)` overload; `/residueanchors`, printing `ANCHORS_VERDICT:`.

The command imports `core.internal.residue` directly. That is this project's established pattern rather than a leak — `LegendaryHytalePlugin` imports three `core.internal.weather` classes, and `ResidueSiteProbeCommand` already imports `CandidateLattice` and `SiteScorer` the same way. No public facade is wanted; adding one would be a new convention for one caller.

The probe enumerates candidates over the region **expanded by `suppressionRadius`**, selects over all of them, then reports only anchors inside the unexpanded region. Reporting the margin's anchors overstates density; omitting the margin from selection mints spurious anchors on every edge.

- [ ] **Step 1: Add the `CandidateLattice` overload**

`candidatesWithin` currently takes a `LatticeParameters`, and this path has an `AnchorParameters`. Rather than make either record depend on the other, take the two values the lattice actually uses.

Write the failing test first, appending to `CandidateLatticeTest.java`:

```java
    @Test
    void theValueOverloadAgreesWithTheParametersOverload() {
        LatticeParameters p = LatticeParameters.defaults();
        List<WorldPoint2d> viaRecord = CandidateLattice.candidatesWithin(0x5EEDL, p, 0, 0, 1000, 1000);
        List<WorldPoint2d> viaValues = CandidateLattice.candidatesWithin(
                0x5EEDL, p.cellSize(), p.jitterFraction(), 0, 0, 1000, 1000);
        assertEquals(viaRecord, viaValues);
        assertFalse(viaValues.isEmpty(), "a 1000x1000 region must contain candidates at cellSize 256");
    }
```

Run: `./gradlew :core:test --tests '*CandidateLatticeTest' > /tmp/t.log 2>&1`
Then: `grep -E "error:|BUILD" /tmp/t.log`
Expected: failure — no such method.

Now change `CandidateLattice` so the existing methods delegate to value-taking overloads. Replace the body of `candidatesWithin` and `candidateInCell` with:

```java
    public static List<WorldPoint2d> candidatesWithin(
            long worldSeed, LatticeParameters parameters, double minX, double minZ, double maxX, double maxZ) {
        return candidatesWithin(
                worldSeed, parameters.cellSize(), parameters.jitterFraction(), minX, minZ, maxX, maxZ);
    }

    /**
     * Every candidate inside the bounds, taking the two dials the lattice actually uses.
     *
     * <p>Exists because the lattice is shared by two parameter records — {@code LatticeParameters}
     * for the superseded weighted scorer and {@code AnchorParameters} for the volume model — and
     * neither should have to depend on the other to propose a position.
     */
    public static List<WorldPoint2d> candidatesWithin(
            long worldSeed,
            int cellSize,
            double jitterFraction,
            double minX,
            double minZ,
            double maxX,
            double maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("empty bounds: x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ);
        }
        long firstCellX = Math.floorDiv((long) Math.floor(minX), cellSize) - 1;
        long lastCellX = Math.floorDiv((long) Math.floor(maxX), cellSize) + 1;
        long firstCellZ = Math.floorDiv((long) Math.floor(minZ), cellSize) - 1;
        long lastCellZ = Math.floorDiv((long) Math.floor(maxZ), cellSize) + 1;

        List<WorldPoint2d> out = new ArrayList<>();
        for (long cx = firstCellX; cx <= lastCellX; cx++) {
            for (long cz = firstCellZ; cz <= lastCellZ; cz++) {
                WorldPoint2d candidate = candidateInCell(worldSeed, cellSize, jitterFraction, cx, cz);
                if (candidate.x() >= minX && candidate.x() <= maxX && candidate.z() >= minZ && candidate.z() <= maxZ) {
                    out.add(candidate);
                }
            }
        }
        return out;
    }

    public static WorldPoint2d candidateInCell(
            long worldSeed, LatticeParameters parameters, long cellX, long cellZ) {
        return candidateInCell(worldSeed, parameters.cellSize(), parameters.jitterFraction(), cellX, cellZ);
    }

    /** The one candidate belonging to a cell, taking values rather than a parameter record. */
    public static WorldPoint2d candidateInCell(
            long worldSeed, int cellSize, double jitterFraction, long cellX, long cellZ) {
        double centreX = cellX * (double) cellSize + cellSize / 2.0;
        double centreZ = cellZ * (double) cellSize + cellSize / 2.0;
        double reach = jitterFraction * cellSize;
        double jitterX = ResidueRandom.signed(worldSeed, cellX, cellZ) * reach;
        double jitterZ = ResidueRandom.signed(worldSeed ^ 0x5DEECE66DL, cellZ, cellX) * reach;
        return new WorldPoint2d(centreX + jitterX, centreZ + jitterZ);
    }
```

Keep both existing javadoc blocks on the `LatticeParameters`-taking methods — they document the extent guarantee and the jitter-axis reasoning, and that content is not duplicated below.

Run the test again; expected `BUILD SUCCESSFUL`. The existing `CandidateLatticeTest` cases must all still pass — they are the regression check that delegation changed nothing.

- [ ] **Step 2: Write the command**

```java
package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.AnchorSelector;
import io.github.legendaryforge.legendary.core.internal.residue.CandidateLattice;
import io.github.legendaryforge.legendary.core.internal.residue.VolumeStrength;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3d;

/**
 * Debug command: {@code /residueanchors}
 *
 * <p>Reports how many anchors each element gets around the player and how far apart they are. This
 * is the one number the sizing decision cannot settle from a desk: whether crystal density lands
 * between "travel 500 blocks to find any" and "the landscape is riddled with them".
 *
 * <p><strong>Selection runs over a margin and reports without it.</strong> A candidate within one
 * suppression radius of the reported region can suppress a candidate inside it, so candidates are
 * enumerated over the region expanded by {@code suppressionRadius} and filtered afterwards.
 * Skipping either half biases density in a different direction.
 *
 * <p><strong>Spacing is reported, not just a count.</strong> A count over a region answers "how
 * many"; the question is "how far does a player walk", which only nearest-neighbour distance
 * answers.
 *
 * <p>Anchored on a connected player because a headless server holds no loaded region at any offset
 * from 0 to 4096 (measured 2026-10-21), so every column would read unloaded and every element would
 * report zero for the wrong reason.
 *
 * <p>Prints {@code ANCHORS_VERDICT:} as its last line.
 */
public class ResidueAnchorsCommand extends AbstractWorldCommand {

    /** Region reported around the player, per axis. Small on purpose: each candidate is a full-column scan. */
    private static final int REGION_RADIUS = 512;

    /** Stand-in until a world-seed accessor is confirmed; the probe's figures are seed-relative. */
    private static final long WORLD_SEED = 0x5EEDL;

    public ResidueAnchorsCommand() {
        super("residueanchors", "Reports per-element anchor count and spacing around the player");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- residue anchors ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players connected. A headless server loads no region, so every column\n")
                    .append("would read unloaded and every element would report zero for the wrong\n")
                    .append("reason. Connect a client.\n")
                    .append("ANCHORS_VERDICT: INCONCLUSIVE | no players\n");
            send(context, sb.toString());
            return;
        }

        String who = null;
        int playerX = 0;
        int playerY = 0;
        int playerZ = 0;
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                sb.append("[").append(playerRef.getUsername()).append("] TransformComponent ABSENT\n");
                continue;
            }
            Vector3d pos = transform.getPosition();
            who = playerRef.getUsername();
            playerX = MathUtil.floor(pos.x);
            playerY = MathUtil.floor(pos.y);
            playerZ = MathUtil.floor(pos.z);
            break;
        }
        if (who == null) {
            sb.append("no player had a TransformComponent; cannot anchor\n")
                    .append("ANCHORS_VERDICT: INCONCLUSIVE | no transform\n");
            send(context, sb.toString());
            return;
        }

        AnchorParameters parameters = AnchorParameters.defaults();
        // referencePlaneY remains a v1 limitation of the adapter's surfaceHeightAt. VolumeStrength
        // never calls that method -- it walks the whole column -- so this value only reaches
        // isLoaded, and no candidate's score depends on where the player is standing.
        SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);

        int margin = parameters.suppressionRadius();
        double minX = playerX - REGION_RADIUS;
        double maxX = playerX + REGION_RADIUS;
        double minZ = playerZ - REGION_RADIUS;
        double maxZ = playerZ + REGION_RADIUS;

        List<WorldPoint2d> candidates = CandidateLattice.candidatesWithin(
                WORLD_SEED,
                parameters.cellSize(),
                parameters.jitterFraction(),
                minX - margin,
                minZ - margin,
                maxX + margin,
                maxZ + margin);

        sb.append("anchor: ").append(who).append(" at ").append(playerX).append(',')
                .append(playerY).append(',').append(playerZ).append('\n');
        sb.append("region: +/-").append(REGION_RADIUS).append("b, margin ").append(margin)
                .append("b, candidates ").append(candidates.size()).append('\n');
        sb.append("element     anchors  mean gap  min gap  strongest  loaded\n");

        int totalAnchors = 0;
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            // Lightning awaits its own treatment. Skipped explicitly rather than defaulted, and
            // printed rather than omitted, so the element stays visible in the output.
            if (grounding instanceof ElementGrounding.Weather) {
                sb.append(String.format(
                        "%-11s %7s %9s %8s %10s %7s%n",
                        grounding.elementId(), "-", "-", "-", "pending", "-"));
                continue;
            }
            List<Anchor> measured = new ArrayList<>();
            long sampled = 0;
            for (WorldPoint2d candidate : candidates) {
                Optional<Anchor> anchor = VolumeStrength.measure(sample, parameters, grounding, candidate);
                if (anchor.isPresent()) {
                    measured.add(anchor.get());
                    sampled += anchor.get().samplesTaken();
                }
            }
            List<Anchor> selected = AnchorSelector.select(WORLD_SEED, parameters, measured);
            List<Anchor> inRegion = new ArrayList<>();
            for (Anchor anchor : selected) {
                if (anchor.position().x() >= minX
                        && anchor.position().x() <= maxX
                        && anchor.position().z() >= minZ
                        && anchor.position().z() <= maxZ) {
                    inRegion.add(anchor);
                }
            }
            totalAnchors += inRegion.size();
            sb.append(String.format(
                    "%-11s %7d %9s %8s %10s %7d%n",
                    grounding.elementId(),
                    inRegion.size(),
                    gap(inRegion, false),
                    gap(inRegion, true),
                    inRegion.isEmpty() ? "-" : Integer.toString(strongest(inRegion)),
                    sampled));
        }

        sb.append("\nREADING THIS. Zero anchors means no qualifying terrain in range OR nothing\n")
                .append("loaded, and those are different answers -- the loaded column is how you tell\n")
                .append("them apart. A zero with a large loaded count is a real absence.\n");
        sb.append("ANCHORS_VERDICT: ")
                .append(totalAnchors > 0 ? "READ" : "EMPTY")
                .append(" | ")
                .append(totalAnchors)
                .append(" anchor(s) across 4 element(s); lightning pending\n");
        send(context, sb.toString());
    }

    /** Mean or minimum nearest-neighbour horizontal distance, or {@code "-"} below two anchors. */
    private static String gap(List<Anchor> anchors, boolean minimum) {
        if (anchors.size() < 2) {
            return "-";
        }
        double total = 0;
        double smallest = Double.MAX_VALUE;
        for (Anchor anchor : anchors) {
            double nearest = Double.MAX_VALUE;
            for (Anchor other : anchors) {
                if (other == anchor) {
                    continue;
                }
                nearest = Math.min(nearest, anchor.position().horizontalDistanceTo(other.position()));
            }
            total += nearest;
            smallest = Math.min(smallest, nearest);
        }
        return String.format("%.0fb", minimum ? smallest : total / anchors.size());
    }

    private static int strongest(List<Anchor> anchors) {
        int best = 0;
        for (Anchor anchor : anchors) {
            best = Math.max(best, anchor.strength());
        }
        return best;
    }
}
```

The `loaded` column is deliberate and was learned from `/fluidscancost`: a zero that cannot be distinguished from "nothing was there to read" is the failure mode this whole port's `isLoaded` exists to prevent, and the old probe output could not make that distinction.

- [ ] **Step 3: Register the command and verify the guard**

In `LegendaryHytalePlugin.java`, add `registerCommand(new ResidueAnchorsCommand());` beside the existing registrations **and** add `residueanchors` to the boot-log command list. `checkCommandRegistration` asserts no two commands share a name and that the boot log names exactly what was registered — it fails if either half is missed, and both halves had in fact failed before that guard existed.

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew :mod:hytale:checkCommandRegistration > /tmp/t.log 2>&1`
Then: `grep -E "COMMANDS_VERDICT|BUILD" /tmp/t.log`
Expected: `COMMANDS_VERDICT: GREEN` with the class and name counts both up by one from Task 6's reading.

- [ ] **Step 4: Amend the sizing decision document**

In `docs/superpowers/specs/2026-10-22-residue-sizing-decision.md`, under *"The per-element spread is a baseline, not five values"*, the four-row "Expected adjustment" table reads as a plan to hand-tune five radii. Replace it with the mechanism now implemented, and keep the correction that follows it:

```markdown
**Amended 2026-10-22.** This section carried a table reading *Earth and Wind wider, Fire narrower,
Lightning narrower if the empty case bites* — a plan to hand-tune five radii. The operator's position
is that the per-element differences should emerge from rarity, abundance and topology rather than
from dials, and the implementation follows that instead:

- **The suppression radius stays uniform at 512 for every element.** Uniform spacing is what makes
  currents connectable and crystal density predictable, and it is the only dial that sets how far a
  player walks between crystals.
- **Rarity is handled by `minimumStrengthFraction`.** A region whose best candidate holds only a
  trace of an element gets no anchor for it, so Fire's network comes out sparse and regional because
  lava is — with nothing tuned to say so.
- **Overabundance is handled by the `(strength, hash)` tie-break.** In uniform ocean, deep rock or
  open sky every strength ties and the hash decides, giving one anchor per radius — the same count an
  equal area of varied coastline yields.

The alternative not taken: per-element radii would compensate for the *extent of qualifying terrain*,
since a region with none gets no anchor at all. That reading of §3 is still correct, and remains the
fallback if the uniform radius cannot satisfy both Earth and Fire — which is what §10 names as the
first thing to try before abandoning volume as a measure.
```

- [ ] **Step 5: Run the whole gate**

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew check > /tmp/t.log 2>&1`
Then: `grep -E "DOCS_VERDICT|COMMANDS_VERDICT|BUILD SUCCESSFUL|BUILD FAILED" /tmp/t.log`

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && ./gradlew test --rerun-tasks > /tmp/r.log 2>&1`
Then: `cd /home/stephaneb/Workspace/Projects/Hytale && python3 scripts/test-census.py > /tmp/c.log 2>&1`
Then: `grep CENSUS_VERDICT /tmp/c.log`

Run: `cd /home/stephaneb/Workspace/Projects/Hytale && python3 scripts/coverage-census.py > /tmp/v.log 2>&1`
Then: `grep COVERAGE_VERDICT /tmp/v.log`
Expected: `0 exempt`, proving `mod/hytale` compiled against the real jar rather than being skipped.

`--rerun-tasks` is required rather than optional: `build` passes in about a second on cached tasks while the census reads results that may be a day old, which has already produced a green reading that was no evidence about the code beneath it.

- [ ] **Step 6: Commit**

```bash
cd /home/stephaneb/Workspace/Projects/Hytale
git add -A core/src mod/hytale/src docs/superpowers/specs/2026-10-22-residue-sizing-decision.md
git commit -m "feat(mod): /residueanchors reports per-element anchor count and spacing

The number the sizing decision cannot settle from a desk: whether crystal
density lands between 'travel 500 blocks' and 'landscape riddled'. Selection
runs over a suppression-radius margin and reports without it, because each
half alone biases density in a different direction. The loaded column makes a
zero legible -- no qualifying terrain and nothing loaded are different answers.

Amends the sizing decision's per-element table into the floor-and-tie-break
mechanism that replaced it."
```

---

## What this plan does not build, and why

- **Currents, crystals and geodes.** Anchor spacing gates all three: an MST over anchors that are too sparse or too dense produces currents of the wrong length, and crystal coverage is `2w/R` off that spacing. Building pathing on unvalidated spacing risks discarding it. `influenceRadius` and `crossingTolerance` are carried in `AnchorParameters` now so the record does not change shape later, and are unused until then.
- **Lightning's measure.** Its own treatment, not yet designed, and not to be filled in by analogy with Wind — it is the element Stormseeker depends on, so it earns a design pass rather than a borrowed predicate. `VolumeStrength` throws for `Weather` so no caller can silently obtain a number.
- **Per-element measurement window heights.** Counting qualifying open air up a full column may score a low ridge above a summit, which would invert Wind against §2. Whether it does depends on whether the environment at altitude still reads as `Mountains` — unmeasured, and `/residueanchors` reporting Wind's strongest-anchor strength is what will show it.
- **Removing `SiteScorer` and `LatticeParameters`.** Both still serve `/residueprobe`, which reads a single candidate usefully. They are superseded by this path, not dead; deleting them is a separate change once currents land.
- **Correcting §4's Earth y-source wording in the spec.** §4 sources Earth's y from "the cave volume's own level" while §2 rejects cave-as-Earth as inverted. That is **settled, not open**: an anchor is a spot with a lot of the element, so an Earth anchor sits where the rock is, and `VolumeStrength`'s modal level of solid points puts it there. §4's phrase is stale wording from before §2's rejection and should be deleted from the spec — a documentation edit this plan does not make, since it touches a PROPOSED spec rather than the code.

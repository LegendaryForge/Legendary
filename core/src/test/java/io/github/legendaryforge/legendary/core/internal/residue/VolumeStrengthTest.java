package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ColumnMeasurement;
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
 * Volume counting, tested against a fake sample. A lake must out-score a puddle, the anchor's
 * elevation must land in a body rather than between two of them, and earth and air must come out as
 * exact complements — rock against not-rock-and-not-water, partitioning the window between them.
 */
class VolumeStrengthTest {

    /** Small radius and coarse step keep the fakes legible: 3x3 columns, 4 sampled levels. */
    private static final AnchorParameters P = new AnchorParameters(256, 0.35, 512, 80, 80, 0.01, 8, 8);

    private static final ElementGrounding WATER =
            new ElementGrounding.Physical("water", Set.of("Water_Source"), Set.of("Shores"));
    private static final ElementGrounding EARTH =
            new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, ElementGrounding.Occupancy.SOLID);
    private static final ElementGrounding AIR =
            new ElementGrounding.Geometric("wind", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.OPEN);
    private static final ElementGrounding LIGHTNING = new ElementGrounding.Weather("lightning", 0.10, 13, 15);

    private static final WorldPoint2d ORIGIN = new WorldPoint2d(0, 0);

    private static final class FakeTerrain implements TerrainSample {
        private final Map<String, String> fluids = new HashMap<>();
        private final Set<String> solids = new HashSet<>();
        private final Set<String> unloaded = new HashSet<>();
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

        FakeTerrain unload(int x, int y, int z) {
            unloaded.add(key(x, y, z));
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
            return !unloaded.contains(key(x, y, z));
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
            return OptionalDouble.of(0.5);
        }
    }

    @Test
    void aFullLevelOutScoresASinglePoint() {
        Anchor lake = VolumeStrength.measure(
                        new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source"), P, WATER, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        Anchor puddle = VolumeStrength.measure(
                        new FakeTerrain().fluidAtOnePoint(0, 80, 0, "Water_Source"), P, WATER, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(9, lake.strength());
        assertEquals(1, puddle.strength());
    }

    @Test
    void emptyWhenNothingSatisfiesThePredicate() {
        assertEquals(
                0, VolumeStrength.measure(new FakeTerrain(), P, WATER, ORIGIN).strength());
    }

    @Test
    void anchorElevationIsTheModalLevelNotTheMidpoint() {
        // A dense body at y=80 and one stray point at y=240. The anchor must sit at 80, not at 160.
        FakeTerrain twoBodies =
                new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source").fluidAtOnePoint(0, 240, 0, "Water_Source");
        Anchor anchor = VolumeStrength.measure(twoBodies, P, WATER, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(10, anchor.strength());
        assertEquals(80.0, anchor.position().y(), 1e-9);
    }

    @Test
    void modalLevelTieBreaksToTheLowerLevel() {
        // Two equally dense bodies. Determinism requires a stated winner, and it is the lower.
        FakeTerrain twoEqualBodies = new FakeTerrain()
                .fluidEverywhereAtLevel(80, "Water_Source")
                .fluidEverywhereAtLevel(240, "Water_Source");
        Anchor anchor = VolumeStrength.measure(twoEqualBodies, P, WATER, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(18, anchor.strength());
        assertEquals(80.0, anchor.position().y(), 1e-9);
    }

    @Test
    void samplesTakenIsTheWholeColumnWindow() {
        // x,z in {-80,0,80} = 3x3; y in {0,80,160,240} = 4 levels (320 is out of bounds).
        Anchor anchor = VolumeStrength.measure(
                        new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source"), P, WATER, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(36, anchor.samplesTaken());
    }

    @Test
    void aFullyLoadedWindowTakesExactlyWindowSampleCountSamples() {
        // Pins this scan's loop bounds to AnchorParameters.windowSampleCount(), which is derived
        // independently from the same two dials. A probe decides "was this window fully loaded" by
        // comparing samplesTaken against that figure, so the two disagreeing would not fail
        // anywhere -- it would quietly report every full window as partial, or the reverse.
        ColumnMeasurement full =
                VolumeStrength.measure(new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source"), P, WATER, ORIGIN);
        assertEquals(P.windowSampleCount(), full.samplesTaken());
        assertEquals(36, P.windowSampleCount(), "and the figure itself is the 3x3x4 window");
    }

    @Test
    void aPartlyUnloadedWindowFallsShortOfWindowSampleCount() {
        // The other half of the pin: the comparison must be able to come out false.
        ColumnMeasurement partial = VolumeStrength.measure(
                new FakeTerrain().fluidEverywhereAtLevel(80, "Water_Source").unload(0, 0, 0), P, WATER, ORIGIN);
        assertTrue(partial.samplesTaken() < P.windowSampleCount());
    }

    @Test
    void unloadedPointsAreExcludedFromTheDenominator() {
        // An unloaded point counted as a real negative is the exact failure isLoaded exists to
        // prevent. Three of the 36 window positions are unloaded here.
        FakeTerrain partlyUnloaded = new FakeTerrain()
                .fluidEverywhereAtLevel(80, "Water_Source")
                .unload(0, 0, 0)
                .unload(0, 160, 0)
                .unload(0, 240, 0);
        Anchor anchor = VolumeStrength.measure(partlyUnloaded, P, WATER, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(33, anchor.samplesTaken());
        assertEquals(9, anchor.strength());
    }

    @Test
    void earthCountsRockAndAirCountsOpenSpace() {
        FakeTerrain rock = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone1_Caves")
                .sky(0);
        Anchor earth =
                VolumeStrength.measure(rock, P, EARTH, ORIGIN).asCandidate(0, 0).orElseThrow();
        assertEquals(9, earth.strength(), "one solid level, 3x3 columns");

        // The same world read as air: the three OPEN levels count (0, 160, 240), the solid one does
        // not. Earth and air are exact complements here -- 9 against 27 of 36 samples.
        FakeTerrain sameWorld = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone1_Plains")
                .sky(15);
        Anchor air = VolumeStrength.measure(sameWorld, P, AIR, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(27, air.strength(), "three open levels, 3x3 columns");
        assertEquals(36, air.samplesTaken());
        assertEquals(36, earth.strength() + air.strength(), "earth and air partition the window");
    }

    @Test
    void fluidIsNotAir() {
        // getBlock reports 0 inside a fluid, so isSolidAt is false there and "not solid" alone would
        // count every submerged position as air. Flood every non-solid level: nothing counts.
        FakeTerrain flooded = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .fluidEverywhereAtLevel(0, "Water_Source")
                .fluidEverywhereAtLevel(160, "Water_Source")
                .fluidEverywhereAtLevel(240, "Water_Source")
                .environment("Env_Zone1_Plains")
                .sky(15);
        ColumnMeasurement air = VolumeStrength.measure(flooded, P, AIR, ORIGIN);
        assertEquals(0, air.strength(), "water is not air");
        assertTrue(air.anythingRead(), "and the window was fully read, so this is a real absence");

        // Control: the same world with the fluid removed does count those levels. Without it the
        // zero above could come from the apparatus rather than from the fluid rule.
        FakeTerrain dry = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone1_Plains")
                .sky(15);
        assertEquals(27, VolumeStrength.measure(dry, P, AIR, ORIGIN).strength(), "control: dry counts");
    }

    @Test
    void geometricRequiresBothEnvironmentAndSkyBand() {
        FakeTerrain wrongSky = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone1_Caves")
                .sky(15);
        assertEquals(0, VolumeStrength.measure(wrongSky, P, EARTH, ORIGIN).strength());

        FakeTerrain wrongEnvironment = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone1_Plains")
                .sky(0);
        assertEquals(
                0, VolumeStrength.measure(wrongEnvironment, P, EARTH, ORIGIN).strength());
    }

    @Test
    void wideGatesCountSolidPointsUnderAnyEnvironmentAndAnySky() {
        // The mechanism Earth relies on in production: gates widened to accept everything, so
        // occupancy alone decides and a mountain's bulk is not excluded for lacking a cave label.
        ElementGrounding rockIsRock =
                new ElementGrounding.Geometric("earth", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.SOLID);
        FakeTerrain mountain = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone1_Mountains")
                .sky(15);
        Anchor earth = VolumeStrength.measure(mountain, P, rockIsRock, ORIGIN)
                .asCandidate(0, 0)
                .orElseThrow();
        assertEquals(9, earth.strength(), "rock under a Mountains environment at full sky must still count");
    }

    @Test
    void anAbsentElementStillReportsHowMuchTerrainWasRead() {
        // The defect this type was introduced for, found by reading /residueanchors' own output:
        // Wind matched nothing and the probe reported sampled=0, which its printed guidance read as
        // "nothing loaded" — while Fire, Water and Earth each sampled ~1.2M at the same candidates.
        // A fully loaded window with no matching fluid must still report its full denominator.
        ColumnMeasurement absent = VolumeStrength.measure(new FakeTerrain(), P, WATER, ORIGIN);
        assertEquals(0, absent.strength(), "no water in this fake");
        assertEquals(36, absent.samplesTaken(), "but the whole window was read");
        assertTrue(absent.anythingRead(), "so absence is distinguishable from not having looked");
        assertEquals(-1, absent.modalLevel(), "and there is no level to report");
        assertTrue(absent.asCandidate(0, 0).isEmpty(), "not a candidate, but still a measurement");
    }

    @Test
    void anUnloadedWindowIsDistinguishableFromAnAbsentElement() {
        FakeTerrain nothingLoaded = new FakeTerrain();
        for (int x = -80; x <= 80; x += 80) {
            for (int z = -80; z <= 80; z += 80) {
                for (int y = 0; y <= 319; y += 80) {
                    nothingLoaded.unload(x, y, z);
                }
            }
        }
        ColumnMeasurement unread = VolumeStrength.measure(nothingLoaded, P, WATER, ORIGIN);
        assertEquals(0, unread.samplesTaken());
        assertFalse(unread.anythingRead(), "nothing was loaded, so nothing is known");
    }

    @Test
    void weatherGroundingsThrowBecauseLightningAwaitsItsOwnTreatment() {
        FakeTerrain stormy = new FakeTerrain()
                .solidEverywhereAtLevel(80)
                .environment("Env_Zone4_Crucible")
                .sky(15);
        UnsupportedOperationException thrown = assertThrows(
                UnsupportedOperationException.class, () -> VolumeStrength.measure(stormy, P, LIGHTNING, ORIGIN));
        assertTrue(thrown.getMessage().contains("lightning"), "the message must name the element");
    }
}

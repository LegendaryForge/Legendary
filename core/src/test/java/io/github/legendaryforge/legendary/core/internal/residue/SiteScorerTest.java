package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.SiteTyping;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Scoring is tested against a fake sample rather than the game, which is the point of the injected
 * port: the rules deciding whether a Fire nexus lands at the lava lake are checkable on any machine,
 * with no jar present.
 *
 * <p>The cases are organised by <strong>grounding</strong>, because the whole reason the hierarchy is
 * sealed is that the three kinds must score differently — and a single uniform rule got two of them
 * wrong in opposite directions.
 */
class SiteScorerTest {

    private static final LatticeParameters P = LatticeParameters.defaults();

    private static final ElementGrounding FIRE =
            new ElementGrounding.Physical("fire", Set.of("Fluid_Lava"), Set.of("Volcanoes", "Caves_Volcanic"));
    private static final ElementGrounding WATER =
            new ElementGrounding.Physical("water", Set.of("Fluid_Water"), Set.of("Shores", "Swamps"));
    private static final ElementGrounding WIND = new ElementGrounding.Geometric(
            "wind", Set.of("Mountains", "Plateaus"), 12, 15, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID);
    private static final ElementGrounding EARTH =
            new ElementGrounding.Geometric("earth", Set.of("Caves"), 0, 6, ElementGrounding.Occupancy.SOLID);
    private static final ElementGrounding LIGHTNING = new ElementGrounding.Weather("lightning", 0.10, 13, 15);
    private static final List<ElementGrounding> ALL = List.of(FIRE, WATER, WIND, EARTH, LIGHTNING);

    /** Mirrors the real rules' shape: one element, two spellings of the same fluid. */
    private static final ElementGrounding TWO_VARIANT_WATER =
            new ElementGrounding.Physical("water", Set.of("Water_Source", "Water"), Set.of("Shores"));

    private static final class FakeTerrain implements TerrainSample {
        private final Map<String, String> fluids = new HashMap<>();
        private final java.util.Set<String> solids = new java.util.HashSet<>();
        private final Map<String, Integer> surfaces = new HashMap<>();
        private String environment;
        private int sky = 15;
        private Double storm;
        private boolean everythingLoaded = true;

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }

        FakeTerrain surface(int x, int z, int y) {
            surfaces.put(x + ":" + z, y);
            return this;
        }

        FakeTerrain solid(int x, int y, int z) {
            solids.add(key(x, y, z));
            return this;
        }

        FakeTerrain fluid(int x, int y, int z, String id) {
            fluids.put(key(x, y, z), id);
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

        FakeTerrain storm(double probability) {
            this.storm = probability;
            return this;
        }

        FakeTerrain nothingLoaded() {
            this.everythingLoaded = false;
            return this;
        }

        @Override
        public boolean isLoaded(int x, int y, int z) {
            return everythingLoaded;
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
            return everythingLoaded && solids.contains(key(x, y, z));
        }

        @Override
        public OptionalInt surfaceHeightAt(int x, int z) {
            Integer y = surfaces.get(x + ":" + z);
            return y == null ? OptionalInt.empty() : OptionalInt.of(y);
        }

        @Override
        public OptionalDouble stormProbabilityAt(int x, int y, int z) {
            return storm == null ? OptionalDouble.empty() : OptionalDouble.of(storm);
        }
    }

    private static List<SiteTyping> score(FakeTerrain terrain) {
        return SiteScorer.scoreAll(new WorldPoint2d(0, 0), terrain, P, ALL);
    }

    private static List<SiteTyping> scoreOnly(FakeTerrain terrain, ElementGrounding grounding) {
        return SiteScorer.scoreAll(new WorldPoint2d(0, 0), terrain, P, List.of(grounding));
    }

    private static Optional<SiteTyping> typing(List<SiteTyping> typings, String element) {
        return typings.stream().filter(t -> t.elementId().equals(element)).findFirst();
    }

    // --- absence of a result, versus a negative result -------------------------------------------

    @Test
    void unknownSurface_yieldsNoTypings_notAZeroScore() {
        assertTrue(score(new FakeTerrain()).isEmpty());
    }

    @Test
    void unloadedRegion_yieldsNoTypings() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone4_Volcanoes")
                .nothingLoaded();
        assertTrue(score(t).isEmpty(), "an unloaded region must not produce a typing");
    }

    // --- Physical: the fluid is required ---------------------------------------------------------

    @Test
    void physical_volcanicEnvironmentWithoutLava_doesNotQualifyAsFire() {
        // The headline defect of the uniform rule this hierarchy replaced. Environment ids are a
        // regional classification, not a promise about a coordinate: a volcanic region with no lava
        // in it is where a Fire site would be PLAUSIBLE, which is not a claim residue should make.
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone4_Volcanoes")
                .sky(15);
        assertTrue(typing(score(t), "fire").isEmpty(), "volcanic environment alone must not type a Fire site");
    }

    @Test
    void physical_lavaAlone_qualifies() {
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).fluid(8, 64, 8, "Fluid_Lava");
        SiteTyping fire = typing(score(t), "fire").orElseThrow();
        assertTrue(fire.score() >= P.qualifyingScore(), "lava alone must qualify, got " + fire.score());
    }

    @Test
    void physical_environmentOnlyCorroborates_raisingTheScore() {
        FakeTerrain bare = new FakeTerrain().surface(0, 0, 64).fluid(0, 64, 8, "Fluid_Lava");
        FakeTerrain corroborated = new FakeTerrain()
                .surface(0, 0, 64)
                .fluid(0, 64, 8, "Fluid_Lava")
                .environment("Env_Zone4_Volcanoes");
        assertTrue(typing(score(corroborated), "fire").orElseThrow().score()
                > typing(score(bare), "fire").orElseThrow().score());
    }

    @Test
    void physical_lavaBelowTheSurface_isStillFound() {
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).fluid(0, 56, 0, "Fluid_Lava");
        assertTrue(typing(score(t), "fire").isPresent());
    }

    @Test
    void physical_twoVariantsOfTheSameFluid_addTheFluidWeightOnce() {
        // Reachable in production, not hypothetical: HytaleDerivationRules requires
        // Set.of("Water_Source", "Water") for water and Set.of("Lava_Source", "Lava") for fire,
        // because getFluidId reports the flowing variant as readily as the source. A site holding
        // both therefore scored 2 x fluidWeight and outranked a genuine shoreline — ranking only,
        // since one source already meets the threshold, but the ordering is what the probe is read
        // for.
        FakeTerrain both = new FakeTerrain()
                .surface(0, 0, 64)
                .fluid(0, 64, 8, "Water_Source")
                .fluid(8, 64, 0, "Water");
        FakeTerrain one = new FakeTerrain().surface(0, 0, 64).fluid(0, 64, 8, "Water_Source");

        double bothScore = typing(scoreOnly(both, TWO_VARIANT_WATER), "water")
                .orElseThrow()
                .score();
        double oneScore =
                typing(scoreOnly(one, TWO_VARIANT_WATER), "water").orElseThrow().score();

        assertEquals(oneScore, bothScore, 1e-9, "a second matching variant must not add a second fluidWeight");
        assertEquals(P.fluidWeight(), bothScore, 1e-9, "one fluid contribution, whatever matched");
    }

    @Test
    void physical_everyMatchedVariant_isStillNamedInTheReasons() {
        // Scoring once must not cost the operator the detail. The probe is judged by walking to the
        // site, so which variants were found is exactly what makes a reading interpretable.
        FakeTerrain both = new FakeTerrain()
                .surface(0, 0, 64)
                .fluid(0, 64, 8, "Water_Source")
                .fluid(8, 64, 0, "Water");
        List<String> reasons = typing(scoreOnly(both, TWO_VARIANT_WATER), "water")
                .orElseThrow()
                .reasons();
        assertTrue(
                reasons.stream().anyMatch(r -> r.contains("Water_Source")),
                "source variant must appear in reasons, got " + reasons);
        assertTrue(
                reasons.stream().anyMatch(r -> r.contains("Water")),
                "flowing variant must appear in reasons, got " + reasons);
    }

    // --- Geometric: both halves needed ----------------------------------------------------------

    @Test
    void geometric_environmentAlone_scoresButDoesNotQualify() {
        // A mountain environment without exposure is a mountain's interior.
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone3_Mountains")
                .sky(2);
        SiteTyping wind = typing(score(t), "wind").orElseThrow();
        assertTrue(wind.score() > 0, "it should score, so the probe can show the near-miss");
        assertTrue(wind.score() < P.qualifyingScore(), "but it must not qualify, got " + wind.score());
    }

    @Test
    void geometric_environmentPlusExposure_qualifies() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone3_Mountains")
                .sky(15);
        assertTrue(typing(score(t), "wind").orElseThrow().score() >= P.qualifyingScore());
    }

    @Test
    void geometric_skyLightAloneQualifiesNothing() {
        // No environment match anywhere: open plain at full exposure must produce no Wind typing at
        // all. Otherwise every open column in the world is a Wind site.
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Plains")
                .sky(15);
        assertTrue(typing(score(t), "wind").isEmpty());
    }

    @Test
    void geometric_earthUsesTheInverseExposureBand() {
        FakeTerrain deep = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Caves_Plains")
                .sky(0);
        FakeTerrain mouth = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Caves_Plains")
                .sky(15);
        assertTrue(typing(score(deep), "earth").orElseThrow().score() >= P.qualifyingScore());
        assertTrue(
                typing(score(mouth), "earth").orElseThrow().score() < P.qualifyingScore(),
                "a cave read at full sky is a cave mouth and must score lower");
    }

    // --- Weather: Lightning, the element with no physical presence -------------------------------

    @Test
    void weather_noStormData_yieldsNoLightningTyping() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Plains")
                .sky(15);
        assertTrue(typing(score(t), "lightning").isEmpty(), "absent storm data is not a zero");
    }

    @Test
    void weather_zoneOneTrickleOfStorms_isGatedOut() {
        // Zone 1 sits near 0.014 storm share. A trickle must contribute nothing, or every
        // environment with any rain becomes faintly Lightning.
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).sky(15).storm(0.014);
        assertTrue(typing(score(t), "lightning").isEmpty(), "0.014 must not pass the 0.10 gate");
    }

    @Test
    void weather_crucibleStormShareWithExposure_qualifies() {
        // Env_Zone4_Crucible carries Zone4_Swamp_Storm at weight 50 against Gloomy at 50.
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).sky(15).storm(0.50);
        SiteTyping lightning = typing(score(t), "lightning").orElseThrow();
        assertTrue(
                lightning.score() >= P.qualifyingScore(),
                "0.50 storm + exposure should qualify, got " + lightning.score());
    }

    @Test
    void weather_stormyButRoofed_doesNotQualify() {
        // A storm overhead means nothing in a cave.
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).sky(0).storm(0.50);
        SiteTyping lightning = typing(score(t), "lightning").orElseThrow();
        assertTrue(lightning.score() < P.qualifyingScore(), "roofed must not qualify, got " + lightning.score());
        assertTrue(
                lightning.reasons().stream().anyMatch(r -> r.contains("under a roof")),
                "the probe must say why it scored low");
    }

    @Test
    void weather_needsNoEnvironmentSubstring_atAll() {
        // The point of the Weather grounding: Lightning is sited by what HAPPENS there, so it must
        // qualify with no environment name match of any kind.
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).sky(15).storm(0.60);
        assertTrue(typing(score(t), "lightning").isPresent());
    }

    // --- mixed sites and ordering ---------------------------------------------------------------

    @Test
    void aSiteQualifyingForTwoElements_reportsBothAboveTheThreshold() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Shores")
                .fluid(8, 64, 0, "Fluid_Lava")
                .fluid(-8, 64, 0, "Fluid_Water");
        long qualifying =
                score(t).stream().filter(s -> s.score() >= P.qualifyingScore()).count();
        assertEquals(2, qualifying, "expected a mixed site; got " + score(t));
    }

    @Test
    void typingsAreOrderedBestFirst() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Swamps")
                .fluid(0, 64, 8, "Fluid_Water")
                .fluid(16, 64, 0, "Fluid_Lava");
        List<SiteTyping> typings = score(t);
        assertEquals("water", typings.get(0).elementId(), "water had fluid + corroboration, fire only fluid");
        for (int i = 1; i < typings.size(); i++) {
            assertTrue(typings.get(i - 1).score() >= typings.get(i).score(), "not sorted descending");
        }
    }

    // --- construction guards --------------------------------------------------------------------

    @Test
    void physicalGroundingWithoutAFluid_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElementGrounding.Physical("fire", Set.of(), Set.of("Volcanoes")));
    }

    @Test
    void geometricGroundingWithoutAnEnvironment_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ElementGrounding.Geometric(
                        "wind", Set.of(), 12, 15, ElementGrounding.Occupancy.OPEN_ABOVE_SOLID));
    }

    @Test
    void weatherGroundingWithAnImpossibleProbability_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ElementGrounding.Weather("lightning", 1.5, 13, 15));
    }
}

package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.ElementRule;
import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.SiteTyping;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Scoring is tested against a fake sample rather than the game, which is the point of the injected
 * port: the rules that decide whether a Fire nexus lands at the lava lake are checkable on any
 * machine, with no jar present.
 */
class SiteScorerTest {

    private static final LatticeParameters P = LatticeParameters.defaults();

    private static final ElementRule FIRE =
            new ElementRule("fire", Set.of("Fluid_Lava"), Set.of("Caves_Volcanic", "Volcanoes"), 0, 15);
    private static final ElementRule WATER =
            new ElementRule("water", Set.of("Fluid_Water"), Set.of("Shores", "Swamps", "Glacial"), 0, 15);
    private static final ElementRule WIND = new ElementRule("wind", Set.of(), Set.of("Peaks", "Mountains"), 12, 15);
    private static final List<ElementRule> RULES = List.of(FIRE, WATER, WIND);

    /** A hand-built world: nothing loaded unless placed, so a test says exactly what exists. */
    private static final class FakeTerrain implements TerrainSample {
        private final Map<String, String> fluids = new HashMap<>();
        private final Map<String, Integer> surfaces = new HashMap<>();
        private String environment;
        private int sky = 15;
        private boolean everythingLoaded = true;

        private static String key(int x, int y, int z) {
            return x + ":" + y + ":" + z;
        }

        FakeTerrain surface(int x, int z, int y) {
            surfaces.put(x + ":" + z, y);
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
        public OptionalInt surfaceHeightAt(int x, int z) {
            Integer y = surfaces.get(x + ":" + z);
            return y == null ? OptionalInt.empty() : OptionalInt.of(y);
        }
    }

    private static List<SiteTyping> score(FakeTerrain terrain, double x, double z) {
        return SiteScorer.scoreAll(new WorldPoint2d(x, z), terrain, P, RULES);
    }

    @Test
    void unknownSurface_yieldsNoTypings_notAZeroScore() {
        // The absence of a result, not a negative result. Returning a zero-scored typing here would
        // make "nothing there" indistinguishable from "nothing known", which is the exact confusion
        // TerrainSample.isLoaded exists to prevent.
        assertTrue(score(new FakeTerrain(), 0, 0).isEmpty());
    }

    @Test
    void unloadedRegion_yieldsNoTypings() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone4_Volcanoes")
                .nothingLoaded();
        assertTrue(score(t, 0, 0).isEmpty(), "an unloaded region must not produce a typing");
    }

    @Test
    void lavaInTheNeighbourhood_typesTheSiteAsFire() {
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).fluid(8, 64, 8, "Fluid_Lava");
        List<SiteTyping> typings = score(t, 0, 0);
        assertFalse(typings.isEmpty());
        assertEquals("fire", typings.get(0).elementId());
        assertTrue(typings.get(0).reasons().stream().anyMatch(r -> r.contains("Fluid_Lava")));
    }

    @Test
    void lavaBelowTheSurface_isStillFound() {
        // A lava lake under a thin crust is exactly the kind of site a player would call obvious, and
        // sampling the surface plane alone would miss it. This pins the vertical span.
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).fluid(0, 56, 0, "Fluid_Lava");
        assertEquals("fire", score(t, 0, 0).get(0).elementId());
    }

    @Test
    void environmentAlone_typesTheSite() {
        FakeTerrain t = new FakeTerrain().surface(0, 0, 64).environment("Env_Zone2_Caves_Volcanic_T3");
        List<SiteTyping> typings = score(t, 0, 0);
        assertEquals("fire", typings.get(0).elementId());
        // The substring match is what lets one rule cover the T1/T2/T3 tiers without listing them.
        assertTrue(typings.get(0).reasons().stream().anyMatch(r -> r.contains("Caves_Volcanic_T3")));
    }

    @Test
    void skyLightNeverQualifiesASiteOnItsOwn() {
        // Wind's rule has no fluids and a sky range of 12..15. On an open plain with no matching
        // environment it must score nothing -- otherwise every open column in the world becomes a
        // Wind site, which is how a derivation stops meaning anything.
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Plains")
                .sky(15);
        assertTrue(
                score(t, 0, 0).stream().noneMatch(s -> s.elementId().equals("wind")),
                "sky light alone qualified a site");
    }

    @Test
    void skyLightAddsToASiteThatAlreadyMatchedLocationally() {
        FakeTerrain exposed = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone3_Mountains")
                .sky(15);
        FakeTerrain shaded = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone3_Mountains")
                .sky(3);
        double withSky = score(exposed, 0, 0).get(0).score();
        double withoutSky = score(shaded, 0, 0).get(0).score();
        assertTrue(withSky > withoutSky, "exposure must raise the score of an already-matching site");
    }

    @Test
    void aSiteQualifyingForTwoElements_reportsBothAboveTheThreshold() {
        // The mixed nexus, under the placed-nexus model: not a crossing of two currents but "a site
        // that qualifies for two elements" -- a river mouth beside lava. Both must clear
        // qualifyingScore, and the second is half the answer rather than a runner-up.
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Shores")
                .fluid(8, 64, 0, "Fluid_Lava")
                .fluid(-8, 64, 0, "Fluid_Water");
        List<SiteTyping> typings = score(t, 0, 0);
        long qualifying =
                typings.stream().filter(s -> s.score() >= P.qualifyingScore()).count();
        assertEquals(2, qualifying, "expected a mixed site; got " + typings);
    }

    @Test
    void typingsAreOrderedBestFirst() {
        FakeTerrain t = new FakeTerrain()
                .surface(0, 0, 64)
                .environment("Env_Zone1_Swamps")
                .fluid(0, 64, 8, "Fluid_Water")
                .fluid(16, 64, 0, "Fluid_Lava");
        List<SiteTyping> typings = score(t, 0, 0);
        assertEquals("water", typings.get(0).elementId(), "water matched fluid + environment, fire only fluid");
        for (int i = 1; i < typings.size(); i++) {
            assertTrue(typings.get(i - 1).score() >= typings.get(i).score(), "not sorted descending");
        }
    }

    @Test
    void ruleMatchingNothing_isRejectedAtConstruction() {
        // A sky-range-only rule would qualify every column in the world. Caught at construction
        // rather than producing a derivation that silently means nothing.
        assertThrows(IllegalArgumentException.class, () -> new ElementRule("air", Set.of(), Set.of(), 12, 15));
    }
}

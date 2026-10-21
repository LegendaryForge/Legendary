package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.SiteTyping;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

/**
 * Terrain disposes: scores one candidate against every element grounding and reports all of them.
 *
 * <p>Scoring dispatches on the grounding's <em>type</em>, which is the fix for a real defect. The
 * first version applied one uniform rule — fluids, environments and a sky range for every element —
 * and the weights meant an environment match plus exposure reached the qualifying threshold exactly.
 * So a volcanic region with no lava in it scored as a Fire site, while Wind and Lightning could only
 * ever qualify that same way because they have no physical feature to require. Both halves were
 * wrong, from one rule shape.
 */
public final class SiteScorer {

    private SiteScorer() {}

    /**
     * Every grounding's score for this candidate, best first, zero-scoring groundings omitted.
     *
     * <p>All typings are returned rather than just the winner, for two reasons. A mixed site is
     * defined under the placed-nexus model as <em>a site that qualifies for two elements</em> — a
     * river mouth beside lava — rather than as a crossing of two currents, so the second typing is
     * not a runner-up, it is half the answer. And a probe whose job is to be judged needs its
     * near-misses visible: "Fire 4.0, Water 1.0" and "Fire 4.0, Water 1.9" describe different places,
     * and only one is about to become mixed when a dial moves.
     *
     * <p>Returns an empty list when the candidate's own column is not loaded. That is the absence of
     * a result rather than a negative one, which is the whole reason
     * {@link TerrainSample#isLoaded} exists.
     */
    public static List<SiteTyping> scoreAll(
            WorldPoint2d candidate,
            TerrainSample sample,
            LatticeParameters parameters,
            List<ElementGrounding> groundings) {
        int x = (int) Math.floor(candidate.x());
        int z = (int) Math.floor(candidate.z());
        OptionalInt surface = sample.surfaceHeightAt(x, z);
        if (surface.isEmpty()) {
            return List.of();
        }
        int y = surface.getAsInt();
        if (!sample.isLoaded(x, y, z)) {
            return List.of();
        }

        Set<String> fluids = fluidsNear(sample, x, y, z, parameters);
        Optional<String> environment = sample.environmentAt(x, y, z);
        int sky = sample.skyLightAt(x, y, z);

        List<SiteTyping> typings = new ArrayList<>();
        for (ElementGrounding grounding : groundings) {
            SiteTyping typing =
                    switch (grounding) {
                        case ElementGrounding.Physical physical ->
                            scorePhysical(physical, fluids, environment, parameters);
                        case ElementGrounding.Geometric geometric ->
                            scoreGeometric(geometric, environment, sky, parameters);
                        case ElementGrounding.Weather weather ->
                            scoreWeather(weather, sample, x, y, z, sky, parameters);
                    };
            if (typing != null) {
                typings.add(typing);
            }
        }
        Collections.sort(typings);
        return List.copyOf(typings);
    }

    /**
     * The fluid is required. No fluid, no score — not even when the environment matches.
     *
     * <p>That is the asymmetry the {@code Physical} type exists to enforce: a volcanic region is
     * where a Fire site would be <em>plausible</em>, and plausibility is not a claim residue should
     * make about a coordinate.
     */
    private static SiteTyping scorePhysical(
            ElementGrounding.Physical grounding,
            Set<String> fluids,
            Optional<String> environment,
            LatticeParameters parameters) {
        List<String> reasons = new ArrayList<>();
        boolean fluidPresent = false;
        for (String fluid : fluids) {
            if (grounding.requiredFluids().contains(fluid)) {
                fluidPresent = true;
                reasons.add("fluid " + fluid + " within " + parameters.neighbourhoodRadius() + "b");
            }
        }
        if (!fluidPresent) {
            return null;
        }
        // Once, however many variants matched. The requirement is "this fluid is here", and the
        // game spells one fluid two ways -- `Set.of("Water_Source", "Water")` -- so accumulating per
        // variant made a source-plus-flow site outrank a genuine shoreline on a difference that is
        // about block bookkeeping rather than terrain. Every matched variant is still reported in
        // the reasons, because that is what makes a probe reading interpretable on foot.
        double score = parameters.fluidWeight();
        if (environment.isPresent()
                && ElementGrounding.environmentMatches(environment.get(), grounding.corroboratingEnvironments())) {
            score += parameters.environmentWeight();
            reasons.add("environment " + environment.get() + " corroborates");
        }
        return new SiteTyping(grounding.elementId(), score, reasons);
    }

    /**
     * Terrain shape, with both halves needed to qualify.
     *
     * <p>An environment match alone scores {@code environmentWeight}, deliberately below the
     * qualifying threshold: a mountain environment without exposure is a mountain's interior. Sky
     * light is never read without an environment match, because on its own it would qualify every
     * open column in the world.
     */
    private static SiteTyping scoreGeometric(
            ElementGrounding.Geometric grounding, Optional<String> environment, int sky, LatticeParameters parameters) {
        if (environment.isEmpty()
                || !ElementGrounding.environmentMatches(environment.get(), grounding.environments())) {
            return null;
        }
        double score = parameters.environmentWeight();
        List<String> reasons = new ArrayList<>();
        reasons.add("environment " + environment.get());
        if (sky >= 0 && sky >= grounding.minSkyLight() && sky <= grounding.maxSkyLight()) {
            score += parameters.skyWeight();
            reasons.add("skyLight " + sky + " in " + grounding.minSkyLight() + ".." + grounding.maxSkyLight());
        } else {
            reasons.add("skyLight " + sky + " OUTSIDE " + grounding.minSkyLight() + ".." + grounding.maxSkyLight()
                    + " — environment alone does not qualify");
        }
        return new SiteTyping(grounding.elementId(), score, reasons);
    }

    /**
     * Storm probability, gated and then scaled, plus exposure.
     *
     * <p>The gate matters more than the scaling: a trickle of storms must contribute nothing at all,
     * or every environment with any rain at all becomes faintly Lightning. Zone 1 near 0.014 should
     * score zero; {@code Env_Zone4_Crucible} at 0.50 should score well.
     *
     * <p>Exposure is required in practice as well as scaled, for a reason that is fiction rather than
     * mechanism: a storm overhead means nothing in a cave. A roofed position reports its sky light in
     * the reasons so the probe shows why a storm-frequent place scored low.
     */
    private static SiteTyping scoreWeather(
            ElementGrounding.Weather grounding,
            TerrainSample sample,
            int x,
            int y,
            int z,
            int sky,
            LatticeParameters parameters) {
        OptionalDouble storm = sample.stormProbabilityAt(x, y, z);
        if (storm.isEmpty() || storm.getAsDouble() < grounding.minStormProbability()) {
            return null;
        }
        double probability = storm.getAsDouble();
        double score = probability * parameters.stormWeight();
        List<String> reasons = new ArrayList<>();
        reasons.add(String.format("storm probability %.3f x %.1f", probability, parameters.stormWeight()));
        if (sky >= 0 && sky >= grounding.minSkyLight() && sky <= grounding.maxSkyLight()) {
            score += parameters.skyWeight();
            reasons.add("skyLight " + sky + " in " + grounding.minSkyLight() + ".." + grounding.maxSkyLight());
        } else {
            reasons.add("skyLight " + sky + " OUTSIDE " + grounding.minSkyLight() + ".." + grounding.maxSkyLight()
                    + " — a storm overhead means nothing under a roof");
        }
        return new SiteTyping(grounding.elementId(), score, reasons);
    }

    /**
     * Distinct fluids in the candidate's neighbourhood.
     *
     * <p>A {@link TreeSet} so the order is stable and the probe's output is diffable between runs; an
     * unordered set made two identical runs look different.
     *
     * <p>The vertical span matters more than it looks: lava sits below the surface far more often than
     * on it, and sampling the surface plane alone would miss a lava lake under a thin crust — exactly
     * the kind of site a player would call obvious.
     */
    private static Set<String> fluidsNear(
            TerrainSample sample, int x, int surfaceY, int z, LatticeParameters parameters) {
        Set<String> found = new TreeSet<>();
        int r = parameters.neighbourhoodRadius();
        int step = parameters.sampleStep();
        int span = parameters.verticalSpan();
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                for (int dy = -span; dy <= span; dy += step) {
                    int px = x + dx;
                    int py = surfaceY + dy;
                    int pz = z + dz;
                    if (sample.isLoaded(px, py, pz)) {
                        sample.fluidAt(px, py, pz).ifPresent(found::add);
                    }
                }
            }
        }
        return found;
    }
}

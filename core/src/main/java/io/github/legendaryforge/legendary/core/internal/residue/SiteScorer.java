package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.ElementRule;
import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.SiteTyping;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

/** Terrain disposes: scores one candidate against every element rule and reports all of them. */
public final class SiteScorer {

    private SiteScorer() {}

    /**
     * Every rule's score for this candidate, best first, zero-scoring rules omitted.
     *
     * <p>All typings are returned rather than just the winner, for two reasons. A mixed site is
     * defined under the placed-nexus model as <em>a site that qualifies for two elements</em> — a
     * river mouth beside lava — rather than as a crossing of two currents, so the second-place
     * typing is not a runner-up, it is half the answer. And a probe whose job is to be judged needs
     * its near-misses visible: "Fire 4.0, Water 1.0" and "Fire 4.0, Water 1.9" describe different
     * places, and only one of them is about to become a mixed nexus when a dial moves.
     *
     * <p>Returns an empty list when the candidate's own column is not loaded. That is not a
     * negative result — it is the absence of one, and the distinction is the whole reason
     * {@link TerrainSample#isLoaded} exists.
     */
    public static List<SiteTyping> scoreAll(
            WorldPoint2d candidate, TerrainSample sample, LatticeParameters parameters, List<ElementRule> rules) {
        int x = (int) Math.floor(candidate.x());
        int z = (int) Math.floor(candidate.z());
        OptionalInt surface = sample.surfaceHeightAt(x, z);
        if (surface.isEmpty()) {
            return List.of();
        }
        int surfaceY = surface.getAsInt();
        if (!sample.isLoaded(x, surfaceY, z)) {
            return List.of();
        }

        Set<String> fluids = fluidsNear(sample, x, surfaceY, z, parameters);
        Optional<String> environment = sample.environmentAt(x, surfaceY, z);
        int sky = sample.skyLightAt(x, surfaceY, z);

        List<SiteTyping> typings = new ArrayList<>();
        for (ElementRule rule : rules) {
            double score = 0;
            List<String> reasons = new ArrayList<>();

            for (String fluid : fluids) {
                if (rule.fluidIds().contains(fluid)) {
                    score += parameters.fluidWeight();
                    reasons.add("fluid " + fluid + " within " + parameters.neighbourhoodRadius() + "b");
                }
            }
            if (environment.isPresent() && rule.matchesEnvironment(environment.get())) {
                score += parameters.environmentWeight();
                reasons.add("environment " + environment.get());
            }
            // Sky light only adds to a rule that already matched something locational. On its own it
            // would qualify every open column in the world for Wind, which is how a derivation stops
            // meaning anything.
            if (score > 0 && sky >= 0 && sky >= rule.minSkyLight() && sky <= rule.maxSkyLight()) {
                score += parameters.skyWeight();
                reasons.add("skyLight " + sky + " in " + rule.minSkyLight() + ".." + rule.maxSkyLight());
            }

            if (score > 0) {
                typings.add(new SiteTyping(rule.elementId(), score, reasons));
            }
        }
        Collections.sort(typings);
        return List.copyOf(typings);
    }

    /**
     * Distinct qualifying-capable fluids in the candidate's neighbourhood.
     *
     * <p>A {@link TreeSet} so the order is stable and the probe's output is diffable between runs;
     * an unordered set made two identical runs look different.
     *
     * <p>The vertical span matters more than it looks: lava sits below the surface far more often
     * than on it, and sampling the surface plane alone would miss a lava lake under a thin crust —
     * which is exactly the kind of site a player would call obvious.
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

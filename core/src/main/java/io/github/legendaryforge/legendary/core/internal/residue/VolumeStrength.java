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

/**
 * How much of one element is in a candidate's window — anchor-density design §2, "strength is
 * volume".
 *
 * <p>A <strong>count</strong> of sample points satisfying the element's predicate, not a weighted sum
 * of matched signals. The difference is the whole point: the weighted scorer returned the same answer
 * for a trickle and a caldera, so "strong" had nothing to grip.
 *
 * <p><strong>The window is the full world column.</strong> Anchors may sit above or below ground, so
 * a span chosen around a surface would decide in advance where they can be — and underground rivers
 * and deep lava lakes are exactly the features such a span would hide. The V1 column chunk shape
 * means a loaded column is loaded for its whole height, so this costs reads rather than loads.
 *
 * <p><strong>The anchor's elevation is the modal sampled level</strong>, lowest y winning a tie. §4
 * says a {@code Physical} anchor takes y from "the fluid surface in the window" — singular, which a
 * column holding both a surface lake and an underground river does not have. The modal level picks
 * the denser body; a mean or a midpoint would place the anchor in the rock between them.
 */
public final class VolumeStrength {

    private VolumeStrength() {}

    /**
     * Measure one element at one candidate, before any floor or suppression is applied.
     *
     * <p>Empty when no sampled point satisfied the predicate. That covers both "no qualifying
     * terrain" and "nothing loaded", and the caller distinguishes them through
     * {@link Anchor#samplesTaken} on a non-empty result — a probe should report a zero-sample window
     * as unknown rather than as absent.
     *
     * @throws UnsupportedOperationException for a {@code Weather} grounding; see
     *     {@link #satisfiesWeather}
     */
    public static Optional<Anchor> measure(
            TerrainSample sample, AnchorParameters parameters, ElementGrounding grounding, WorldPoint2d candidate) {
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
            TerrainSample sample, ElementGrounding grounding, AnchorParameters parameters, int x, int y, int z) {
        return switch (grounding) {
            case ElementGrounding.Physical physical ->
                sample.fluidAt(x, y, z)
                        .filter(physical.requiredFluids()::contains)
                        .isPresent();
            case ElementGrounding.Geometric geometric -> satisfiesGeometric(sample, geometric, parameters, x, y, z);
            case ElementGrounding.Weather weather -> satisfiesWeather(weather);
        };
    }

    /**
     * Both gates are required, as they were in the weighted scorer.
     *
     * <p>Sky light alone would qualify every open column in the world, and an environment match alone
     * is a mountain's interior rather than its ridge. Occupancy is the third condition and the one
     * that separates Earth from Wind.
     *
     * <p>Earth's production grounding widens both gates to accept everything, so occupancy alone
     * decides for it. That is deliberate — rock needs no cave label and no darkness — and it is why
     * these gates stay here rather than being removed: Wind genuinely needs them.
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
     * counting the existing storm-plus-sky rule would tally all exposed air in a storm region, which
     * carries the same valley-above-summit inversion {@code OPEN_ABOVE_SOLID} exists to fix — so any
     * number produced here would be known-wrong. An empty result would be worse: it reads as "no
     * qualifying terrain", which is the silent-zero failure this port's {@code isLoaded} exists to
     * prevent, one level up.
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

package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Objects;
import java.util.Set;

/**
 * How an element is grounded in the world — and therefore what could possibly site it.
 *
 * <p>This is a sealed hierarchy rather than one configurable rule because <strong>the elements are
 * not grounded the same way, and a single rule shape is wrong for most of them.</strong> The first
 * version of this code used one record with fluids, environments and a sky range, and it was wrong
 * in both directions at once: it let {@code Env_Zone4_Volcanoes} with no lava anywhere qualify as a
 * Fire site, while Wind and Lightning — which have no physical presence to require — could only ever
 * qualify that same way. Encoding the distinction in the type system means
 * {@link io.github.legendaryforge.legendary.core.internal.residue.SiteScorer} cannot forget it.
 *
 * <p>Three groundings, and a fourth case that deliberately has no member here:
 *
 * <ul>
 *   <li>{@link Physical} — a block or fluid exists. Fire is lava; Water is water. The feature is
 *       <strong>required</strong>; environment only corroborates.
 *   <li>{@link Geometric} — no marker exists, but the geometry is real. Earth is enclosed stone,
 *       Wind is open exposure. Qualifies on terrain shape, never on a marker.
 *   <li>{@link Weather} — <strong>no physical presence at all.</strong> Lightning is not a thing that
 *       <em>is</em> somewhere, it is a thing that <em>happens</em> somewhere, so its signal is storm
 *       probability. That is authored data rather than inference:
 *       {@code Env_Zone4_Crucible.json} carries {@code WeatherForecasts} with
 *       {@code Zone4_Swamp_Storm} at weight 50 against {@code Zone4_Swamp_Gloomy} at 50.
 *   <li><strong>Void has no grounding and that is the decision, not an omission.</strong> It is a
 *       staged incursion scoped to a Fragment — the engine starts it on a timer inside a portal
 *       instance and ends it by eviction — so nothing about terrain should site it. Its droppers span
 *       16 environments across three zones with no terrain character, which corroborates the same
 *       reading from placement data.
 * </ul>
 */
public sealed interface ElementGrounding {

    /** The element this grounding types a site as. Opaque: there is no enum of the roster anywhere. */
    String elementId();

    /**
     * How many satisfying sample points a candidate needs before it is an anchor at all — an
     * absolute count, not a share of the window.
     *
     * <p><strong>Per element, because "enough of it" differs between them by orders of
     * magnitude.</strong> This replaced a single {@code minimumStrengthFraction} on
     * {@code AnchorParameters}, which asked "does this element dominate its neighbourhood". That is a
     * sensible question for Earth, Wind and Water at 20-60% of a column and an unanswerable one for
     * Fire: measured 2026-10-23 in a lava biome, the richest candidate in a 1 km region held 261 lava
     * samples of a 43,560-sample window — 0.6% against a 1% floor — while a player swam in deep lava
     * rivers. Lava is riverine and the window is areal, so no window large enough to contain a river
     * is small enough for the river to dominate it.
     *
     * <p>A count also says what the design actually meant. {@code AnchorParameters} describes the
     * floor's purpose as admitting "a lake, an ocean, a caldera — not a 2x2 puddle", and that is an
     * absolute amount. A share of a window additionally moves when the window moves, so the same body
     * of lava would qualify or not depending on {@code measurementRadius}, which is a dial about
     * perception rather than about the element.
     *
     * <p>Interpreting it needs {@code sampleStep}: one sample stands for {@code step³} blocks, so at
     * the default step of 8 a floor of 10 is roughly 5,120 block-volumes.
     */
    int minimumStrengthSamples();

    /** Whether a resolved environment id contains any of {@code needles} as a substring. */
    static boolean environmentMatches(String environmentId, Set<String> needles) {
        for (String needle : needles) {
            if (environmentId.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void requireMinimumStrengthSamples(String elementId, int minimumStrengthSamples) {
        if (minimumStrengthSamples <= 0) {
            throw new IllegalArgumentException("minimumStrengthSamples for " + elementId
                    + " must be positive, got " + minimumStrengthSamples
                    + " — a zero floor makes a single sample of an element an anchor site");
        }
    }

    private static void requireElementId(String elementId) {
        Objects.requireNonNull(elementId, "elementId");
        if (elementId.isBlank()) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
    }

    private static void requireSkyRange(int min, int max) {
        if (min < 0 || max > 15 || min > max) {
            throw new IllegalArgumentException(
                    "sky light bounds must satisfy 0 <= min <= max <= 15, got " + min + ".." + max);
        }
    }

    /**
     * What counts as the element being present at a sampled point, for a {@link Geometric}
     * grounding.
     *
     * <p>Earth and Wind are the same grounding scored by the same rule, and the only thing that
     * differs is this. Anchor-density design §2: Earth counts points where "the point is rock", Wind
     * counts points that are "open air in a matching environment". Without this the scorer needs an
     * {@code elementId} branch, which is exactly the per-element special-casing the sealed hierarchy
     * exists to prevent.
     */
    enum Occupancy {
        /**
         * The point counts when it is solid.
         *
         * <p>A concentration of earth is a lot of rock, and that already discriminates without any
         * baseline: measured at sample step 8 over the full column, a column is 20% solid over an
         * ocean floor, 37.5% over a plain and 80% over a peak. "A lot of rock" is "the ground is
         * high here".
         */
        SOLID,
        /**
         * The point counts when it holds <strong>no block and no fluid</strong> — open space.
         *
         * <p>The exact complement of {@link #SOLID} minus fluid, which makes air and earth
         * symmetric: earth is rock, air is not-rock-and-not-water. There is no sky gate and no
         * environment list, so a cave counts as air — air is there, and the element is named for
         * open space rather than for any wind mechanic the game does not have.
         *
         * <p><strong>The fluid exclusion is load-bearing, not defensive.</strong> {@code getBlock}
         * reports 0 inside a fluid — measured on 0.7.4, {@code getBlock=0} alongside
         * {@code getFluidId=8} — so {@code isSolidAt} is false there and "not solid" alone would
         * count every submerged position as air. Water is not air.
         *
         * <p>Known consequence, recorded rather than discovered later: counting open space over the
         * full column is inversely correlated with ground height, roughly 80% of a column over an
         * ocean floor against 20% at a peak, which is the exact inverse of {@code SOLID}'s 20% to
         * 80%. So air anchors favour valleys and lowlands while earth anchors favour high ground,
         * and the two tile the world rather than competing for it.
         */
        OPEN
    }

    /**
     * An element with a physical presence: the fluid is required, the environment only corroborates.
     *
     * <p>This asymmetry is the whole point of the type. A region labelled volcanic with no lava in it
     * is not a Fire site — it is a place where a Fire site would be plausible, which is a different
     * claim and not one residue should make. Environment ids are a regional classification, not a
     * promise about any coordinate.
     */
    record Physical(
            String elementId,
            Set<String> requiredFluids,
            Set<String> corroboratingEnvironments,
            int minimumStrengthSamples)
            implements ElementGrounding {
        public Physical {
            requireElementId(elementId);
            requireMinimumStrengthSamples(elementId, minimumStrengthSamples);
            requiredFluids = Set.copyOf(Objects.requireNonNull(requiredFluids, "requiredFluids"));
            corroboratingEnvironments =
                    Set.copyOf(Objects.requireNonNull(corroboratingEnvironments, "corroboratingEnvironments"));
            if (requiredFluids.isEmpty()) {
                throw new IllegalArgumentException(
                        "Physical grounding for " + elementId + " has no required fluid. If the element has no "
                                + "physical presence, it is Geometric or Weather, not Physical.");
            }
        }
    }

    /**
     * An element grounded in terrain shape rather than in any marker.
     *
     * <p>Both signals are needed, and {@code SiteScorer} enforces it: an environment match alone
     * scores below the qualifying threshold, because "a mountain environment" without exposure is a
     * mountain's interior. Sky light alone qualifies nothing anywhere, by design — it would make
     * every open column in the world a Wind site.
     */
    record Geometric(
            String elementId,
            Set<String> environments,
            int minSkyLight,
            int maxSkyLight,
            Occupancy occupancy,
            int minimumStrengthSamples)
            implements ElementGrounding {
        public Geometric {
            requireElementId(elementId);
            requireMinimumStrengthSamples(elementId, minimumStrengthSamples);
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

    /**
     * An element whose presence is an event rather than a feature.
     *
     * <p>Lightning's only grounding. Storm probability comes from the environment's own
     * {@code WeatherForecasts} weights, so this is the one grounding where an environment id is not a
     * coarse proxy but the <strong>carrier of the primary signal</strong>.
     *
     * @param minStormProbability hard gate in {@code [0,1]}. A trickle of storms must not contribute
     *     at all — Zone 1 sits near 0.014 and should score nothing, while
     *     {@code Env_Zone4_Crucible} sits at 0.50
     */
    record Weather(
            String elementId, double minStormProbability, int minSkyLight, int maxSkyLight, int minimumStrengthSamples)
            implements ElementGrounding {
        public Weather {
            requireElementId(elementId);
            requireMinimumStrengthSamples(elementId, minimumStrengthSamples);
            requireSkyRange(minSkyLight, maxSkyLight);
            if (!(minStormProbability >= 0) || minStormProbability > 1) {
                throw new IllegalArgumentException("minStormProbability must be in [0,1], got " + minStormProbability);
            }
        }
    }
}

package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.CurrentEdge;
import io.github.legendaryforge.legendary.core.api.residue.CurrentForest;
import io.github.legendaryforge.legendary.core.api.residue.CurrentTopology;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Topology is geometry over already-selected anchors, so these tests build no world.
 *
 * <p>The parameters are explicit and deliberately <strong>not</strong> {@code defaults()}, for the
 * reason {@code AnchorSelectorTest} records: every fixture here is geometry chosen relative to a
 * suppression radius, and reading that radius from the production defaults made those tests fail
 * when a tuning dial moved for reasons unrelated to what they were testing.
 */
class CurrentsTest {

    private static final AnchorParameters P = new AnchorParameters(128, 0.35, 256, 128, 8, 8, 8);

    private static final long SEED = 0x5EEDL;

    /**
     * Earth: {@code Geometric}, and therefore an element that <strong>cannot be absent</strong>.
     * Rock is everywhere, which is what makes {@code Contained} invalid for it.
     */
    private static final ElementGrounding EARTH =
            new ElementGrounding.Geometric("earth", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.SOLID, 223_232);

    /**
     * Fire: {@code Physical}, and therefore an element that <strong>can</strong> be absent — no lava
     * in the window means no anchor, by arithmetic. That is what makes {@code Contained} valid.
     */
    private static final ElementGrounding FIRE =
            new ElementGrounding.Physical("fire", Set.of("Lava_Source"), Set.of("Volcanoes"), 5_120);

    private static Anchor at(double x, double z, int strength) {
        return new Anchor("earth", new WorldPoint3d(x, 64, z), strength, 100_000);
    }

    /**
     * The property that separates a drainage forest from "everything points at the maximum".
     *
     * <p>Three collinear anchors of increasing strength, all within reach of each other. The weakest
     * must link to the <em>middle</em> one, not to the strongest — otherwise the structure is a star
     * rather than a tree, every edge is as long as the region, and the "follow it upstream" walk has
     * exactly one step.
     */
    @Test
    void anAnchorLinksToTheNearestStrongerOneNotTheStrongest() {
        Anchor weak = at(0, 0, 10);
        Anchor middle = at(300, 0, 20);
        Anchor strong = at(600, 0, 30);

        CurrentForest forest =
                Currents.build(SEED, P, EARTH, new CurrentTopology.Sprawling(1000), List.of(weak, middle, strong));

        assertEquals(2, forest.edges().size(), "two edges for three anchors in a chain");
        assertEquals(middle.position(), forest.edges().get(0).to().position());
        assertEquals(weak.position(), forest.edges().get(0).from().position());
    }

    /**
     * Reach is a bound, not a preference — which is what makes the structure local.
     *
     * <p>The halo argument the design rests on is that an anchor's parent depends only on anchors
     * within {@code reach}. If a stronger anchor just outside reach could still claim it, the
     * dependency would be unbounded and a region's edges would depend on how far the caller
     * happened to look.
     */
    @Test
    void aStrongerAnchorBeyondReachLeavesTheWeakerOneASource() {
        Anchor weak = at(0, 0, 10);
        Anchor strong = at(900, 0, 20);

        CurrentForest forest =
                Currents.build(SEED, P, EARTH, new CurrentTopology.Sprawling(800), List.of(weak, strong));

        assertEquals(List.of(), forest.edges(), "900 b apart, reach 800: no edge");
        assertEquals(2, forest.sources().size(), "both anchors are sources");
    }

    /**
     * A reach that cannot produce an edge is a configuration error, not an empty answer.
     *
     * <p>No two anchors are ever closer than {@code suppressionRadius}: {@code AnchorSelector.beats}
     * is a <em>total</em> order, so of any two candidates inside that radius one strictly beats the
     * other and the loser is not an anchor. So a reach at or below it yields an empty forest for
     * every input, everywhere, and the measured probe confirmed exactly 0 edges at that reach.
     *
     * <p>Empty is the dangerous answer, which is why this throws: a caller that sets reach too small
     * gets a world with no currents in it and nothing anywhere says why.
     */
    @Test
    void aReachAtOrBelowTheSuppressionRadiusIsRejected() {
        List<Anchor> anchors = List.of(at(0, 0, 10), at(300, 0, 20));

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> Currents.build(SEED, P, EARTH, new CurrentTopology.Sprawling(P.suppressionRadius()), anchors));

        assertTrue(
                thrown.getMessage().contains("suppressionRadius"),
                "the message must name the parameter that makes this impossible, got: " + thrown.getMessage());
    }

    /**
     * Acyclicity, exercised where it is actually at risk: every strength tied.
     *
     * <p>The design claims the forest is acyclic <em>by construction</em>, because strength strictly
     * decreases along every edge. That claim rests entirely on {@code AnchorSelector.beats} being a
     * <strong>total</strong> order, and the case that tests it is a uniform field — the ocean, where
     * every strength is equal and only the position hash separates anything. A rule phrased "at
     * least as strong as mine" makes every tied pair mutually upstream and the nearest-neighbour
     * pairs become two-cycles.
     *
     * <p>So this walks every anchor to a source and bounds the walk. An unbounded walk is the
     * failure, and it would otherwise show up as a hang in whatever consumes the forest.
     */
    @Test
    void everyWalkTerminatesAtASourceWhenAllStrengthsTie() {
        List<Anchor> anchors = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                anchors.add(at(i * 400, j * 400, 500));
            }
        }

        CurrentForest forest = Currents.build(SEED, P, EARTH, new CurrentTopology.Sprawling(1200), anchors);

        Map<WorldPoint3d, WorldPoint3d> parent = new HashMap<>();
        for (CurrentEdge edge : forest.edges()) {
            assertNull(
                    parent.put(edge.from().position(), edge.to().position()),
                    "an anchor may have at most one upstream");
        }
        assertFalse(forest.sources().isEmpty(), "a finite forest must have at least one source");

        for (Anchor anchor : anchors) {
            WorldPoint3d at = anchor.position();
            int steps = 0;
            while (parent.containsKey(at)) {
                at = parent.get(at);
                steps++;
                assertTrue(steps <= anchors.size(), "walk from " + anchor.position() + " did not terminate");
            }
        }
    }

    /**
     * {@code Contained} must not span the gap between two groups — that is its entire reason.
     *
     * <p>A minimum spanning tree over all of an element's anchors must connect every one of them, so
     * for a patchy element it emits an edge bridging two separated groups across terrain where the
     * element is absent. {@code Contained} applies the spanning tree <em>within</em> each group
     * instead, which is right exactly where the grounding guarantees that absence.
     */
    @Test
    void containedSpansWithinEachGroupAndNeverBetweenThem() {
        List<Anchor> anchors = List.of(at(0, 0, 10), at(300, 0, 20), at(600, 0, 30), at(9000, 0, 40), at(9300, 0, 50));

        CurrentForest forest = Currents.build(SEED, P, FIRE, new CurrentTopology.Contained(600), anchors);

        assertEquals(3, forest.edges().size(), "2 edges in the group of three, 1 in the pair, 0 between");
        for (CurrentEdge edge : forest.edges()) {
            assertTrue(
                    edge.length() <= 600,
                    "no edge may exceed reach, got " + edge.length() + " b — that would be the bridge");
        }
    }

    /**
     * The tree inside a group is <em>minimum</em> spanning, not merely spanning.
     *
     * <p>Three collinear anchors at 0, 100 and 250, all one group. The minimum tree uses the two
     * short hops (100 and 150) for a total of 250. A star rooted at the strongest anchor also spans
     * the group and also respects reach, but costs 150 + 250 = 400 — and the difference is visible
     * in play, because current length is what crystal coverage is derived from.
     */
    @Test
    void theTreeInsideAGroupIsMinimal() {
        List<Anchor> anchors = List.of(at(0, 0, 10), at(100, 0, 20), at(250, 0, 30));

        CurrentForest forest = Currents.build(SEED, P, FIRE, new CurrentTopology.Contained(600), anchors);

        List<Double> lengths = new ArrayList<>();
        for (CurrentEdge edge : forest.edges()) {
            lengths.add(edge.length());
        }
        lengths.sort(Double::compareTo);
        assertEquals(List.of(100.0, 150.0), lengths, "the two short hops, not a star from the strongest");
    }

    /**
     * {@code Contained} on an element that cannot be absent is rejected, not merely inadvisable.
     *
     * <p>Grouping relies on empty terrain separating the groups, and only a grounding that can be
     * <em>absent</em> supplies it. A {@code Geometric} element cannot be absent — rock and open air
     * are everywhere — so single linkage over its anchors yields one unbounded group and the
     * structure degenerates into exactly the global spanning tree it exists to replace.
     *
     * <p>This is the pairing the design says must be impossible to get wrong. It fails loudly here
     * because the failure is otherwise invisible: a single enormous group still returns a valid
     * forest, and nothing in it says the gap it should not have crossed was crossed.
     */
    @Test
    void containedOnAGroundingThatCannotBeAbsentIsRejected() {
        List<Anchor> anchors = List.of(at(0, 0, 10), at(300, 0, 20));

        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> Currents.build(SEED, P, EARTH, new CurrentTopology.Contained(600), anchors));

        assertTrue(
                thrown.getMessage().contains("Geometric"),
                "the message must name the grounding kind that makes this impossible, got: " + thrown.getMessage());
    }

    /**
     * The other direction of that guard, because only testing the rejection leaves the accept path
     * unexamined — and a guard written in one direction is evidence the opposite one was never
     * considered.
     *
     * <p>{@code Weather} is the case worth naming rather than assuming. Lightning has no physical
     * presence at all, so one might expect it to be the awkward one; but its storm-probability gate
     * means it <em>can</em> be absent, and {@code Contained} needs nothing more than that. It does
     * not need a gradient, which is the thing Lightning genuinely lacks — its signal is constant per
     * environment — and which is why it gets this topology rather than the other one.
     */
    @Test
    void containedOnAWeatherGroundingIsAccepted() {
        ElementGrounding lightning = new ElementGrounding.Weather("lightning", 0.25, 0, 15, 223_232);
        List<Anchor> anchors = List.of(at(0, 0, 10), at(300, 0, 20));

        CurrentForest forest = Currents.build(SEED, P, lightning, new CurrentTopology.Contained(600), anchors);

        assertEquals(1, forest.edges().size(), "one group of two, so one current");
    }

    /**
     * Both topologies reject a non-positive reach at construction.
     *
     * <p>Separate from the suppression-radius floor above, which depends on parameters this record
     * does not have. This is the arithmetic case: a reach of zero or less is not a small reach, it is
     * not a reach.
     */
    @Test
    void aNonPositiveReachIsRejectedByBothTopologies() {
        assertThrows(IllegalArgumentException.class, () -> new CurrentTopology.Sprawling(0));
        assertThrows(IllegalArgumentException.class, () -> new CurrentTopology.Contained(-1));
    }
}

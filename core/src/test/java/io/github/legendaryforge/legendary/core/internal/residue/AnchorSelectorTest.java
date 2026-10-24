package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint3d;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Selection is geometry over already-measured candidates, so these tests build no world. The two
 * properties that matter: abundance must not change how many anchors exist, and the floor must make
 * a rare element simply absent rather than weakly present.
 */
class AnchorSelectorTest {

    /**
     * An explicit suppression radius, deliberately <strong>not</strong> {@code defaults()}.
     *
     * <p>Every fixture below is geometry chosen relative to this radius, so reading it from the
     * production defaults made these mechanics tests depend on a tuning dial. The dial moved on
     * 2026-10-23 — {@code 2026-10-23-residue-sizing-re-derivation.md} halved it from 512 to 256 —
     * and four of these tests failed for reasons that had nothing to do with selection: their
     * candidates simply stopped being neighbours. The sizing decision is pinned in exactly one
     * place, {@code AnchorParametersTest.defaultsMatchTheSizingDecision}, and this is not it.
     */
    private static final AnchorParameters P = new AnchorParameters(128, 0.35, 512, 128, 8, 8, 8);

    private static final long SEED = 0x5EEDL;

    /**
     * The floor is per element now, so these tests must name one. 10 is the production Fire floor,
     * derived from the 0-against-44 separation measured at a live lava site; it keeps the small
     * fixture strengths below meaningful.
     */
    private static final ElementGrounding WATER =
            new ElementGrounding.Physical("water", Set.of("Water_Source"), Set.of("Shores"), 10 * 8 * 8 * 8);

    private static Anchor at(double x, double z, int strength) {
        return new Anchor("water", new WorldPoint3d(x, 64, z), strength, 1000);
    }

    @Test
    void theStrongestInANeighbourhoodWins() {
        List<Anchor> selected =
                AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 100), at(100, 0, 500), at(200, 0, 300)));
        assertEquals(1, selected.size());
        assertEquals(500, selected.get(0).strength());
    }

    @Test
    void candidatesBeyondTheSuppressionRadiusDoNotCompete() {
        // 600 blocks apart, suppression radius 512: both survive.
        List<Anchor> selected = AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 100), at(600, 0, 500)));
        assertEquals(2, selected.size());
    }

    @Test
    void candidatesExactlyAtTheSuppressionRadiusDoCompete() {
        // The boundary is where an off-by-one lives, so it gets its own case.
        List<Anchor> selected =
                AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 100), at(P.suppressionRadius(), 0, 500)));
        assertEquals(1, selected.size());
        assertEquals(500, selected.get(0).strength());
    }

    @Test
    void theFloorRemovesTraceAmounts() {
        // minimumStrengthFraction 0.01 of 1000 samples = 10. Nine is below the floor.
        assertTrue(AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 9))).isEmpty());
        assertEquals(
                1, AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 10))).size());
    }

    @Test
    void tiesAreBrokenByHashNotByOrder() {
        List<Anchor> forward = AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 500), at(100, 0, 500)));
        List<Anchor> reversed = AnchorSelector.select(SEED, P, WATER, List.of(at(100, 0, 500), at(0, 0, 500)));
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
                at(0, 0, 900),
                at(100, 0, 900),
                at(200, 0, 900),
                at(0, 100, 900),
                at(100, 100, 900),
                at(200, 100, 900),
                at(0, 200, 900),
                at(100, 200, 900),
                at(200, 200, 900));
        assertEquals(1, AnchorSelector.select(SEED, P, WATER, uniform).size());
    }

    @Test
    void suppressionIsHorizontalSoElevationCannotSeparateTwoAnchors() {
        Anchor deep = new Anchor("water", new WorldPoint3d(0, 10, 0), 100, 1000);
        Anchor high = new Anchor("water", new WorldPoint3d(0, 300, 0), 500, 1000);
        List<Anchor> selected = AnchorSelector.select(SEED, P, WATER, List.of(deep, high));
        assertEquals(1, selected.size());
        assertEquals(500, selected.get(0).strength());
    }

    @Test
    void theHashWinnerIsNotTheHighestCoordinate() {
        // Added after mutation testing: removing the hash tie-break left the coordinate fallback,
        // which also resolves ties, so the uniform-ocean count test still passed. The hash's job is
        // not the COUNT but an unbiased spread — coordinate ordering would clump every
        // neighbourhood's winner toward its high-x corner.
        //
        // At seed 0x5EED, hash(0,0) > hash(300,0), so the lower-x candidate must win. A selector
        // that fell back to coordinates would pick (300,0) instead.
        List<Anchor> selected = AnchorSelector.select(SEED, P, WATER, List.of(at(0, 0, 900), at(300, 0, 900)));
        assertEquals(1, selected.size());
        assertEquals(0.0, selected.get(0).position().x(), 1e-9, "the hash, not the larger coordinate, must decide");
    }

    @Test
    void elevationCannotPushTwoAnchorsApartBeyondTheRadius() {
        // Added after mutation testing: the previous fixture put both anchors in one column
        // (y=10 and y=300), so their 3D distance was 290 — still inside the 512 radius, and they
        // competed whichever distance was used. Here horizontal is 450 but 3D is 551.6, so a
        // selector using 3D distance would let BOTH survive.
        Anchor low = new Anchor("water", new WorldPoint3d(0, 0, 0), 100, 1000);
        Anchor high = new Anchor("water", new WorldPoint3d(450, 319, 0), 500, 1000);
        List<Anchor> selected = AnchorSelector.select(SEED, P, WATER, List.of(low, high));
        assertEquals(1, selected.size(), "suppression is horizontal; 319 blocks of elevation must not separate them");
        assertEquals(500, selected.get(0).strength());
    }

    @Test
    void selectionIsDeterministicAcrossRuns() {
        List<Anchor> uniform = List.of(at(0, 0, 900), at(100, 0, 900), at(200, 0, 900));
        assertEquals(AnchorSelector.select(SEED, P, WATER, uniform), AnchorSelector.select(SEED, P, WATER, uniform));
    }

    @Test
    void theFloorIsAnAbsoluteAmountSoASparseElementCanStillQualify() {
        // Measured 2026-10-23 in a lava biome: the richest candidate in a 1km region held 261 lava
        // samples of a 43,560-sample window -- 0.6%, against a 1% fraction floor it could never
        // clear. Lava is riverine and the window is areal, so a share-based floor asks "does this
        // element dominate its neighbourhood", which for lava has no yes anywhere in the world.
        // The design's own words are "a lake, an ocean, a caldera -- not a 2x2 puddle", and that is
        // an absolute amount.
        ElementGrounding fire = withFloorAtStep8(10);
        Anchor river = new Anchor("fire", new WorldPoint3d(0, 143, 0), 261, 43_560);
        assertEquals(0.006, river.strengthFraction(), 0.001, "well under any sane fraction floor");
        assertEquals(List.of(river), AnchorSelector.select(SEED, P, fire, List.of(river)));
    }

    @Test
    void anElementThatIsAbsentIsStillExcluded() {
        // The other half: the floor must be able to say no. Separation at the lava site was 0 samples
        // against 44 for the weakest real river, so any threshold in that range works -- this pins
        // that the chosen one rejects nothing-at-all rather than admitting everything.
        ElementGrounding fire = withFloorAtStep8(10);
        Anchor trace = new Anchor("fire", new WorldPoint3d(0, 143, 0), 9, 43_560);
        assertEquals(List.of(), AnchorSelector.select(SEED, P, fire, List.of(trace)));
    }

    @Test
    void theFloorDoesNotScaleWithWindowSizeTheWayAFractionDoes() {
        // A count floor is independent of the denominator, which is the whole point: the same body
        // of lava measured in a wider window would fail a fraction floor purely because the window
        // grew. Same strength, 10x the samples, same verdict.
        ElementGrounding fire = withFloorAtStep8(10);
        Anchor narrow = new Anchor("fire", new WorldPoint3d(0, 143, 0), 50, 43_560);
        Anchor wide = new Anchor("fire", new WorldPoint3d(0, 143, 0), 50, 435_600);
        assertEquals(1, AnchorSelector.select(SEED, P, fire, List.of(narrow)).size());
        assertEquals(1, AnchorSelector.select(SEED, P, fire, List.of(wide)).size());
    }

    @Test
    void theFloorIsInvariantUnderSampleStepBecauseItIsAVolume() {
        // The defect this closes. The floor used to be a raw count of satisfying samples, and one
        // sample stands for step³ blocks -- so the SAME body of lava yields 8x the samples at step 4
        // that it does at step 8, and a count floor silently became 8x looser. No error, no failing
        // test. It was a live trap rather than a theoretical one: the queued anisotropic-sampling
        // proposal (coarse x/z, step 1 in y) raises a window from 43,560 samples to 348,480, which
        // is exactly that 8x, and it would have let Fire site on an eighth of the lava its floor was
        // measured against.
        ElementGrounding fire = withFloorAtStep8(10); // 5,120 blocks
        AnchorParameters step8 = withStep(8);
        AnchorParameters step4 = withStep(4);

        assertEquals(10, fire.minimumStrengthSamples(8));
        assertEquals(80, fire.minimumStrengthSamples(4), "8x the samples describe the same volume");

        // Half the floor -- 2,560 blocks -- must be rejected at BOTH steps. Under the old raw count
        // of 10 this was ACCEPTED at step 4, because the same body reads as 40 samples there.
        Anchor halfAtStep8 = new Anchor("fire", new WorldPoint3d(0, 143, 0), 5, 43_560);
        Anchor halfAtStep4 = new Anchor("fire", new WorldPoint3d(0, 143, 0), 40, 348_480);
        assertEquals(List.of(), AnchorSelector.select(SEED, step8, fire, List.of(halfAtStep8)));
        assertEquals(List.of(), AnchorSelector.select(SEED, step4, fire, List.of(halfAtStep4)));

        // And the same body exactly AT the floor qualifies at both.
        Anchor atStep8 = new Anchor("fire", new WorldPoint3d(0, 143, 0), 10, 43_560);
        Anchor atStep4 = new Anchor("fire", new WorldPoint3d(0, 143, 0), 80, 348_480);
        assertEquals(
                1, AnchorSelector.select(SEED, step8, fire, List.of(atStep8)).size());
        assertEquals(
                1, AnchorSelector.select(SEED, step4, fire, List.of(atStep4)).size());
    }

    @Test
    void theProductionFloorsReproduceTheirHistoricalCountsAtTheDefaultStep() {
        // The conversion to volumes must be behaviour-preserving at the current parameters, which is
        // what made it safe to take on its own rather than alongside a tuning change. These two are
        // the values in HytaleDerivationRules, restated here because mod/hytale does not compile on
        // CI -- so without this the guarantee would be untested anywhere a runner can see.
        assertEquals(10, withFloorAtStep8(10).minimumStrengthSamples(8), "Fire's measured floor");
        assertEquals(436, withFloorAtStep8(436).minimumStrengthSamples(8), "the abundant elements' floor");
    }

    @Test
    void theSampleCountRoundsUpSoTruncationCannotWeakenTheFloor() {
        // ceilDiv, not integer division: the comparison this stands for is strength * step³ >= volume,
        // and rounding down would admit a candidate up to one sample short -- 512 blocks at step 8.
        ElementGrounding fire = withFloorVolume(5_121);
        assertEquals(11, fire.minimumStrengthSamples(8), "5,121 blocks does not fit in 10 samples of 512");
    }

    /**
     * A Fire grounding whose floor is the given sample count <em>at the default step of 8</em>.
     *
     * <p>The floor is a volume now, but these fixtures are written in samples because that is the
     * unit the live measurements they cite were recorded in. Converting here keeps the fixtures
     * readable against their own evidence without the production type carrying a step-dependent
     * number.
     */
    private static ElementGrounding withFloorAtStep8(int samples) {
        return withFloorVolume(samples * 8 * 8 * 8);
    }

    private static ElementGrounding withFloorVolume(int blocks) {
        return new ElementGrounding.Physical("fire", Set.of("Lava_Source"), Set.of("Volcanoes"), blocks);
    }

    private static AnchorParameters withStep(int sampleStep) {
        return new AnchorParameters(128, 0.35, 512, 128, sampleStep, 8, 8);
    }
}

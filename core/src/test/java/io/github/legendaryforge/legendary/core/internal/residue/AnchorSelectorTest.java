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
        List<Anchor> selected =
                AnchorSelector.select(SEED, P, List.of(at(0, 0, 100), at(100, 0, 500), at(200, 0, 300)));
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
    void candidatesExactlyAtTheSuppressionRadiusDoCompete() {
        // The boundary is where an off-by-one lives, so it gets its own case.
        List<Anchor> selected = AnchorSelector.select(SEED, P, List.of(at(0, 0, 100), at(512, 0, 500)));
        assertEquals(1, selected.size());
        assertEquals(500, selected.get(0).strength());
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
                at(0, 0, 900),
                at(100, 0, 900),
                at(200, 0, 900),
                at(0, 100, 900),
                at(100, 100, 900),
                at(200, 100, 900),
                at(0, 200, 900),
                at(100, 200, 900),
                at(200, 200, 900));
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

    @Test
    void theHashWinnerIsNotTheHighestCoordinate() {
        // Added after mutation testing: removing the hash tie-break left the coordinate fallback,
        // which also resolves ties, so the uniform-ocean count test still passed. The hash's job is
        // not the COUNT but an unbiased spread — coordinate ordering would clump every
        // neighbourhood's winner toward its high-x corner.
        //
        // At seed 0x5EED, hash(0,0) > hash(300,0), so the lower-x candidate must win. A selector
        // that fell back to coordinates would pick (300,0) instead.
        List<Anchor> selected = AnchorSelector.select(SEED, P, List.of(at(0, 0, 900), at(300, 0, 900)));
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
        List<Anchor> selected = AnchorSelector.select(SEED, P, List.of(low, high));
        assertEquals(1, selected.size(), "suppression is horizontal; 319 blocks of elevation must not separate them");
        assertEquals(500, selected.get(0).strength());
    }

    @Test
    void selectionIsDeterministicAcrossRuns() {
        List<Anchor> uniform = List.of(at(0, 0, 900), at(100, 0, 900), at(200, 0, 900));
        assertEquals(AnchorSelector.select(SEED, P, uniform), AnchorSelector.select(SEED, P, uniform));
    }
}

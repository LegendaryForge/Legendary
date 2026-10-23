package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnchorParametersTest {

    @Test
    void defaultsMatchTheSizingDecision() {
        AnchorParameters p = AnchorParameters.defaults();
        assertEquals(512, p.suppressionRadius());
        assertEquals(128, p.measurementRadius());
        assertEquals(8, p.sampleStep());
        assertEquals(8, p.influenceRadius());
        assertEquals(8, p.crossingTolerance());
    }

    @Test
    void crossingToleranceMayNotExceedInfluenceRadius() {
        AnchorParameters p = AnchorParameters.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnchorParameters(
                        p.cellSize(),
                        p.jitterFraction(),
                        p.suppressionRadius(),
                        p.measurementRadius(),
                        p.sampleStep(),
                        p.minimumStrengthFraction(),
                        8,
                        9));
    }

    @Test
    void cellSizeMayNotExceedSuppressionRadius() {
        AnchorParameters p = AnchorParameters.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnchorParameters(
                        1024,
                        p.jitterFraction(),
                        512,
                        p.measurementRadius(),
                        p.sampleStep(),
                        p.minimumStrengthFraction(),
                        p.influenceRadius(),
                        p.crossingTolerance()));
    }

    @Test
    void floorMustBeAFraction() {
        AnchorParameters p = AnchorParameters.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new AnchorParameters(
                        p.cellSize(),
                        p.jitterFraction(),
                        p.suppressionRadius(),
                        p.measurementRadius(),
                        p.sampleStep(),
                        1.5,
                        p.influenceRadius(),
                        p.crossingTolerance()));
    }

    @Test
    void worldHeightIsThreeHundredAndTwenty() {
        assertEquals(320, AnchorParameters.defaults().worldHeight());
    }
}

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

    @Test
    void windowSampleCountIsTheFullyLoadedDenominator() {
        // radius 128 at step 8 -> 33 positions per horizontal axis; y 0..319 at step 8 -> 40 levels.
        assertEquals(33 * 33 * 40, AnchorParameters.defaults().windowSampleCount());
    }

    @Test
    void windowSampleCountTruncatesRatherThanRoundingUp() {
        // The scan stops at the last position <= radius, so a radius that is not a whole number of
        // steps contributes no extra position. 130/8 yields the same 33 as 128/8 -- which matters
        // because this figure is what a probe compares samplesTaken against to decide "fully
        // loaded", and rounding up would make every full window look partial.
        assertEquals(33 * 33 * 40, withWindow(130, 8).windowSampleCount());
    }

    @Test
    void aZeroRadiusWindowIsOneColumn() {
        assertEquals(40, withWindow(0, 8).windowSampleCount());
    }

    @Test
    void aWindowTooLargeToCountIsRejectedAtConstruction() {
        // samplesTaken is an int, so a window with more positions than an int can hold could never
        // report itself fully loaded. Rejected here rather than overflowing silently later.
        assertThrows(IllegalArgumentException.class, () -> withWindow(Integer.MAX_VALUE, 1));
    }

    private static AnchorParameters withWindow(int measurementRadius, int sampleStep) {
        AnchorParameters p = AnchorParameters.defaults();
        return new AnchorParameters(
                p.cellSize(),
                p.jitterFraction(),
                p.suppressionRadius(),
                measurementRadius,
                sampleStep,
                p.minimumStrengthFraction(),
                p.influenceRadius(),
                p.crossingTolerance());
    }
}

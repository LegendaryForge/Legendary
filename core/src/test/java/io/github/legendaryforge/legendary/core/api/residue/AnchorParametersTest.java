package io.github.legendaryforge.legendary.core.api.residue;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnchorParametersTest {

    @Test
    void defaultsMatchTheSizingDecision() {
        // 2026-10-23-residue-sizing-re-derivation.md. cellSize is asserted alongside the radius
        // because the two are coupled: the ratio between them fixes saturation and the competitor
        // count, so halving one without the other changes the network's character rather than its
        // scale. Asserting only the radius would let that pair drift apart silently.
        AnchorParameters p = AnchorParameters.defaults();
        assertEquals(256, p.suppressionRadius());
        assertEquals(128, p.cellSize());
        assertEquals(2, p.suppressionRadius() / p.cellSize(), "R/cellSize = 2 is what keeps selection saturated");
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
                        p.influenceRadius(),
                        p.crossingTolerance()));
    }

    @Test
    void theStrengthFloorIsNotAParameterHere() {
        // It moved to ElementGrounding.minimumStrengthVolume() on 2026-10-23. Asserted rather than
        // merely deleted: a reader who comes looking for a floor on this record should find a
        // statement that it is deliberately absent, not silence.
        for (java.lang.reflect.RecordComponent component : AnchorParameters.class.getRecordComponents()) {
            assertFalse(
                    component.getName().toLowerCase(java.util.Locale.ROOT).contains("strength"),
                    "AnchorParameters should carry no strength floor, found " + component.getName());
        }
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
                p.influenceRadius(),
                p.crossingTolerance());
    }
}

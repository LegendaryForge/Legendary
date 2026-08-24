package io.github.legendaryforge.legendary.quests.stormseeker.trial.flowing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the per-player motion tracker.
 *
 * <p>This logic previously lived in a private nested class inside
 * {@code mod/hytale}'s HytaleStormseekerHost, where it could not be tested on any machine
 * without the game jar even though it touches no platform type. It was the only real
 * computation in that module.
 */
final class PositionMotionTrackerTest {

    @Test
    void firstSampleSeedsAndReportsNoMotion() {
        var tracker = new PositionMotionTracker(0.01);

        MotionSample first = tracker.accept(10, 20, 30);

        // No previous position exists, so a delta would be meaningless. Reporting the
        // distance from the origin here would make every player "moving" on connect.
        assertEquals(0.0, first.dx());
        assertEquals(0.0, first.dy());
        assertEquals(0.0, first.dz());
        assertFalse(first.moving());
    }

    @Test
    void secondSampleReportsTheDeltaFromTheFirst() {
        var tracker = new PositionMotionTracker(0.01);
        tracker.accept(10, 20, 30);

        MotionSample moved = tracker.accept(13, 24, 30);

        assertEquals(3.0, moved.dx());
        assertEquals(4.0, moved.dy());
        assertEquals(0.0, moved.dz());
        assertTrue(moved.moving());
        assertEquals(5.0, moved.distance(), 1e-9);
    }

    @Test
    void movementBelowTheThresholdIsNotMoving() {
        var tracker = new PositionMotionTracker(0.01);
        tracker.accept(0, 0, 0);

        MotionSample jitter = tracker.accept(0.001, 0.001, 0.001);

        assertFalse(jitter.moving(), "sub-threshold drift must not read as movement");
        assertNotEquals(0.0, jitter.dx(), "the delta is still reported, only `moving` is gated");
    }

    @Test
    void thresholdIsExclusive() {
        var tracker = new PositionMotionTracker(1.0);
        tracker.accept(0, 0, 0);

        // Exactly at the threshold: the guard is `> threshold`, so this is NOT moving.
        // Pinned because an off-by-one-comparison here is invisible in play and would
        // silently change trial evaluation.
        assertFalse(tracker.accept(1.0, 0, 0).moving());
    }

    @Test
    void thresholdIsCrossedJustAbove() {
        var tracker = new PositionMotionTracker(1.0);
        tracker.accept(0, 0, 0);

        assertTrue(tracker.accept(1.001, 0, 0).moving());
    }

    @Test
    void successiveSamplesAreRelativeToTheImmediatelyPreviousPosition() {
        var tracker = new PositionMotionTracker(0.01);
        tracker.accept(0, 0, 0);
        tracker.accept(5, 0, 0);

        MotionSample third = tracker.accept(7, 0, 0);

        assertEquals(2.0, third.dx(), "delta must be from the previous sample, not the origin");
    }

    @Test
    void standingStillReportsZeroDeltaAndNotMoving() {
        var tracker = new PositionMotionTracker(0.01);
        tracker.accept(4, 4, 4);

        MotionSample still = tracker.accept(4, 4, 4);

        assertEquals(0.0, still.distance(), 1e-9);
        assertFalse(still.moving());
    }

    @Test
    void latestReturnsTheMostRecentSampleWithoutAdvancing() {
        var tracker = new PositionMotionTracker(0.01);
        tracker.accept(0, 0, 0);
        tracker.accept(2, 0, 0);

        assertEquals(2.0, tracker.latest().dx());
        assertEquals(2.0, tracker.latest().dx(), "latest() must not mutate tracker state");
    }
}

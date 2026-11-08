package io.github.legendaryforge.legendary.core.internal.residue;

import static org.junit.jupiter.api.Assertions.*;

import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ColumnMeasurement;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Wind's grounding: air that terrain has funnelled, rather than air that happens to be there.
 *
 * <p><strong>Why this replaces {@code Occupancy.OPEN} for Wind.</strong> {@code OPEN} counts open
 * space over the whole world column, which its own javadoc records as reading "strongest over deep
 * water" — so wind anchors favoured ocean floors and lowlands, the inverse of where wind reads. A
 * constriction rule puts them at valley necks, saddles and passes instead.
 *
 * <p>Three clauses, and each has a test here because each is load-bearing. A sample point counts
 * when it is <strong>open air</strong>, <em>and</em> its column is <strong>constricted</strong>,
 * <em>and</em> it lies <strong>below the wall top</strong> of that constriction. Drop the third and
 * a deep valley gets scored by how much sky sits over it, which is the ocean-floor defect wearing a
 * different hat.
 *
 * <p>Terrain is a height function, so these tests build no world and the arithmetic is exact.
 */
class ChannelledGroundingTest {

    /**
     * A small window on purpose, so the expected count is arithmetic rather than a guess.
     *
     * <p>Radius 16 at step 8 samples {@code dx, dz} in {@code -16,-8,0,8,16} — 25 columns — and
     * {@code y} in {@code 0,8,..,312}, 40 levels.
     */
    private static final AnchorParameters P = new AnchorParameters(128, 0.35, 256, 16, 8, 8, 8);

    private static final int RING = 8;

    private static final int RISE = 16;

    /** Ground outside the trench. */
    private static final int PLATEAU = 100;

    /** Ground inside the trench, so its walls stand 30 blocks proud. */
    private static final int FLOOR = 70;

    /** Half-width 3, so at step 8 exactly one sampled column — {@code x = 0} — lies inside. */
    private static final int TRENCH_HALF_WIDTH = 3;

    private static final ElementGrounding WIND =
            new ElementGrounding.Channelled("wind", Set.of("Env_"), 0, 15, RING, RISE, 1);

    /** A north-south trench: constricted across x, open along z. */
    private static final IntBinaryOperator TRENCH = (x, z) -> Math.abs(x) <= TRENCH_HALF_WIDTH ? FLOOR : PLATEAU;

    private static final IntBinaryOperator FLAT = (x, z) -> PLATEAU;

    /**
     * Terrain as a height function: solid at or below the surface, air above, no fluid anywhere.
     *
     * <p>Deliberately fluid-free. The point of this grounding is that it needs only heights, which
     * is what makes Wind measurable from the generator for the first time — so a fixture that
     * supplied fluid would be testing a capability the production headless path does not have.
     */
    private record Heights(IntBinaryOperator surface) implements TerrainSample {
        @Override
        public boolean isLoaded(int x, int y, int z) {
            return y >= 0 && y < 320;
        }

        @Override
        public boolean isSolidAt(int x, int y, int z) {
            return isLoaded(x, y, z) && y <= surface.applyAsInt(x, z);
        }

        @Override
        public OptionalInt surfaceHeightAt(int x, int z) {
            return OptionalInt.of(surface.applyAsInt(x, z));
        }

        @Override
        public Optional<String> fluidAt(int x, int y, int z) {
            return Optional.empty();
        }

        @Override
        public Optional<String> environmentAt(int x, int y, int z) {
            return Optional.of("Env_Synthetic");
        }

        @Override
        public int skyLightAt(int x, int y, int z) {
            return 15;
        }

        @Override
        public OptionalDouble stormProbabilityAt(int x, int y, int z) {
            return OptionalDouble.empty();
        }
    }

    /**
     * The whole grounding in one exact count.
     *
     * <p>At step 8 the only sampled column inside the trench is {@code x = 0}, for all five sampled
     * {@code z}. Its floor is 70 and its wall top is 100, so the sampled air levels below the wall
     * top are 72, 80, 88 and 96 — four of them. Every other column is unconstricted: for
     * {@code x = ±8} one ring probe lands in the trench and the wall is negative, and for
     * {@code x = ±16} both probes stand on the plateau and the wall is zero.
     *
     * <p>So the strength is {@code 5 x 4 = 20}, and no other number is consistent with the rule.
     */
    @Test
    void strengthCountsOnlyAirInsideTheChannelBelowItsWallTop() {
        ColumnMeasurement measurement = VolumeStrength.measure(new Heights(TRENCH), P, WIND, new WorldPoint2d(0, 0));

        assertEquals(20, measurement.strength());
    }

    /**
     * Flat ground scores nothing, however much open sky stands over it.
     *
     * <p>This is the defect being replaced, stated as a test. Under {@code Occupancy.OPEN} a flat
     * low plain is the <em>best</em> wind site in the world, because its column holds the most air.
     * Under constriction it is not a wind site at all.
     */
    @Test
    void flatGroundScoresNothing() {
        ColumnMeasurement measurement = VolumeStrength.measure(new Heights(FLAT), P, WIND, new WorldPoint2d(0, 0));

        assertEquals(0, measurement.strength(), "no axis is walled, so no column is constricted");
    }

    /**
     * A bowl scores nothing: enclosed on every side is shelter, not a channel.
     *
     * <p>The distinction is the one {@code /windchannels} measured as CHANNEL against BOWL, and it is
     * what makes this grounding about <em>flow</em> rather than about enclosure. A pit holds still
     * air; wind needs somewhere to come from and somewhere to go.
     */
    @Test
    void aBowlScoresNothing() {
        IntBinaryOperator pit =
                (x, z) -> Math.abs(x) <= TRENCH_HALF_WIDTH && Math.abs(z) <= TRENCH_HALF_WIDTH ? FLOOR : PLATEAU;

        ColumnMeasurement measurement = VolumeStrength.measure(new Heights(pit), P, WIND, new WorldPoint2d(0, 0));

        assertEquals(0, measurement.strength(), "walled on all four axes, so nothing flows through");
    }
}

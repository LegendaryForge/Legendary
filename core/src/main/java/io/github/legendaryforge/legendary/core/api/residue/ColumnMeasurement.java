package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Objects;
import java.util.Optional;

/**
 * What one candidate's window contained for one element — including when it contained nothing.
 *
 * <p><strong>This type exists because returning {@code Optional<Anchor>} lost the denominator.</strong>
 * An element whose predicate matched nothing produced an empty result, so a caller could not tell
 * "terrain was read and the element is absent" from "nothing was loaded, so nothing was read". Those
 * are different answers, and keeping them apart is the whole reason {@link TerrainSample#isLoaded}
 * exists.
 *
 * <p>Found by reading a live probe's own output on 2026-10-22: {@code /residueanchors} reported Wind
 * with {@code sampled=0} while Fire, Water and Earth each sampled ~1.2M at the same candidates — and
 * the probe's printed guidance read that zero as "nothing loaded". The instrument gave the wrong
 * reading for exactly the case it was built to distinguish, and only cross-referencing another row
 * disambiguated it.
 *
 * @param elementId the element measured, opaque to {@code :core}
 * @param strength satisfying sample points found
 * @param samplesTaken loaded sample points examined — carried even when {@code strength} is 0
 * @param modalLevel the y with the most satisfying samples, or {@code -1} when {@code strength} is 0
 */
public record ColumnMeasurement(String elementId, int strength, int samplesTaken, int modalLevel) {

    public ColumnMeasurement {
        Objects.requireNonNull(elementId, "elementId");
        if (elementId.isBlank()) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
        if (strength < 0 || samplesTaken < 0) {
            throw new IllegalArgumentException(
                    "counts must be non-negative, got strength " + strength + " samplesTaken " + samplesTaken);
        }
        if (strength > samplesTaken) {
            throw new IllegalArgumentException(
                    "strength " + strength + " exceeds samplesTaken " + samplesTaken + ", which cannot happen");
        }
        if (strength == 0 && modalLevel != -1) {
            throw new IllegalArgumentException("a zero-strength measurement has no modal level, got " + modalLevel);
        }
        if (strength > 0 && modalLevel < 0) {
            throw new IllegalArgumentException("a non-zero measurement needs a modal level, got " + modalLevel);
        }
    }

    /** Whether any sample point was examined at all. {@code false} means nothing was loaded. */
    public boolean anythingRead() {
        return samplesTaken > 0;
    }

    /** Satisfying fraction of the window, or 0 when nothing was sampled. */
    public double strengthFraction() {
        return samplesTaken == 0 ? 0.0 : strength / (double) samplesTaken;
    }

    /**
     * This measurement as a candidate anchor, or empty when the element was absent.
     *
     * <p>Empty here means "not a candidate", which is a different claim from the empty result this
     * type replaced: the measurement survives either way, so a caller can still report how much
     * terrain it read.
     */
    public Optional<Anchor> asCandidate(double x, double z) {
        if (strength == 0) {
            return Optional.empty();
        }
        return Optional.of(new Anchor(elementId, new WorldPoint3d(x, modalLevel, z), strength, samplesTaken));
    }
}

package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.id.ResourceId;
import io.github.legendaryforge.legendary.core.api.residue.CurrentParameters;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The arms of one element's current, as polylines radiating from the Grand Convergence.
 *
 * <p>Each arm is a correlated random walk: the heading persists between steps and jitters by at
 * most {@link CurrentParameters#headingJitter()} radians. Persistence is what makes an arm snake
 * rather than scribble, and it is why arms wander back across themselves — those crossings are the
 * nexuses.
 */
final class CurrentGeometry {

    private static final long DOMAIN = 0x41726D73L; // "Arms"
    private static final long ELEMENT_DOMAIN = 0x456C656DL; // "Elem"

    private final WorldPoint2d convergence;
    private final List<List<WorldPoint2d>> arms;
    private final List<WorldPoint2d> crossings;

    CurrentGeometry(long worldSeed, ResourceId elementId, CurrentParameters parameters) {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(parameters, "parameters");

        // Deliberately the raw world seed: every element must agree on the shared anchor, which is
        // why GrandConvergence carries an explicit prohibition against a per-element overload.
        this.convergence = GrandConvergence.locate(worldSeed);

        // Everything downstream of the anchor is per-element, so two elements cannot coincide by
        // construction rather than by a convention each caller has to remember.
        long elementSeed = ResidueRandom.mix(worldSeed, ELEMENT_DOMAIN, ResidueRandom.stableHash(elementId.toString()));
        double headingOffset = ResidueRandom.unit(elementSeed, ELEMENT_DOMAIN, 0L) * 2.0 * Math.PI;

        List<List<WorldPoint2d>> built = new ArrayList<>(parameters.armCount());
        for (int arm = 0; arm < parameters.armCount(); arm++) {
            built.add(Collections.unmodifiableList(buildArm(elementSeed, headingOffset, parameters, arm)));
        }
        this.arms = Collections.unmodifiableList(built);
        this.crossings = Collections.unmodifiableList(findCrossings());
    }

    /**
     * Every proper self-crossing of the network.
     *
     * <p>Eager, not memoised. The crossing set is a pure function of the seed, element and
     * parameters — it does not depend on any query — and {@code densityAt} now needs it, so every
     * consumer pays for it regardless. Laziness would buy nothing except a thread-safety problem on
     * a multithreaded server. Construction is O(segments squared).
     */
    private List<WorldPoint2d> findCrossings() {
        List<WorldPoint2d> flat = new ArrayList<>();
        for (List<WorldPoint2d> arm : arms) {
            for (int i = 1; i < arm.size(); i++) {
                flat.add(arm.get(i - 1));
                flat.add(arm.get(i));
            }
        }
        List<WorldPoint2d> found = new ArrayList<>();
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            for (int j = i + 2; j + 1 < flat.size(); j += 2) {
                WorldPoint2d hit = SegmentMath.intersection(flat.get(i), flat.get(i + 1), flat.get(j), flat.get(j + 1));
                if (hit != null) {
                    found.add(hit);
                }
            }
        }
        return found;
    }

    /** Proper self-crossings of the network — the nexuses. Computed once, in the constructor. */
    List<WorldPoint2d> crossings() {
        return crossings;
    }

    private List<WorldPoint2d> buildArm(
            long elementSeed, double headingOffset, CurrentParameters parameters, int armIndex) {
        List<WorldPoint2d> points = new ArrayList<>(parameters.stepsPerArm() + 1);
        points.add(convergence);

        double heading = headingOffset + 2.0 * Math.PI * armIndex / parameters.armCount();
        double x = convergence.x();
        double z = convergence.z();

        for (int step = 0; step < parameters.stepsPerArm(); step++) {
            heading += ResidueRandom.signed(elementSeed, DOMAIN + armIndex, step) * parameters.headingJitter();
            x += Math.cos(heading) * parameters.stepLength();
            z += Math.sin(heading) * parameters.stepLength();
            points.add(new WorldPoint2d(x, z));
        }
        return points;
    }

    WorldPoint2d convergence() {
        return convergence;
    }

    /** Arms, each an unmodifiable polyline whose element 0 is the convergence. */
    List<List<WorldPoint2d>> arms() {
        return arms;
    }
}

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

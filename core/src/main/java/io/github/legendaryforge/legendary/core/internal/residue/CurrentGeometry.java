package io.github.legendaryforge.legendary.core.internal.residue;

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

    private final WorldPoint2d convergence;
    private final List<List<WorldPoint2d>> arms;

    CurrentGeometry(long worldSeed, CurrentParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        this.convergence = GrandConvergence.locate(worldSeed);

        List<List<WorldPoint2d>> built = new ArrayList<>(parameters.armCount());
        for (int arm = 0; arm < parameters.armCount(); arm++) {
            built.add(Collections.unmodifiableList(buildArm(worldSeed, parameters, arm)));
        }
        this.arms = Collections.unmodifiableList(built);
    }

    private List<WorldPoint2d> buildArm(long worldSeed, CurrentParameters parameters, int armIndex) {
        List<WorldPoint2d> points = new ArrayList<>(parameters.stepsPerArm() + 1);
        points.add(convergence);

        double heading = 2.0 * Math.PI * armIndex / parameters.armCount();
        double x = convergence.x();
        double z = convergence.z();

        for (int step = 0; step < parameters.stepsPerArm(); step++) {
            heading += ResidueRandom.signed(worldSeed, DOMAIN + armIndex, step) * parameters.headingJitter();
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

package io.github.legendaryforge.legendary.mod.stormseeker.item;

import java.util.Objects;

/**
 * Canonical recraft eligibility rules for Stormseeker quest items.
 *
 * <p>Material requirements are intentionally not modeled here yet; this class only answers:
 * "is recraft allowed right now?" based on current possession.
 *
 * <p>Policy (canonical):
 * - Frame recraft requires full materials again (defined later) and is allowed only if missing.
 * - Stormseeker recraft requires full frame materials + final ritual materials (defined later) and is allowed only if missing.
 * - Uniqueness is enforced: one active frame and one active Stormseeker per owner.
 */
public final class StormseekerRecraftRules {

    private StormseekerRecraftRules() {}

    public static boolean canRecraftFrame(boolean hasValidOwnerBoundFrame) {
        return !hasValidOwnerBoundFrame;
    }

    public static boolean canRecraftStormseeker(boolean hasValidOwnerBoundStormseeker) {
        return !hasValidOwnerBoundStormseeker;
    }

    public static boolean allowsNewFrameInstance(boolean hasValidOwnerBoundFrame) {
        return !hasValidOwnerBoundFrame;
    }

    public static boolean allowsNewStormseekerInstance(boolean hasValidOwnerBoundStormseeker) {
        return !hasValidOwnerBoundStormseeker;
    }

    public static void requireEvaluatedPossession(Boolean hasFlag, String name) {
        Objects.requireNonNull(hasFlag, name);
    }
}

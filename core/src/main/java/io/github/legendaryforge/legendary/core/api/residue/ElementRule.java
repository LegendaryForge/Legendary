package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Objects;
import java.util.Set;

/**
 * What makes a site qualify for one element, expressed as data.
 *
 * <p>Rules are data rather than code because the first question about terrain derivation is not
 * whether it runs but whether it sites things a player would recognise — a Fire nexus at the lava
 * lake, not thirty blocks into the basalt beside it. That is a tuning question, and tuning a
 * {@code Set<String>} is cheaper than tuning a method.
 *
 * <p><strong>The ids are Hytale's, so the rules are not defined here.</strong> {@code :core} may
 * not know what {@code Fluid_Lava} is; it only knows how to match a name it was handed. The shipped
 * roster lives in {@code mod/hytale}, which is the only module allowed to name engine assets.
 *
 * @param elementId the element this rule types a site as — opaque here, deliberately: the roster is
 *     a reversible documentation decision and {@code :core} holds no enum of it
 * @param fluidIds resolved fluid ids that qualify, e.g. {@code Fluid_Lava}
 * @param environmentSubstrings matched as substrings of a resolved environment id, so
 *     {@code Caves_Volcanic} covers its {@code _T1}/{@code _T2}/{@code _T3} tiers without listing them
 * @param minSkyLight inclusive lower bound on sky light at the sampled surface, or 0 for "unconstrained"
 * @param maxSkyLight inclusive upper bound, or 15 for "unconstrained"
 */
public record ElementRule(
        String elementId, Set<String> fluidIds, Set<String> environmentSubstrings, int minSkyLight, int maxSkyLight) {

    public ElementRule {
        Objects.requireNonNull(elementId, "elementId");
        if (elementId.isBlank()) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
        fluidIds = Set.copyOf(Objects.requireNonNull(fluidIds, "fluidIds"));
        environmentSubstrings = Set.copyOf(Objects.requireNonNull(environmentSubstrings, "environmentSubstrings"));
        if (minSkyLight < 0 || maxSkyLight > 15 || minSkyLight > maxSkyLight) {
            throw new IllegalArgumentException(
                    "sky light bounds must satisfy 0 <= min <= max <= 15, got " + minSkyLight + ".." + maxSkyLight);
        }
        if (fluidIds.isEmpty() && environmentSubstrings.isEmpty()) {
            throw new IllegalArgumentException(
                    "rule for " + elementId + " matches nothing: give it a fluid or an environment. "
                            + "A sky-light range alone would qualify every column in the world.");
        }
    }

    /** Whether a resolved environment id matches any of this rule's substrings. */
    public boolean matchesEnvironment(String environmentId) {
        for (String needle : environmentSubstrings) {
            if (environmentId.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}

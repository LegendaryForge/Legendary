package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import java.util.ArrayList;
import java.util.List;

/**
 * Which measured candidates become anchors — anchor-density design §3.
 *
 * <p>Two rules, and the per-element behaviour falls out of them rather than being configured:
 *
 * <ul>
 *   <li><strong>An absolute floor.</strong> A candidate below {@code minimumStrengthFraction} of its
 *       own sample count is not an anchor at any strength ranking. This is how rarity handles
 *       itself: a region with no lava yields no Fire anchor, and nothing is tuned to say so.
 *   <li><strong>A uniform-radius local winner.</strong> A candidate is an anchor when nothing within
 *       one suppression radius beats it on {@code (strength, hash)}. This is how overabundance
 *       handles itself: in a uniform ocean every strength ties, the hash decides, and the result is
 *       one anchor per radius — the same count an equal area of varied coastline yields.
 * </ul>
 *
 * <p><strong>The hash tie-break is not a detail.</strong> Without it a tie means nothing is strictly
 * stronger, so every point in the ocean qualifies and the plateau defect returns. It is also
 * legitimate rather than a fallback: anchors are invisible, so in a region where every point is
 * identical there is no better place for one to be.
 *
 * <p><strong>Callers must supply a margin.</strong> A candidate within one suppression radius of the
 * region of interest can suppress a candidate inside it, so a caller selecting over a region must
 * pass measurements from the region expanded by {@code suppressionRadius}. Omitting the margin mints
 * spurious anchors along every edge.
 */
public final class AnchorSelector {

    private AnchorSelector() {}

    /**
     * The subset of {@code measured} that clears the floor and wins its neighbourhood, in input order.
     *
     * <p>Takes the {@code grounding} because the floor is a property of the element, not of the
     * lattice — see {@link ElementGrounding#minimumStrengthVolume()}. Callers already have it: they
     * needed it to produce {@code measured} in the first place.
     */
    public static List<Anchor> select(
            long worldSeed, AnchorParameters parameters, ElementGrounding grounding, List<Anchor> measured) {
        // The grounding states its floor as a block volume; the sample count that represents it
        // depends on sampleStep, so it is derived here rather than stored anywhere.
        int floor = grounding.minimumStrengthSamples(parameters.sampleStep());
        List<Anchor> eligible = new ArrayList<>();
        for (Anchor anchor : measured) {
            // An absolute count, so the verdict does not move when measurementRadius does.
            if (anchor.strength() >= floor) {
                eligible.add(anchor);
            }
        }

        double radius = parameters.suppressionRadius();
        List<Anchor> selected = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            Anchor anchor = eligible.get(i);
            boolean beaten = false;
            for (int j = 0; j < eligible.size(); j++) {
                // Compared by index, not by identity or equality. "Skip myself" is a position in
                // the list, and Anchor is a record: reference equality reads as a mistake to the
                // next author (Error Prone flags it), while value equality would be wrong — two
                // value-equal candidates are distinct competitors, not one entry seen twice.
                if (i == j) {
                    continue;
                }
                Anchor rival = eligible.get(j);
                if (anchor.position().horizontalDistanceTo(rival.position()) > radius) {
                    continue;
                }
                if (beats(worldSeed, rival, anchor)) {
                    beaten = true;
                    break;
                }
            }
            if (!beaten) {
                selected.add(anchor);
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Whether {@code rival} outranks {@code anchor} on {@code (strength, hash)}.
     *
     * <p>The hash is taken over the rounded position so it is a property of the place rather than of
     * a double's exact bits, which keeps the comparison stable if a candidate's coordinates are ever
     * recomputed at a different precision.
     */
    private static boolean beats(long worldSeed, Anchor rival, Anchor anchor) {
        if (rival.strength() != anchor.strength()) {
            return rival.strength() > anchor.strength();
        }
        long rivalHash = positionHash(worldSeed, rival);
        long anchorHash = positionHash(worldSeed, anchor);
        if (rivalHash != anchorHash) {
            return rivalHash > anchorHash;
        }
        // Astronomically unlikely, but a total order must be total: fall back to coordinates so the
        // outcome cannot depend on iteration order.
        if (rival.position().x() != anchor.position().x()) {
            return rival.position().x() > anchor.position().x();
        }
        return rival.position().z() > anchor.position().z();
    }

    private static long positionHash(long worldSeed, Anchor anchor) {
        long x = Math.round(anchor.position().x());
        long z = Math.round(anchor.position().z());
        return ResidueRandom.mix(worldSeed, x, z);
    }
}

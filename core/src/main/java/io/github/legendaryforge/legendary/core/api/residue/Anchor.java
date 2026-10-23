package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Objects;

/**
 * One selected anchor: a place with a decent concentration of one element.
 *
 * <p>{@code strength} is a <strong>count of sample points</strong> satisfying the element's
 * predicate, not a weighted score — anchor-density design §2, "strength is volume". {@code
 * samplesTaken} is carried alongside it so the count is interpretable: 250 means nothing without the
 * denominator, and the denominator changes with the measurement radius and sample step.
 *
 * <p><strong>Anchors are invisible.</strong> §7 removed nexus as a placed entity and §5 has players
 * read crystal density, so nothing here is a landmark a player can reach — which is why an anchor
 * needs no local justification and why a hash may legitimately decide between tied candidates.
 *
 * @param elementId the element, opaque to {@code :core}
 * @param position where the anchor sits, including its own elevation
 * @param strength satisfying sample points found in the measurement window
 * @param samplesTaken loaded sample points examined, the denominator for {@code strength}
 */
public record Anchor(String elementId, WorldPoint3d position, int strength, int samplesTaken) {

    public Anchor {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(position, "position");
        if (elementId.isBlank()) {
            throw new IllegalArgumentException("elementId must not be blank");
        }
        if (strength < 0) {
            throw new IllegalArgumentException("strength must be non-negative, got " + strength);
        }
        if (samplesTaken < 0) {
            throw new IllegalArgumentException("samplesTaken must be non-negative, got " + samplesTaken);
        }
        if (strength > samplesTaken) {
            throw new IllegalArgumentException(
                    "strength " + strength + " exceeds samplesTaken " + samplesTaken + ", which cannot happen");
        }
    }

    /** Satisfying fraction of the window, or 0 when nothing was sampled. */
    public double strengthFraction() {
        return samplesTaken == 0 ? 0.0 : strength / (double) samplesTaken;
    }
}

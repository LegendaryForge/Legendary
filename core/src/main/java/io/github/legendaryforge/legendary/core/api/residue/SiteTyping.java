package io.github.legendaryforge.legendary.core.api.residue;

import java.util.List;
import java.util.Objects;

/**
 * One element's score for one candidate site, with the signals that produced it.
 *
 * <p>{@code reasons} exists because a probe that prints only a verdict cannot be judged. The point
 * of running derivation against real terrain before implementing it is to see <em>why</em> a site
 * was typed as it was, standing on that ground — so every contributing read is recorded, not
 * summarised.
 *
 * @param elementId the element, opaque to {@code :core}
 * @param score weighted sum of matched signals; compare against
 *     {@link LatticeParameters#qualifyingScore()}
 * @param reasons human-readable signal list, in the order they were found
 */
public record SiteTyping(String elementId, double score, List<String> reasons) implements Comparable<SiteTyping> {

    public SiteTyping {
        Objects.requireNonNull(elementId, "elementId");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (!Double.isFinite(score) || score < 0) {
            throw new IllegalArgumentException("score must be finite and non-negative, got " + score);
        }
    }

    /** Descending by score, so a sorted list reads best-first. */
    @Override
    public int compareTo(SiteTyping other) {
        return Double.compare(other.score, score);
    }
}

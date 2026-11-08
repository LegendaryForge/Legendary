package io.github.legendaryforge.legendary.core.api.residue;

import java.util.Objects;

/**
 * One current, from one anchor to another.
 *
 * <p>Directed: {@code from} is downstream and {@code to} is upstream. For a
 * {@link CurrentTopology.Sprawling} forest that direction is intrinsic, because {@code to} is always
 * the stronger of the pair — which is what gives "follow the current upstream" a meaning that an
 * undirected tree cannot supply.
 *
 * <p>Carries anchors rather than positions because a consumer needs the strengths: a current's rank
 * in the network is a property of the anchors it joins, not of the line between them.
 */
public record CurrentEdge(Anchor from, Anchor to) {

    public CurrentEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }

    /** Horizontal length of this current's straight-line span, in blocks. */
    public double length() {
        return from.position().horizontalDistanceTo(to.position());
    }
}

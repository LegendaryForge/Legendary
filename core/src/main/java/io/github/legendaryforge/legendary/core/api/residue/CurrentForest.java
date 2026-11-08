package io.github.legendaryforge.legendary.core.api.residue;

import java.util.List;
import java.util.Objects;

/**
 * One element's current network over a region: its edges, and the anchors that have no upstream.
 *
 * <p><strong>A forest rather than a tree, and that is the design rather than a shortfall.</strong>
 * Anchors are derived from an area's elemental affinity, so an element's anchors are patchy — and a
 * structure forced to connect all of them would have to emit an edge across terrain where the
 * element is simply absent. So this may be disconnected, and its connectivity is then a readable
 * fact about the world rather than an artefact of the algorithm.
 *
 * @param edges every current in the region, each directed from weaker to stronger
 * @param sources anchors with no upstream — the most intense local expression of the element
 */
public record CurrentForest(List<CurrentEdge> edges, List<Anchor> sources) {

    public CurrentForest {
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
    }
}

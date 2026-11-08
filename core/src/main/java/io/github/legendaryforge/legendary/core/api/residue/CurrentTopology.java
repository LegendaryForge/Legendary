package io.github.legendaryforge.legendary.core.api.residue;

/**
 * How one element's currents connect its anchors — per-element topology design §4.
 *
 * <p>A sealed hierarchy for the same reason {@link ElementGrounding} is one: the elements do not
 * connect the same way, and a single rule shape is wrong for some of them. Which kind an element
 * gets is decided by a property its grounding already carries — <strong>whether the element can be
 * absent</strong>.
 *
 * @param reach how far a current will stretch to connect, in blocks
 */
public sealed interface CurrentTopology {

    /** How far a current will stretch to connect, in blocks. */
    int reach();

    /**
     * Each anchor links to the nearest <em>stronger</em> anchor within {@link #reach()}; an anchor
     * with nothing stronger in reach is a source.
     *
     * <p>For elements that cannot be absent, so their anchors tile the world and a connected
     * structure over all of them would have to span gaps that are not there to span.
     */
    record Sprawling(int reach) implements CurrentTopology {
        public Sprawling {
            if (reach <= 0) {
                throw new IllegalArgumentException("reach must be positive, got " + reach);
            }
        }
    }

    /**
     * Anchors are grouped by single linkage at {@link #reach()}, then a <strong>minimum spanning
     * tree</strong> is built within each group. Groups are never joined.
     *
     * <p>For elements that <em>can</em> be absent, where the grounding guarantees empty terrain
     * around each group — so the spanning tree's defect does not arise. A tree over all of a patchy
     * element's anchors would have to emit an edge bridging two groups across terrain holding none
     * of the element, which is the failure that retired the global tree.
     *
     * <p><strong>Not valid for an element that cannot be absent.</strong> Rock and open air are
     * everywhere, so single linkage over them yields one unbounded group and this degenerates into
     * exactly the structure it exists to avoid.
     */
    record Contained(int reach) implements CurrentTopology {
        public Contained {
            if (reach <= 0) {
                throw new IllegalArgumentException("reach must be positive, got " + reach);
            }
        }
    }
}

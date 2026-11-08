package io.github.legendaryforge.legendary.core.internal.residue;

import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.CurrentEdge;
import io.github.legendaryforge.legendary.core.api.residue.CurrentForest;
import io.github.legendaryforge.legendary.core.api.residue.CurrentTopology;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Builds one element's {@link CurrentForest} from its selected anchors. */
public final class Currents {

    private Currents() {}

    public static CurrentForest build(
            long worldSeed,
            AnchorParameters parameters,
            ElementGrounding grounding,
            CurrentTopology topology,
            List<Anchor> anchors) {
        // Contained relies on empty terrain separating its groups, and only a grounding that can be
        // ABSENT supplies it. Geometric cannot be absent -- rock and open air are everywhere -- so
        // single linkage over its anchors yields one unbounded group and this degenerates into the
        // global spanning tree it exists to replace. Rejected here rather than documented, because
        // the failure is invisible in the output: one enormous group still returns a valid forest.
        if (topology instanceof CurrentTopology.Contained && grounding instanceof ElementGrounding.Geometric) {
            throw new IllegalArgumentException("Contained topology requires a grounding that can be absent, and "
                    + grounding.elementId() + " is Geometric — rock and open air are everywhere, so its anchors "
                    + "form one unbounded group and nothing separates the groups Contained needs");
        }
        // Rejected rather than returning an empty forest. No two anchors are ever closer than the
        // suppression radius -- AnchorSelector.beats is a total order, so of any two candidates
        // inside it one strictly beats the other and the loser is not an anchor -- so a reach at or
        // below it yields no edge for any input anywhere. Empty is the dangerous answer: a world
        // with no currents and nothing saying why.
        if (topology.reach() <= parameters.suppressionRadius()) {
            throw new IllegalArgumentException("reach " + topology.reach()
                    + " is at or below suppressionRadius " + parameters.suppressionRadius()
                    + ", and no two anchors are ever that close, so no current could exist");
        }
        return switch (topology) {
            case CurrentTopology.Sprawling sprawling -> sprawling(worldSeed, sprawling.reach(), anchors);
            case CurrentTopology.Contained contained -> contained(worldSeed, contained.reach(), anchors);
        };
    }

    /**
     * Single-linkage groups at {@code reach}, then a minimum spanning tree inside each.
     *
     * <p>Grouping and spanning use the same threshold on purpose: a group is a set of anchors
     * reachable from one another in steps of at most {@code reach}, and the tree joining them uses
     * only those steps. So no edge can exceed {@code reach}, and the gap between groups is never
     * bridged.
     */
    private static CurrentForest contained(long worldSeed, int reach, List<Anchor> anchors) {
        List<List<Integer>> groups = singleLinkageGroups(anchors, reach);
        List<CurrentEdge> edges = new ArrayList<>();
        List<Anchor> sources = new ArrayList<>();
        for (List<Integer> group : groups) {
            // The group's strongest anchor is its source, and the tree is oriented toward it so
            // "upstream" means the same thing it means for a Sprawling forest. A minimum spanning
            // tree is undirected; the design's direction principle is what picks the root.
            int root = group.get(0);
            for (int index : group) {
                if (AnchorSelector.beats(worldSeed, anchors.get(index), anchors.get(root))) {
                    root = index;
                }
            }
            sources.add(anchors.get(root));
            edges.addAll(primTreeOrientedToRoot(anchors, group, root));
        }
        return new CurrentForest(edges, sources);
    }

    /** Groups of anchors mutually reachable in steps of at most {@code reach}. */
    private static List<List<Integer>> singleLinkageGroups(List<Anchor> anchors, int reach) {
        boolean[] seen = new boolean[anchors.size()];
        List<List<Integer>> groups = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            if (seen[i]) {
                continue;
            }
            List<Integer> group = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(i);
            seen[i] = true;
            while (!queue.isEmpty()) {
                int at = queue.poll();
                group.add(at);
                for (int j = 0; j < anchors.size(); j++) {
                    if (seen[j]) {
                        continue;
                    }
                    double distance = anchors.get(at)
                            .position()
                            .horizontalDistanceTo(anchors.get(j).position());
                    if (distance <= reach) {
                        seen[j] = true;
                        queue.add(j);
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }

    /**
     * Prim's algorithm over one group, with every edge directed along the path toward {@code root}.
     *
     * <p>Prim grown <em>from</em> the root gives the orientation for free: each anchor is attached by
     * the edge that admitted it, and that edge points at an anchor already in the tree — which is
     * one step nearer the root by construction.
     */
    private static List<CurrentEdge> primTreeOrientedToRoot(List<Anchor> anchors, List<Integer> group, int root) {
        List<CurrentEdge> edges = new ArrayList<>();
        List<Integer> inTree = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>(group);
        inTree.add(root);
        remaining.remove(Integer.valueOf(root));
        while (!remaining.isEmpty()) {
            int bestOutside = -1;
            int bestInside = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int outside : remaining) {
                for (int inside : inTree) {
                    double distance = anchors.get(outside)
                            .position()
                            .horizontalDistanceTo(anchors.get(inside).position());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestOutside = outside;
                        bestInside = inside;
                    }
                }
            }
            if (bestOutside < 0) {
                // Unreachable: single linkage built this group, so every member is within reach of
                // some other member. Asserted rather than assumed, because a silent partial tree
                // would read as a correct sparse network.
                throw new IllegalStateException("a single-linkage group had an unreachable member");
            }
            edges.add(new CurrentEdge(anchors.get(bestOutside), anchors.get(bestInside)));
            inTree.add(bestOutside);
            remaining.remove(Integer.valueOf(bestOutside));
        }
        return edges;
    }

    /** Each anchor to the nearest anchor that beats it within {@code reach}, or no edge at all. */
    private static CurrentForest sprawling(long worldSeed, int reach, List<Anchor> anchors) {
        List<CurrentEdge> edges = new ArrayList<>();
        List<Anchor> sources = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            Anchor self = anchors.get(i);
            Anchor best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int j = 0; j < anchors.size(); j++) {
                // By index, not by equality: two value-equal anchors are distinct competitors, the
                // same reason AnchorSelector compares positions in its list rather than values.
                if (i == j) {
                    continue;
                }
                Anchor other = anchors.get(j);
                if (!AnchorSelector.beats(worldSeed, other, self)) {
                    continue;
                }
                double distance = self.position().horizontalDistanceTo(other.position());
                if (distance > reach || distance >= bestDistance) {
                    continue;
                }
                bestDistance = distance;
                best = other;
            }
            if (best == null) {
                sources.add(self);
            } else {
                edges.add(new CurrentEdge(self, best));
            }
        }
        return new CurrentForest(edges, sources);
    }
}

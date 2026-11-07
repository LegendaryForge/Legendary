package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ColumnMeasurement;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.AnchorSelector;
import io.github.legendaryforge.legendary.core.internal.residue.CandidateLattice;
import io.github.legendaryforge.legendary.core.internal.residue.VolumeStrength;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3d;

/**
 * Debug command: {@code /residueanchors}
 *
 * <p>Reports how many anchors each element gets around the player and how far apart they are. This
 * is the one number the sizing decision cannot settle from a desk: whether crystal density lands
 * between "travel 500 blocks to find any" and "the landscape is riddled with them".
 *
 * <p><strong>Rows are candidates, not elements.</strong> The first version printed one row per
 * element holding that element's <em>strongest</em> candidate's satisfying fraction — so the four
 * fractions in that column came from four different windows, at four different places, and reading
 * down it invited a comparison that was never valid. It nearly produced one. Here every figure on a
 * row is measured over the same window, so comparing along a row is sound; comparing <em>down</em> a
 * column is not, and the output says so.
 *
 * <p><strong>Load state is reported per window, because a small loaded region and a scarce element
 * produce the same small number.</strong> The {@code samples} column against
 * {@link AnchorParameters#windowSampleCount()} is what separates them, and the fully-loaded tally
 * below the table is the only count that bounds how much terrain this reading actually saw.
 *
 * <p><strong>Selection runs over a margin and reports without it.</strong> A candidate within one
 * suppression radius of the reported region can suppress a candidate inside it, so candidates are
 * enumerated over the region expanded by {@code suppressionRadius} and filtered afterwards. Skipping
 * either half biases density in a different direction — reporting the margin's anchors overstates it,
 * and omitting the margin from selection mints spurious anchors on every edge.
 *
 * <p><strong>Spacing is reported, not just a count.</strong> A count over a region answers "how
 * many"; the question is "how far does a player walk", which only nearest-neighbour distance answers.
 *
 * <p>Anchored on a connected player because a headless server holds no loaded region at any offset
 * from 0 to 4096 (measured 2026-10-21), so every column would read unloaded and every element would
 * report zero for the wrong reason.
 *
 * <p>Prints {@code ANCHORS_VERDICT:} as its last line.
 */
public class ResidueAnchorsCommand extends AbstractWorldCommand {

    /** Region reported around the player, per axis. Small on purpose: each candidate is a full-column scan. */
    private static final int REGION_RADIUS = 512;

    /** Stand-in until a world-seed accessor is confirmed; the probe's figures are seed-relative. */
    private static final long WORLD_SEED = 0x5EEDL;

    public ResidueAnchorsCommand() {
        super("residueanchors", "Reports per-candidate element strength, load state, and anchor spacing");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- residue anchors ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players connected. A headless server loads no region, so every column\n")
                    .append("would read unloaded and every element would report zero for the wrong\n")
                    .append("reason. Connect a client.\n")
                    .append("ANCHORS_VERDICT: INCONCLUSIVE | no players\n");
            send(context, sb.toString());
            return;
        }

        String who = null;
        int playerX = 0;
        int playerY = 0;
        int playerZ = 0;
        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                sb.append("[").append(playerRef.getUsername()).append("] TransformComponent ABSENT\n");
                continue;
            }
            Vector3d pos = transform.getPosition();
            who = playerRef.getUsername();
            playerX = MathUtil.floor(pos.x);
            playerY = MathUtil.floor(pos.y);
            playerZ = MathUtil.floor(pos.z);
            break;
        }
        if (who == null) {
            sb.append("no player had a TransformComponent; cannot anchor\n")
                    .append("ANCHORS_VERDICT: INCONCLUSIVE | no transform\n");
            send(context, sb.toString());
            return;
        }

        AnchorParameters parameters = AnchorParameters.defaults();
        // referencePlaneY remains a v1 limitation of the adapter's surfaceHeightAt. VolumeStrength
        // never calls that method -- it walks the whole column -- so this value only reaches
        // isLoaded, and no candidate's score depends on where the player is standing.
        SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);

        // Lightning awaits its own treatment and VolumeStrength throws for it, so it is partitioned
        // out here rather than skipped inside the loop -- a measurable element and a pending one are
        // different kinds of row, and the pending one still gets printed.
        List<ElementGrounding> measurable = new ArrayList<>();
        List<ElementGrounding> awaitingTreatment = new ArrayList<>();
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if (grounding instanceof ElementGrounding.Weather) {
                awaitingTreatment.add(grounding);
            } else {
                measurable.add(grounding);
            }
        }

        int margin = parameters.suppressionRadius();
        double minX = playerX - REGION_RADIUS;
        double maxX = playerX + REGION_RADIUS;
        double minZ = playerZ - REGION_RADIUS;
        double maxZ = playerZ + REGION_RADIUS;

        List<WorldPoint2d> candidates = CandidateLattice.candidatesWithin(
                WORLD_SEED,
                parameters.cellSize(),
                parameters.jitterFraction(),
                minX - margin,
                minZ - margin,
                maxX + margin,
                maxZ + margin);

        int window = parameters.windowSampleCount();

        // DIAGNOSTIC ONLY -- fire's declared environment set, read off the production rules so it
        // cannot drift from what they say. VolumeStrength never reads Physical.environments(), so
        // this set is dead data in production today; the column below measures what it WOULD be
        // worth. Lava volume gives fire a maximum of 0.006 against a 0.01 floor even in deep lava
        // rivers, so the question is whether the environment it sits in discriminates where the
        // fluid cannot.
        Set<String> fireEnvironments = Set.of();
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if (grounding instanceof ElementGrounding.Physical physical && "fire".equals(physical.elementId())) {
                fireEnvironments = physical.corroboratingEnvironments();
            }
        }

        sb.append("anchor: ")
                .append(who)
                .append(" at ")
                .append(playerX)
                .append(',')
                .append(playerY)
                .append(',')
                .append(playerZ)
                .append('\n');
        sb.append("params: cell ")
                .append(parameters.cellSize())
                .append("b, suppression ")
                .append(parameters.suppressionRadius())
                .append("b, measure ")
                .append(parameters.measurementRadius())
                .append("b, step ")
                .append(parameters.sampleStep())
                .append('\n');
        // The floor is per element now, so it belongs in the per-element table rather than in the
        // params line -- printing one number there would imply a single floor that no longer exists.
        sb.append("region: +/-")
                .append(REGION_RADIUS)
                .append("b, margin ")
                .append(margin)
                .append("b, candidates ")
                .append(candidates.size())
                .append(", full window ")
                .append(window)
                .append(" samples\n");

        // Candidate-outer, element-inner. The nesting is the fix: it is what makes a row one window.
        List<CandidateReading> readings = new ArrayList<>();
        Map<String, List<Anchor>> candidatesByElement = new LinkedHashMap<>();
        for (ElementGrounding grounding : measurable) {
            candidatesByElement.put(grounding.elementId(), new ArrayList<>());
        }
        for (WorldPoint2d candidate : candidates) {
            boolean inRegion =
                    candidate.x() >= minX && candidate.x() <= maxX && candidate.z() >= minZ && candidate.z() <= maxZ;
            Map<String, ColumnMeasurement> byElement = new LinkedHashMap<>();
            for (ElementGrounding grounding : measurable) {
                ColumnMeasurement measurement = VolumeStrength.measure(sample, parameters, grounding, candidate);
                byElement.put(grounding.elementId(), measurement);
                measurement
                        .asCandidate(candidate.x(), candidate.z())
                        .ifPresent(candidatesByElement.get(grounding.elementId())::add);
            }
            readings.add(new CandidateReading(
                    candidate, inRegion, byElement, environmentShare(sample, parameters, candidate, fireEnvironments)));
        }

        // Selection is per element and still needs every candidate including the margin's, so it
        // runs off the lists accumulated above rather than off the rows.
        Map<String, List<Anchor>> anchorsByElement = new LinkedHashMap<>();
        Set<String> anchoredAt = new HashSet<>();
        for (ElementGrounding grounding : measurable) {
            String id = grounding.elementId();
            List<Anchor> inRegion = new ArrayList<>();
            for (Anchor anchor :
                    AnchorSelector.select(WORLD_SEED, parameters, grounding, candidatesByElement.get(id))) {
                if (anchor.position().x() >= minX
                        && anchor.position().x() <= maxX
                        && anchor.position().z() >= minZ
                        && anchor.position().z() <= maxZ) {
                    inRegion.add(anchor);
                    anchoredAt.add(
                            siteKey(id, anchor.position().x(), anchor.position().z()));
                }
            }
            anchorsByElement.put(id, inRegion);
        }

        sb.append("\nPER CANDIDATE. Every figure on a row is the SAME window, so reading ALONG a row\n")
                .append("is valid. Reading DOWN a column is not -- those are different windows at\n")
                .append("different places. Candidates whose window read nothing are counted below but\n")
                .append("not printed: they carry no comparison.\n");
        sb.append(String.format("%8s %8s %4s %6s %9s", "x", "z", "reg", "load", "samples"));
        for (ElementGrounding grounding : measurable) {
            sb.append(String.format(" %7s", grounding.elementId()));
        }
        sb.append(String.format(" %7s", "volc*"));
        sb.append("  anchored\n");

        int fullyLoaded = 0;
        int fullyLoadedInRegion = 0;
        int partlyLoaded = 0;
        int readNothing = 0;
        int disagreements = 0;
        for (CandidateReading reading : readings) {
            int samples = reading.samplesTaken();
            if (!reading.everyElementReadTheSameWindow()) {
                disagreements++;
            }
            if (samples == 0) {
                readNothing++;
                continue;
            }
            if (samples >= window) {
                fullyLoaded++;
                if (reading.inRegion()) {
                    fullyLoadedInRegion++;
                }
            } else {
                partlyLoaded++;
            }
            sb.append(String.format(
                    "%8d %8d %4s %5.0f%% %9d",
                    Math.round(reading.candidate().x()),
                    Math.round(reading.candidate().z()),
                    reading.inRegion() ? "*" : "-",
                    100.0 * samples / window,
                    samples));
            StringBuilder anchored = new StringBuilder();
            for (ElementGrounding grounding : measurable) {
                String id = grounding.elementId();
                sb.append(String.format(" %7.3f", reading.byElement().get(id).strengthFraction()));
                if (anchoredAt.contains(
                        siteKey(id, reading.candidate().x(), reading.candidate().z()))) {
                    anchored.append(anchored.length() == 0 ? "" : " ").append(id);
                }
            }
            sb.append(String.format(" %7.3f", reading.volcanicShare()));
            sb.append("  ").append(anchored.length() == 0 ? "-" : anchored).append('\n');
        }

        sb.append(String.format(
                "%nwindows: %d candidate(s) -- %d fully loaded (%d in region), %d partial, %d read nothing%n",
                readings.size(), fullyLoaded, fullyLoadedInRegion, partlyLoaded, readNothing));
        if (disagreements > 0) {
            // isLoaded does not depend on the element and neither do the scan's bounds, so every
            // element at one candidate must read the same number of samples. A disagreement means
            // the denominator is element-dependent, which is the shape of the bug that made Wind
            // report sampled=0 while three other elements read 1.2M at the same candidates.
            sb.append(disagreements)
                    .append(" candidate(s) reported DIFFERENT sample counts across elements, which cannot\n")
                    .append("happen: isLoaded is element-independent. Do not read this output's load\n")
                    .append("accounting until that is explained.\n");
        }

        int totalAnchors = 0;
        sb.append("\nPER ELEMENT. Anchors surviving the floor and suppression, inside the region.\n");
        sb.append("element     anchors  mean gap  min gap     floor  best\n");
        for (ElementGrounding grounding : measurable) {
            List<Anchor> anchors = anchorsByElement.get(grounding.elementId());
            totalAnchors += anchors.size();
            int best = 0;
            for (CandidateReading reading : readings) {
                ColumnMeasurement m = reading.byElement().get(grounding.elementId());
                if (m != null) {
                    best = Math.max(best, m.strength());
                }
            }
            sb.append(String.format(
                    "%-11s %7d %9s %8s %9d %5d%n",
                    grounding.elementId(),
                    anchors.size(),
                    gap(anchors, false),
                    gap(anchors, true),
                    grounding.minimumStrengthSamples(parameters.sampleStep()),
                    best));
        }
        for (ElementGrounding grounding : awaitingTreatment) {
            sb.append(String.format(
                    "%-11s %7s %9s %8s %9d %5s%n",
                    grounding.elementId(),
                    "-",
                    "pending",
                    "pending",
                    grounding.minimumStrengthSamples(parameters.sampleStep()),
                    "-"));
        }

        sb.append("\nvolc* is a DIAGNOSTIC, not a score: the share of the window whose environment is\n")
                .append("in fire's declared set, which VolumeStrength does not currently read. Compare it\n")
                .append("to the fire column on the SAME row. If volc* varies between candidates it can\n")
                .append("ground Fire where lava volume cannot; if it is flat across the region, every\n")
                .append("candidate ties and the hash would place Fire anchors arbitrarily inside it --\n")
                .append("abundance is not discrimination.\n");
        sb.append("\nREADING THIS. A zero frac with a large samples count is a real absence; a zero\n")
                .append("with samples 0 is an unread window, and the row is omitted for that reason.\n")
                .append("The floor is the number a frac must clear, so a frac just under it means the\n")
                .append("floor is an absolute SAMPLE COUNT per element now, printed beside each one with\n")
                .append("that element's best candidate -- so 'best' under 'floor' is exactly why an\n")
                .append("element has no anchors, with no arithmetic needed.\n")
                .append("Do NOT turn the fully-loaded count into an area and divide anchors by it. A\n")
                .append("window is ")
                .append(2 * parameters.measurementRadius() + 1)
                .append("b across and a lattice cell is ")
                .append(parameters.cellSize())
                .append("b; a loaded window says nothing\n")
                .append("about the rest of its cell, and nothing here measures loaded area. The count\n")
                .append("bounds how much terrain was seen -- it is not a denominator.\n");
        sb.append("ANCHORS_VERDICT: ")
                .append(totalAnchors > 0 ? "READ" : "EMPTY")
                .append(" | ")
                .append(totalAnchors)
                .append(" anchor(s) across ")
                .append(measurable.size())
                .append(" element(s); ")
                .append(fullyLoadedInRegion)
                .append(" fully-loaded window(s) in region; lightning pending\n");
        send(context, sb.toString());
    }

    /**
     * One candidate's window, measured for every element that has a measure.
     *
     * <p>Exists so a printed row is one window. The previous shape held one list per element, which
     * is why its strongest-candidate column compared four different places.
     */
    private record CandidateReading(
            WorldPoint2d candidate, boolean inRegion, Map<String, ColumnMeasurement> byElement, double volcanicShare) {

        /**
         * Loaded sample positions this window read — the largest any element reported.
         *
         * <p>They should all be equal; {@link #everyElementReadTheSameWindow} is what checks it, and
         * the maximum is used here so a disagreement cannot understate what was read.
         */
        int samplesTaken() {
            int most = 0;
            for (ColumnMeasurement measurement : byElement.values()) {
                most = Math.max(most, measurement.samplesTaken());
            }
            return most;
        }

        /** Whether the denominator came out element-independent, as {@code isLoaded} requires. */
        boolean everyElementReadTheSameWindow() {
            int first = -1;
            for (ColumnMeasurement measurement : byElement.values()) {
                if (first < 0) {
                    first = measurement.samplesTaken();
                } else if (measurement.samplesTaken() != first) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Share of the window whose environment matches {@code environments} — a diagnostic, not a score.
     *
     * <p>Walks the same bounds {@code VolumeStrength} does, deliberately: a share measured over a
     * different window would not be comparable to the element fractions printed beside it, and the
     * whole point of putting it on the same row is that the comparison is same-window.
     *
     * <p>Gates on environment <strong>alone</strong> — no occupancy, no sky. {@code Geometric} cannot
     * express that (its {@code Occupancy} is SOLID or OPEN, with no "either"), which is itself part of
     * the finding: if Fire is to be grounded regionally, the existing grounding types cannot say it.
     */
    private static double environmentShare(
            SectionReaderTerrainSample sample,
            AnchorParameters parameters,
            WorldPoint2d candidate,
            Set<String> environments) {
        if (environments.isEmpty()) {
            return 0;
        }
        int centreX = (int) Math.floor(candidate.x());
        int centreZ = (int) Math.floor(candidate.z());
        int radius = parameters.measurementRadius();
        int step = parameters.sampleStep();
        int top = parameters.worldHeight() - 1;
        int matched = 0;
        int sampled = 0;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                for (int y = 0; y <= top; y += step) {
                    int x = centreX + dx;
                    int z = centreZ + dz;
                    if (!sample.isLoaded(x, y, z)) {
                        continue;
                    }
                    sampled++;
                    if (sample.environmentAt(x, y, z)
                            .filter(id -> ElementGrounding.environmentMatches(id, environments))
                            .isPresent()) {
                        matched++;
                    }
                }
            }
        }
        return sampled == 0 ? 0 : matched / (double) sampled;
    }

    /** Identifies the element-at-a-place pair an anchor occupies, for marking its row. */
    private static String siteKey(String elementId, double x, double z) {
        return elementId + '@' + x + ',' + z;
    }

    /** Mean or minimum nearest-neighbour horizontal distance, or {@code "-"} below two anchors. */
    private static String gap(List<Anchor> anchors, boolean minimum) {
        if (anchors.size() < 2) {
            return "-";
        }
        double total = 0;
        double smallest = Double.MAX_VALUE;
        for (int i = 0; i < anchors.size(); i++) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < anchors.size(); j++) {
                if (i == j) {
                    continue;
                }
                nearest = Math.min(
                        nearest,
                        anchors.get(i)
                                .position()
                                .horizontalDistanceTo(anchors.get(j).position()));
            }
            total += nearest;
            smallest = Math.min(smallest, nearest);
        }
        return String.format("%.0fb", minimum ? smallest : total / anchors.size());
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

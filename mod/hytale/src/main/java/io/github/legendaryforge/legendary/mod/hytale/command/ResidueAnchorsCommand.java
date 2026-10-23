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
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.AnchorSelector;
import io.github.legendaryforge.legendary.core.internal.residue.CandidateLattice;
import io.github.legendaryforge.legendary.core.internal.residue.VolumeStrength;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3d;

/**
 * Debug command: {@code /residueanchors}
 *
 * <p>Reports how many anchors each element gets around the player and how far apart they are. This
 * is the one number the sizing decision cannot settle from a desk: whether crystal density lands
 * between "travel 500 blocks to find any" and "the landscape is riddled with them".
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
 * report zero for the wrong reason. The {@code loaded} column is what makes a zero legible.
 *
 * <p>Prints {@code ANCHORS_VERDICT:} as its last line.
 */
public class ResidueAnchorsCommand extends AbstractWorldCommand {

    /** Region reported around the player, per axis. Small on purpose: each candidate is a full-column scan. */
    private static final int REGION_RADIUS = 512;

    /** Stand-in until a world-seed accessor is confirmed; the probe's figures are seed-relative. */
    private static final long WORLD_SEED = 0x5EEDL;

    public ResidueAnchorsCommand() {
        super("residueanchors", "Reports per-element anchor count and spacing around the player");
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
                .append(", floor ")
                .append(parameters.minimumStrengthFraction())
                .append('\n');
        sb.append("region: +/-")
                .append(REGION_RADIUS)
                .append("b, margin ")
                .append(margin)
                .append("b, candidates ")
                .append(candidates.size())
                .append('\n');
        sb.append("element     anchors  mean gap  min gap  strongest   frac  sampled\n");

        int totalAnchors = 0;
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            // Lightning awaits its own treatment. Skipped explicitly rather than defaulted, and
            // printed rather than omitted, so the element stays visible in the output.
            if (grounding instanceof ElementGrounding.Weather) {
                sb.append(String.format(
                        "%-11s %7s %9s %8s %10s %6s %8s%n", grounding.elementId(), "-", "-", "-", "pending", "-", "-"));
                continue;
            }
            List<Anchor> measured = new ArrayList<>();
            long sampled = 0;
            for (WorldPoint2d candidate : candidates) {
                Optional<Anchor> anchor = VolumeStrength.measure(sample, parameters, grounding, candidate);
                if (anchor.isPresent()) {
                    measured.add(anchor.get());
                    sampled += anchor.get().samplesTaken();
                }
            }
            List<Anchor> selected = AnchorSelector.select(WORLD_SEED, parameters, measured);
            List<Anchor> inRegion = new ArrayList<>();
            for (Anchor anchor : selected) {
                if (anchor.position().x() >= minX
                        && anchor.position().x() <= maxX
                        && anchor.position().z() >= minZ
                        && anchor.position().z() <= maxZ) {
                    inRegion.add(anchor);
                }
            }
            totalAnchors += inRegion.size();
            sb.append(String.format(
                    "%-11s %7d %9s %8s %10s %6s %8d%n",
                    grounding.elementId(),
                    inRegion.size(),
                    gap(inRegion, false),
                    gap(inRegion, true),
                    inRegion.isEmpty() ? "-" : Integer.toString(strongest(inRegion)),
                    inRegion.isEmpty() ? "-" : String.format("%.3f", strongestFraction(inRegion)),
                    sampled));
        }

        sb.append("\nREADING THIS. Zero anchors means no qualifying terrain in range OR nothing\n")
                .append("loaded, and those are different answers -- the sampled column is how you\n")
                .append("tell them apart. A zero with a large sampled count is a real absence.\n")
                .append("'frac' is the strongest candidate's satisfying fraction; the floor is the\n")
                .append("number it must clear, so a frac just under it means the floor is what\n")
                .append("suppressed the element here.\n");
        sb.append("ANCHORS_VERDICT: ")
                .append(totalAnchors > 0 ? "READ" : "EMPTY")
                .append(" | ")
                .append(totalAnchors)
                .append(" anchor(s) across 4 element(s); lightning pending\n");
        send(context, sb.toString());
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

    private static int strongest(List<Anchor> anchors) {
        int best = 0;
        for (Anchor anchor : anchors) {
            best = Math.max(best, anchor.strength());
        }
        return best;
    }

    private static double strongestFraction(List<Anchor> anchors) {
        double best = 0;
        for (Anchor anchor : anchors) {
            best = Math.max(best, anchor.strengthFraction());
        }
        return best;
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

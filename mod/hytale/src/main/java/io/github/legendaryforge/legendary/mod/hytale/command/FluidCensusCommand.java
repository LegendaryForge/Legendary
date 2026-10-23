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
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.joml.Vector3d;

/**
 * Debug command: {@code /fluidcensus}
 *
 * <p>Tallies every distinct fluid id around the player and says which element claims each one.
 *
 * <p><strong>Written to close a broken invariant rather than to explore.</strong>
 * {@code /residuescale} requires Earth + Wind + Water + Fire to sum to 1 at every scale, because the
 * four predicates are exhaustive by construction — Earth is solid, Wind is not-solid-and-no-fluid,
 * and Water and Fire are their fluid sets. At a Plateau site every row summed to exactly 1.000. In a
 * lava biome every row summed to about 0.97. A position counted by nobody must therefore be
 * <em>not solid, holding a fluid that neither Water's set nor Fire's set contains</em> — so there is
 * a third fluid, and roughly 2-3% of the column is it.
 *
 * <p><strong>Guessing which fluid would have been cheap and wrong.</strong> {@code Water_Finite} is
 * the obvious candidate, since Water's set excludes it deliberately — contained water, so that a
 * player emptying a bucket cannot mint a Water nexus. But a lava variant outside
 * {@code {Lava_Source, Lava}} fits the evidence equally well, and so does something nobody has
 * named. This enumerates instead: every id present, its count, the y range it spans, and whether any
 * grounding claims it.
 *
 * <p><strong>The unclaimed rows are the output.</strong> Everything else here is context for them.
 * An unclaimed id with a large count is a hole in the measurement; an unclaimed id with a handful of
 * positions is a curiosity.
 *
 * <p>Two radii, because the invariant broke at <em>every</em> scale: if the third fluid were a local
 * pocket the gap would close as the window widened, and it did not.
 *
 * <p>Prints {@code FLUIDCENSUS_VERDICT:} as its last line.
 */
public class FluidCensusCommand extends AbstractWorldCommand {

    /** Radius and step per pass. The narrow one resolves a pocket; the wide one proves it is not one. */
    private static final int[][] PASSES = {
        {16, 1}, {64, 2},
    };

    public FluidCensusCommand() {
        super("fluidcensus", "Tallies distinct fluid ids near the player and which element claims each");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- fluid census ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players connected; a headless server loads no chunks so there is nothing\n")
                    .append("to census. Connect a client.\n")
                    .append("FLUIDCENSUS_VERDICT: INCONCLUSIVE | no players\n");
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
            sb.append("no player had a TransformComponent\n")
                    .append("FLUIDCENSUS_VERDICT: INCONCLUSIVE | no transform\n");
            send(context, sb.toString());
            return;
        }

        // Which grounding claims which fluid id, read off the production rules rather than restated
        // here -- a hardcoded copy would agree today and drift silently.
        Map<String, String> claimedBy = new LinkedHashMap<>();
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if (grounding instanceof ElementGrounding.Physical physical) {
                for (String fluid : physical.requiredFluids()) {
                    claimedBy.merge(fluid, physical.elementId(), (a, b) -> a + "+" + b);
                }
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
        sb.append("claimed fluid ids, from the production rules: ")
                .append(claimedBy)
                .append('\n');

        SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);
        long totalUnclaimed = 0;

        for (int[] pass : PASSES) {
            int radius = pass[0];
            int step = pass[1];
            Map<String, long[]> tally = new TreeMap<>();
            long sampled = 0;
            long fluidPositions = 0;
            for (int dx = -radius; dx <= radius; dx += step) {
                for (int dz = -radius; dz <= radius; dz += step) {
                    for (int y = 0; y <= 319; y += step) {
                        int x = playerX + dx;
                        int z = playerZ + dz;
                        if (!sample.isLoaded(x, y, z)) {
                            continue;
                        }
                        sampled++;
                        Optional<String> fluid = sample.fluidAt(x, y, z);
                        if (fluid.isEmpty()) {
                            continue;
                        }
                        fluidPositions++;
                        // [count, minY, maxY]
                        long[] row = tally.computeIfAbsent(fluid.get(), k -> new long[] {0, 999, -1});
                        row[0]++;
                        row[1] = Math.min(row[1], y);
                        row[2] = Math.max(row[2], y);
                    }
                }
            }

            sb.append(String.format(
                    "%nPASS radius %d step %d -- %d sampled, %d holding fluid (%.3f of window)%n",
                    radius, step, sampled, fluidPositions, sampled == 0 ? 0.0 : fluidPositions / (double) sampled));
            sb.append(String.format(
                    "%-26s %10s %7s %7s %10s  %s%n", "fluid id", "count", "minY", "maxY", "share", "claimed by"));
            for (Map.Entry<String, long[]> entry : tally.entrySet()) {
                String id = entry.getKey();
                long[] row = entry.getValue();
                String owner = claimedBy.get(id);
                if (owner == null) {
                    totalUnclaimed += row[0];
                }
                sb.append(String.format(
                        "%-26s %10d %7d %7d %10.5f  %s%n",
                        id,
                        row[0],
                        row[1],
                        row[2],
                        sampled == 0 ? 0.0 : row[0] / (double) sampled,
                        owner == null ? "NOBODY <-- the gap" : owner));
            }
            if (tally.isEmpty()) {
                sb.append("(no fluid at all in this window)\n");
            }
        }

        sb.append("\nREADING THIS. An id marked NOBODY is counted by no element, so it is invisible to\n")
                .append("Earth (not solid), to Wind (holds a fluid) and to Water and Fire (wrong set) --\n")
                .append("which is exactly the shortfall /residuescale's sum column reported. Its share\n")
                .append("column should account for that shortfall; if it does not, there is a second\n")
                .append("cause and the sum is still unexplained.\n")
                .append("Whether an unclaimed fluid SHOULD be claimed is a design question, not a bug:\n")
                .append("Water excludes Water_Finite on purpose so a player's bucket cannot mint a nexus.\n");
        sb.append("FLUIDCENSUS_VERDICT: ")
                .append(totalUnclaimed > 0 ? "GAP FOUND" : "ALL CLAIMED")
                .append(" | ")
                .append(totalUnclaimed)
                .append(" unclaimed fluid position(s) across ")
                .append(PASSES.length)
                .append(" pass(es)\n");
        send(context, sb.toString());
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

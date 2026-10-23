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
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ColumnMeasurement;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.VolumeStrength;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3d;

/**
 * Debug command: {@code /residuescale}
 *
 * <p>Measures the element volumes at the player's exact position across a range of window radii and
 * sample steps, so the <em>scale</em> a reading describes can be separated from the <em>correctness</em>
 * of the reading. {@code /residueanchors} measures at lattice candidates, which are never where the
 * player is standing; this one is centred on them deliberately.
 *
 * <p><strong>Why a sweep rather than one small window.</strong> A single alternative radius cannot
 * distinguish "the production window diluted a real local feature" from "the local feature is not
 * what the player thinks it is" — both produce one number that disagrees with the other. A monotone
 * progression from 4b to 128b shows the feature's own extent, and an abrupt change between two radii
 * locates its edge.
 *
 * <p><strong>The step column is the accuracy control, not a performance dial.</strong> A 4b window at
 * the production step of 8 samples one horizontal position, so the small end of the sweep is only
 * meaningful at step 1. Printing {@code 128/1} next to {@code 128/8} is what shows whether the
 * production step is adequate at the production radius: if they agree, the step is fine and only the
 * radius is in question.
 *
 * <p><strong>The sum column is an invariant, not a statistic.</strong> Earth is solid, Wind is
 * no-block-and-no-fluid, and Water and Fire are their fluids, so the four partition every sampled
 * position and must total 1.000 at every scale. A row that does not is a defect in the measurement
 * itself — which is the question this command exists to answer, and the one thing here that does not
 * depend on anybody's judgement about scale.
 *
 * <p>Prints {@code SCALE_VERDICT:} as its last line.
 */
public class ResidueScaleCommand extends AbstractWorldCommand {

    /**
     * Window radius and sample step per row, cheapest first.
     *
     * <p>Ordered by cost so a sweep cut short by the budget below still yields the small-radius rows,
     * which are the ones the production config cannot produce. The last two share a radius and differ
     * only in step, for the reason the javadoc gives.
     */
    private static final int[][] WINDOWS = {
        {4, 1}, {8, 1}, {16, 1}, {32, 1}, {64, 1}, {128, 8}, {128, 1},
    };

    /**
     * Rows are abandoned once the sweep has cost this long.
     *
     * <p>This runs on the world thread — {@code /residueanchors} read 11.1M positions and drew a
     * "Task took 510ms" warning — and the full sweep is roughly 113M. A hitch is acceptable in a
     * debug probe; an unbounded one that lands while a player is moving is not.
     */
    private static final long BUDGET_MS = 15_000;

    public ResidueScaleCommand() {
        super("residuescale", "Measures element volumes at the player across window radii and sample steps");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- residue scale sweep ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players connected. This command measures AT a player, so there is nothing\n")
                    .append("to centre on. Connect a client.\n")
                    .append("SCALE_VERDICT: INCONCLUSIVE | no players\n");
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
            sb.append("no player had a TransformComponent; cannot centre\n")
                    .append("SCALE_VERDICT: INCONCLUSIVE | no transform\n");
            send(context, sb.toString());
            return;
        }

        AnchorParameters production = AnchorParameters.defaults();
        SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);
        WorldPoint2d centre = new WorldPoint2d(playerX, playerZ);

        List<ElementGrounding> measurable = new ArrayList<>();
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if (!(grounding instanceof ElementGrounding.Weather)) {
                measurable.add(grounding);
            }
        }

        sb.append("centre: ")
                .append(who)
                .append(" at ")
                .append(playerX)
                .append(',')
                .append(playerY)
                .append(',')
                .append(playerZ)
                .append(" (the player's own column, not a lattice candidate)\n");
        sb.append("production window for reference: radius ")
                .append(production.measurementRadius())
                .append("b, step ")
                .append(production.sampleStep())
                .append(", floor ")
                .append(production.minimumStrengthFraction())
                .append('\n');

        sb.append(String.format("%n%6s %5s %6s %9s %7s", "radius", "step", "load", "samples", "ms"));
        for (ElementGrounding grounding : measurable) {
            sb.append(String.format(" %7s", grounding.elementId()));
        }
        sb.append("     sum\n");

        StringBuilder levels = new StringBuilder(String.format("%6s", "radius"));
        for (ElementGrounding grounding : measurable) {
            levels.append(String.format(" %7s", grounding.elementId()));
        }
        levels.append('\n');

        long started = System.nanoTime();
        int rowsRun = 0;
        int partitionBreaks = 0;
        boolean budgetHit = false;
        for (int[] windowSpec : WINDOWS) {
            if (System.nanoTime() - started > BUDGET_MS * 1_000_000L) {
                budgetHit = true;
                break;
            }
            int radius = windowSpec[0];
            int step = windowSpec[1];
            AnchorParameters parameters = new AnchorParameters(
                    production.cellSize(),
                    production.jitterFraction(),
                    production.suppressionRadius(),
                    radius,
                    step,
                    production.minimumStrengthFraction(),
                    production.influenceRadius(),
                    production.crossingTolerance());

            long rowStart = System.nanoTime();
            Map<String, ColumnMeasurement> byElement = new LinkedHashMap<>();
            for (ElementGrounding grounding : measurable) {
                byElement.put(grounding.elementId(), VolumeStrength.measure(sample, parameters, grounding, centre));
            }
            long rowMs = (System.nanoTime() - rowStart) / 1_000_000L;
            rowsRun++;

            int samples = 0;
            for (ColumnMeasurement measurement : byElement.values()) {
                samples = Math.max(samples, measurement.samplesTaken());
            }
            int window = parameters.windowSampleCount();
            sb.append(String.format("%6d %5d %5.0f%% %9d %7d", radius, step, 100.0 * samples / window, samples, rowMs));
            levels.append(String.format("%6d", radius));
            double sum = 0;
            for (ElementGrounding grounding : measurable) {
                ColumnMeasurement measurement = byElement.get(grounding.elementId());
                sum += measurement.strengthFraction();
                sb.append(String.format(" %7.3f", measurement.strengthFraction()));
                levels.append(String.format(
                        " %7s", measurement.modalLevel() < 0 ? "-" : Integer.toString(measurement.modalLevel())));
            }
            levels.append(String.format("   step %d%n", step));
            // An unloaded window reads nothing, so its fractions are all zero and sum to zero. That
            // is not a partition failure -- it is the no-data case, and counting it as a defect would
            // make the invariant unreadable precisely where the load column already says why.
            if (samples > 0 && Math.abs(sum - 1.0) > 0.002) {
                partitionBreaks++;
                sb.append(String.format("  %.3f <-- NOT 1%n", sum));
            } else {
                sb.append(String.format("  %.3f%n", sum));
            }
        }

        sb.append("\nMODAL LEVEL -- the y with the most satisfying samples, which is where an anchor of\n")
                .append("that element would sit. '-' means the element was absent at that scale.\n")
                .append(levels);

        if (budgetHit) {
            sb.append("\nBUDGET: stopped after ")
                    .append(rowsRun)
                    .append(" of ")
                    .append(WINDOWS.length)
                    .append(" rows at ")
                    .append(BUDGET_MS)
                    .append("ms. The rows that ran are complete; the rest were not attempted.\n");
        }

        sb.append("\nREADING THIS. The sum column is an invariant: earth + wind + water + fire partition\n")
                .append("every sampled position, so a row that is not 1.000 is a measurement defect and\n")
                .append("nothing below it should be trusted. Compare 128/1 against 128/8 to judge the\n")
                .append("production step; compare small radii against 128 to judge the production radius.\n")
                .append("A feature smaller than a window is diluted by the ratio of their footprints, so\n")
                .append("a reading falling away with radius locates a local feature rather than an error.\n");
        sb.append("SCALE_VERDICT: ")
                .append(partitionBreaks == 0 ? "PARTITION HOLDS" : "PARTITION BROKEN")
                .append(" | ")
                .append(rowsRun)
                .append(" of ")
                .append(WINDOWS.length)
                .append(" window(s) measured | ")
                .append(partitionBreaks)
                .append(" row(s) not summing to 1\n");
        send(context, sb.toString());
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

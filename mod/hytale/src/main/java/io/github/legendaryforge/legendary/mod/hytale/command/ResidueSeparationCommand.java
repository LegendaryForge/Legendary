package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import io.github.legendaryforge.legendary.core.api.residue.Anchor;
import io.github.legendaryforge.legendary.core.api.residue.AnchorParameters;
import io.github.legendaryforge.legendary.core.api.residue.ColumnMeasurement;
import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.CandidateLattice;
import io.github.legendaryforge.legendary.core.internal.residue.VolumeStrength;
import io.github.legendaryforge.legendary.mod.hytale.residue.GeneratorTerrainSample;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Debug command: {@code /residueseparation}
 *
 * <p>Answers one question: <strong>at the current candidate spacing, does narrowing the measurement
 * window make anchor selection less arbitrary?</strong>
 *
 * <p><strong>Why this is worth measuring rather than arguing.</strong>
 * {@code 2026-10-22-residue-sizing-decision.md} §10 names <em>"hash-selected anchors read as
 * arbitrary on the ground"</em> as a thing that would change the sizing decision, and its stated
 * remedy is <em>"a narrower window"</em>. Session 28 then halved {@code cellSize} to 128 while
 * leaving {@code measurementRadius} at 128, so the window (257 b) is now <strong>twice</strong> the
 * candidate spacing rather than equal to it, and neighbouring candidates see more shared terrain
 * than before. The decision was left open as <em>"Unknown until walked to"</em>.
 *
 * <p>Walking to it needs a client. <strong>This does not</strong>, because
 * {@code generateHeight} is a pure generator call — see {@link GeneratorTerrainSample}. So the
 * mechanism half of the question can be settled headlessly, leaving only the perception half
 * ("does it feel arbitrary standing there") to a human.
 *
 * <p><strong>Two metrics, and they are predicted to disagree.</strong> Stated here before the run,
 * because a prediction written after the fact is not one:
 *
 * <ol>
 *   <li><strong>Exact-tie rate.</strong> {@code AnchorSelector.beats} consults the position hash
 *       <em>only</em> when two strengths are integer-equal, so this is literally "how often does the
 *       hash decide". Narrowing the window shrinks the integer range a strength can occupy (43,560
 *       possible values at radius 128, 11,560 at 64), so by pigeonhole it should <em>increase</em>
 *       exact ties.
 *   <li><strong>Relative separation.</strong> How decisively terrain decides when it does decide.
 *       Narrowing reduces the overlap between neighbouring windows, so their strengths should become
 *       less correlated and separation should <em>increase</em>.
 * </ol>
 *
 * <p>If both move as predicted, "narrower is less arbitrary" is not a single fact and the decision
 * needs to say which arbitrariness it minds. That is a more useful outcome than either number alone,
 * and it is why both are printed side by side rather than one being chosen here.
 *
 * <p><strong>Earth only, and that is a property of the predicates rather than a shortcut.</strong>
 * {@link GeneratorTerrainSample} documents why Wind, Water, Fire and Lightning are not measurable
 * this way. Earth's environment and sky-light gates are wide open, so only occupancy discriminates
 * and the substituted read is the only thing driving the number.
 *
 * <p>Prints {@code SEPARATION_VERDICT:} as its last line. Read it by name.
 */
public class ResidueSeparationCommand extends AbstractWorldCommand {

    /**
     * Half-width of the region candidates are drawn from.
     *
     * <p>At the default {@code cellSize} of 128 this yields about {@code (2*768/128)^2 = 144}
     * candidates, which at a suppression radius of 256 gives each roughly 11.6 competitors — enough
     * contests for a rate to mean something without the run taking minutes.
     */
    private static final int REGION_HALF_WIDTH = 768;

    /** The window radii compared. 128 is current; 64 restores window to candidate spacing. */
    private static final int[] WINDOW_RADII = {128, 64};

    /** Surface for the flat negative control. Mid-world, so neither clamp is exercised. */
    private static final int FLAT_CONTROL_SURFACE = 160;

    /** Below this relative gap a contest is reported as near-tied. Not a threshold for any verdict. */
    private static final double NEAR_TIE_FRACTION = 0.01;

    public ResidueSeparationCommand() {
        super(
                "residueseparation",
                "Measures whether narrowing the measurement window makes anchor selection less arbitrary");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- residue separation probe ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        WorldConfig config;
        long seed;
        try {
            config = world.getWorldConfig();
            seed = config.getSeed();
        } catch (RuntimeException e) {
            bail(context, sb, "no world config: " + describe(e));
            return;
        }
        sb.append("seed: ").append(seed).append('\n');

        ChunkGenerator generator;
        try {
            IWorldGen raw = config.getWorldGenProvider().getGenerator();
            if (!(raw instanceof ChunkGenerator cg)) {
                bail(
                        context,
                        sb,
                        "generator is " + (raw == null ? "null" : raw.getClass().getName()));
                return;
            }
            generator = cg;
        } catch (Exception e) {
            bail(context, sb, "getGenerator() threw: " + describe(e));
            return;
        }

        ElementGrounding earth = null;
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if ("earth".equals(grounding.elementId())) {
                earth = grounding;
            }
        }
        if (earth == null) {
            bail(context, sb, "no 'earth' grounding in cosmologicalFive()");
            return;
        }

        AnchorParameters base = AnchorParameters.defaults();
        List<WorldPoint2d> candidates = CandidateLattice.candidatesWithin(
                seed,
                base.cellSize(),
                base.jitterFraction(),
                -REGION_HALF_WIDTH,
                -REGION_HALF_WIDTH,
                REGION_HALF_WIDTH,
                REGION_HALF_WIDTH);

        sb.append("element: earth (the only one this route can measure -- see GeneratorTerrainSample)\n");
        sb.append("region: +/-")
                .append(REGION_HALF_WIDTH)
                .append(" blocks   candidates: ")
                .append(candidates.size())
                .append("   cellSize: ")
                .append(base.cellSize())
                .append("   suppressionRadius: ")
                .append(base.suppressionRadius())
                .append('\n');
        sb.append("floor: ")
                .append(earth.minimumStrengthVolume())
                .append(" blocks = ")
                .append(earth.minimumStrengthSamples(base.sampleStep()))
                .append(" samples at step ")
                .append(base.sampleStep())
                .append('\n');

        if (candidates.isEmpty()) {
            bail(context, sb, "the lattice proposed no candidate in the region");
            return;
        }

        // CONTROL FIRST. A separation measurement that does not report every contest tied on flat
        // ground is measuring something other than terrain, and finding that out after reading the
        // real numbers is finding it out too late.
        sb.append("\n=== CONTROL: flat terrain at y=")
                .append(FLAT_CONTROL_SURFACE)
                .append(" ===\n");
        sb.append("Every column identical, so every contest MUST be an exact tie and separation MUST\n")
                .append("be zero. Anything else invalidates the real rows below.\n");
        sb.append(header());
        Row flatRow = null;
        for (int radius : WINDOW_RADII) {
            Row row = measure(
                    GeneratorTerrainSample.flat(FLAT_CONTROL_SURFACE), withWindow(base, radius), earth, candidates);
            sb.append(row.render("flat r=" + radius));
            if (flatRow == null) {
                flatRow = row;
            }
        }

        sb.append("\n=== REAL TERRAIN, via generateHeight ===\n");
        sb.append(header());
        List<Row> rows = new ArrayList<>();
        for (int radius : WINDOW_RADII) {
            GeneratorTerrainSample sample = GeneratorTerrainSample.of(generator, (int) seed);
            Row row = measure(sample, withWindow(base, radius), earth, candidates);
            row.columns = sample.columnsCached();
            rows.add(row);
            sb.append(row.render("real r=" + radius));
        }

        sb.append('\n').append(interpret(rows, flatRow));

        sb.append("\nSEPARATION_VERDICT: ").append(verdict(rows, flatRow)).append('\n');
        send(context, sb.toString());
    }

    private static String header() {
        return String.format(
                "%-12s %7s %8s %9s %9s %9s %11s%n",
                "window", "elig", "contests", "exactTie%", "nearTie%", "medSep%", "maxStrength");
    }

    /** One window's measurement over every candidate, plus the pairwise contest statistics. */
    private static Row measure(
            TerrainSample sample, AnchorParameters parameters, ElementGrounding earth, List<WorldPoint2d> candidates) {
        Row row = new Row();
        int floor = earth.minimumStrengthSamples(parameters.sampleStep());

        List<Anchor> eligible = new ArrayList<>();
        for (WorldPoint2d candidate : candidates) {
            ColumnMeasurement measurement = VolumeStrength.measure(sample, parameters, earth, candidate);
            row.maxStrength = Math.max(row.maxStrength, measurement.strength());
            // The floor is applied here for the same reason AnchorSelector applies it: a candidate
            // below it never enters a contest, so counting its ties would inflate the rate with
            // pairs the selector never evaluates.
            if (measurement.strength() < floor) {
                continue;
            }
            Optional<Anchor> anchor = measurement.asCandidate(candidate.x(), candidate.z());
            anchor.ifPresent(eligible::add);
        }
        row.eligible = eligible.size();

        double suppression = parameters.suppressionRadius();
        List<Double> separations = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            for (int j = i + 1; j < eligible.size(); j++) {
                Anchor a = eligible.get(i);
                Anchor b = eligible.get(j);
                double dx = a.position().x() - b.position().x();
                double dz = a.position().z() - b.position().z();
                if (Math.sqrt(dx * dx + dz * dz) > suppression) {
                    continue;
                }
                row.contests++;
                int sa = a.strength();
                int sb2 = b.strength();
                if (sa == sb2) {
                    // The ONLY case AnchorSelector.beats consults the hash for.
                    row.exactTies++;
                    separations.add(0.0);
                    continue;
                }
                double relative = (double) Math.abs(sa - sb2) / Math.max(sa, sb2);
                if (relative < NEAR_TIE_FRACTION) {
                    row.nearTies++;
                }
                separations.add(relative);
            }
        }
        row.medianSeparation = median(separations);
        return row;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** {@code base} with one dial changed, so every other parameter is provably identical. */
    private static AnchorParameters withWindow(AnchorParameters base, int measurementRadius) {
        return new AnchorParameters(
                base.cellSize(),
                base.jitterFraction(),
                base.suppressionRadius(),
                measurementRadius,
                base.sampleStep(),
                base.influenceRadius(),
                base.crossingTolerance());
    }

    /** Reads the rows against the prediction stated in the class javadoc. */
    private static String interpret(List<Row> rows, Row flatRow) {
        StringBuilder out = new StringBuilder();
        if (flatRow == null || flatRow.contests == 0) {
            return "CONTROL DID NOT RUN, so the real rows above are uninterpreted.\n";
        }
        if (flatRow.exactTies != flatRow.contests) {
            return "CONTROL FAILED: flat terrain produced "
                    + flatRow.exactTies + " exact ties in " + flatRow.contests
                    + " contests, and every one should have tied. The instrument is measuring\n"
                    + "something other than terrain, so the real rows are NOT evidence.\n";
        }
        out.append("control OK: flat terrain tied every one of ")
                .append(flatRow.contests)
                .append(" contests, so the real rows measure terrain.\n");
        if (rows.size() < 2) {
            return out.append("fewer than two windows measured; nothing to compare.\n")
                    .toString();
        }
        Row wide = rows.get(0);
        Row narrow = rows.get(1);
        out.append("\nagainst the prediction stated before the run:\n");
        out.append("  exact ties   ")
                .append(describeMove(wide.exactTiePercent(), narrow.exactTiePercent()))
                .append("  (predicted UP, from the smaller integer range)\n");
        out.append("  separation   ")
                .append(describeMove(wide.medianSeparation * 100, narrow.medianSeparation * 100))
                .append("  (predicted UP, from less window overlap)\n");
        return out.toString();
    }

    private static String describeMove(double wide, double narrow) {
        if (Double.isNaN(wide) || Double.isNaN(narrow)) {
            return "not computable";
        }
        String direction = narrow > wide ? "UP" : (narrow < wide ? "DOWN" : "UNCHANGED");
        return String.format("%.2f -> %.2f  %s", wide, narrow, direction);
    }

    /**
     * The verdict, computed from the same row objects the body printed.
     *
     * <p>Deliberately reports the direction of both metrics rather than collapsing them into a
     * recommendation. The probe's job is to replace an argument with numbers; choosing which
     * arbitrariness to minimise is a design decision and not this command's to take.
     */
    private static String verdict(List<Row> rows, Row flatRow) {
        if (flatRow == null || flatRow.contests == 0) {
            return "UNTESTED | control did not run";
        }
        if (flatRow.exactTies != flatRow.contests) {
            return "CONTROL_FAILED | flat terrain did not tie every contest";
        }
        if (rows.size() < 2 || rows.get(0).contests == 0 || rows.get(1).contests == 0) {
            return "UNTESTED | fewer than two windows produced contests";
        }
        Row wide = rows.get(0);
        Row narrow = rows.get(1);
        return String.format(
                "MEASURED | control OK | exactTie%% %.2f->%.2f | medSep%% %.2f->%.2f | contests %d/%d",
                wide.exactTiePercent(),
                narrow.exactTiePercent(),
                wide.medianSeparation * 100,
                narrow.medianSeparation * 100,
                wide.contests,
                narrow.contests);
    }

    /** One window's row. Mutable because the contest loop accumulates into it. */
    private static final class Row {
        int eligible;
        int contests;
        int exactTies;
        int nearTies;
        int maxStrength;
        int columns;
        double medianSeparation = Double.NaN;

        double exactTiePercent() {
            return contests == 0 ? Double.NaN : 100.0 * exactTies / contests;
        }

        double nearTiePercent() {
            return contests == 0 ? Double.NaN : 100.0 * nearTies / contests;
        }

        String render(String label) {
            return String.format(
                    "%-12s %7d %8d %9s %9s %9s %11d%s%n",
                    label,
                    eligible,
                    contests,
                    fmt(exactTiePercent()),
                    fmt(nearTiePercent()),
                    fmt(medianSeparation * 100),
                    maxStrength,
                    columns > 0 ? "  (" + columns + " columns)" : "");
        }

        private static String fmt(double value) {
            return Double.isNaN(value) ? "-" : String.format("%.2f", value);
        }
    }

    private static void bail(CommandContext context, StringBuilder sb, String why) {
        sb.append("\nSEPARATION_VERDICT: UNTESTED | ").append(why).append('\n');
        send(context, sb.toString());
    }

    private static String describe(Throwable thrown) {
        String message = thrown.getMessage();
        return thrown.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

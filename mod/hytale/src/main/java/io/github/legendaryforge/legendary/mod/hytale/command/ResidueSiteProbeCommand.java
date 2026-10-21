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
import io.github.legendaryforge.legendary.core.api.residue.LatticeParameters;
import io.github.legendaryforge.legendary.core.api.residue.SiteTyping;
import io.github.legendaryforge.legendary.core.api.residue.WorldPoint2d;
import io.github.legendaryforge.legendary.core.internal.residue.CandidateLattice;
import io.github.legendaryforge.legendary.core.internal.residue.SiteScorer;
import io.github.legendaryforge.legendary.mod.hytale.residue.HytaleDerivationRules;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3d;

/**
 * Debug command: {@code /residueprobe}
 *
 * <p><strong>Measure before building.</strong> Terrain derivation was adopted on 2026-10-21, and the
 * capability question — can we read lava, water, volcanic terrain, exposure — is answered: this
 * module compiles those reads against the real jar. The question this command exists for is the one
 * no amount of reading assets can answer: <em>does scoring a lattice of candidates against real
 * terrain put a Fire nexus at the lava lake, or thirty blocks into the basalt beside it?</em>
 *
 * <p>That is a tuning question, not a capability question, and it decides whether the design is any
 * good. So nothing is placed, nothing is persisted, and nothing is written. The command prints the
 * derivation's opinion about ground the operator can walk to and judge.
 *
 * <p><strong>Why the output is verbose.</strong> It reports every contributing signal per typing,
 * and every typing per candidate rather than only the winner. A probe that prints a verdict cannot
 * be argued with, and this one exists to be argued with. The near-misses matter twice over: a mixed
 * nexus is defined under the placed-nexus model as a site qualifying for <em>two</em> elements, so
 * second place is half the answer; and "Fire 4.0, Water 1.0" against "Fire 4.0, Water 1.9" describes
 * two different places, only one of which becomes mixed when a dial moves.
 *
 * <p><strong>What this probe cannot tell you</strong>, stated here so a reading of its output does
 * not overclaim:
 *
 * <ul>
 *   <li>It samples a <strong>horizontal slab at the operator's own elevation</strong>, not the
 *       terrain surface. {@code IHeightmap.getHeight} exists but the accessor handing a caller the
 *       world's heightmap is unresolved, and inventing a downward block scan would have meant
 *       assuming which block id is empty. So stand on the thing you want judged.
 *   <li>Everything is bounded to <strong>loaded chunks</strong>, which near a player is a region
 *       local to that player. Candidates outside it report {@code unloaded}, which is the absence of
 *       a result and not a negative one.
 *   <li><strong>Lightning will over-report.</strong> Its rule shares Wind's exposure signals because
 *       storm frequency per environment is not available through the terrain port.
 *   <li><strong>River and ocean are indistinguishable.</strong> Water is water; no environment,
 *       sea-level constant or worldgen node separates them.
 * </ul>
 *
 * <p>The verdict line is content rather than exit status, per this project's standing rule that a
 * verdict must survive a pipe.
 */
public class ResidueSiteProbeCommand extends AbstractWorldCommand {

    /** Window half-width in cells. Two cells each way is four cells across — enough to see spacing. */
    private static final int WINDOW_CELLS = 2;

    /** A world seed stand-in, so the run is reproducible while the real accessor is unconfirmed. */
    private static final long PROBE_SEED = 0x5EEDL;

    public ResidueSiteProbeCommand() {
        super("residueprobe", "Scores terrain-derived residue site candidates near you, placing nothing");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        LatticeParameters parameters = LatticeParameters.defaults();
        List<ElementGrounding> groundings = HytaleDerivationRules.cosmologicalFive();

        StringBuilder sb = new StringBuilder();
        sb.append("--- residue site probe (terrain derivation, nothing placed) ---\n");
        sb.append("world: ").append(world.getName()).append('\n');
        sb.append("cell=")
                .append(parameters.cellSize())
                .append("b jitter=")
                .append(parameters.jitterFraction())
                .append(" nbhd=")
                .append(parameters.neighbourhoodRadius())
                .append("b step=")
                .append(parameters.sampleStep())
                .append(" vspan=")
                .append(parameters.verticalSpan())
                .append(" qualify>=")
                .append(parameters.qualifyingScore())
                .append('\n');
        sb.append("groundings: ")
                .append(groundings.size())
                .append(" elements, seed=")
                .append(PROBE_SEED)
                .append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("\nno players connected.\n")
                    .append("A headless server loads no chunk region at all, so every candidate would read\n")
                    .append("unloaded and the run would say nothing about the scoring. Connect a client.\n")
                    .append("PROBE_VERDICT: INCONCLUSIVE | no players\n");
            sendText(context, sb.toString());
            return;
        }

        int totalCandidates = 0;
        int totalTyped = 0;
        int totalMixed = 0;
        int totalUnloaded = 0;
        Map<String, Integer> histogram = new LinkedHashMap<>();

        for (PlayerRef playerRef : playerRefs) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                sb.append("\n[").append(playerRef.getUsername()).append("] TransformComponent ABSENT\n");
                continue;
            }
            Vector3d pos = transform.getPosition();
            int px = MathUtil.floor(pos.x);
            int py = MathUtil.floor(pos.y);
            int pz = MathUtil.floor(pos.z);

            SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), py);

            sb.append("\n[")
                    .append(playerRef.getUsername())
                    .append("] at ")
                    .append(px)
                    .append(',')
                    .append(py)
                    .append(',')
                    .append(pz)
                    .append("  (slab sampled at y=")
                    .append(py)
                    .append(")\n");
            sb.append("  here: env=")
                    .append(sample.environmentAt(px, py, pz).orElse("-"))
                    .append(" fluid=")
                    .append(sample.fluidAt(px, py, pz).orElse("none"))
                    .append(" sky=")
                    .append(sample.skyLightAt(px, py, pz))
                    .append('\n');
            // Both fluid accessors, so one run settles whether the choice between them matters.
            sb.append("  fluid accessors: ")
                    .append(sample.fluidComparisonAt(px, py, pz))
                    .append('\n');

            double reach = (double) WINDOW_CELLS * parameters.cellSize();
            List<WorldPoint2d> candidates = CandidateLattice.candidatesWithin(
                    PROBE_SEED, parameters, px - reach, pz - reach, px + reach, pz + reach);
            sb.append("  candidates in +/-")
                    .append((int) reach)
                    .append("b: ")
                    .append(candidates.size())
                    .append('\n');

            for (WorldPoint2d candidate : candidates) {
                totalCandidates++;
                int cx = (int) Math.floor(candidate.x());
                int cz = (int) Math.floor(candidate.z());
                double distance = Math.hypot(cx - (double) px, cz - (double) pz);

                List<SiteTyping> typings = SiteScorer.scoreAll(candidate, sample, parameters, groundings);
                sb.append("   ")
                        .append(cx)
                        .append(',')
                        .append(cz)
                        .append("  d=")
                        .append(String.format("%.0f", distance))
                        .append("b  ");

                if (typings.isEmpty()) {
                    boolean loaded = sample.isLoaded(cx, py, cz);
                    if (!loaded) {
                        totalUnloaded++;
                    }
                    sb.append(loaded ? "no element qualified" : "unloaded (no result, not a negative)")
                            .append('\n');
                    continue;
                }

                List<String> qualifying = new ArrayList<>();
                for (SiteTyping typing : typings) {
                    if (typing.score() >= parameters.qualifyingScore()) {
                        qualifying.add(typing.elementId());
                    }
                }
                if (!qualifying.isEmpty()) {
                    totalTyped++;
                    histogram.merge(String.join("+", qualifying), 1, Integer::sum);
                }
                if (qualifying.size() >= 2) {
                    totalMixed++;
                    sb.append("MIXED ");
                }
                sb.append(summarise(typings, parameters)).append('\n');
                for (SiteTyping typing : typings) {
                    sb.append("        ")
                            .append(typing.elementId())
                            .append(": ")
                            .append(String.join("; ", typing.reasons()))
                            .append('\n');
                }
            }
        }

        sb.append("\nsummary: ")
                .append(totalCandidates)
                .append(" candidates, ")
                .append(totalTyped)
                .append(" qualified, ")
                .append(totalMixed)
                .append(" mixed, ")
                .append(totalUnloaded)
                .append(" unloaded\n");
        if (!histogram.isEmpty()) {
            sb.append("elements: ");
            histogram.forEach((element, count) ->
                    sb.append(element).append('=').append(count).append(' '));
            sb.append('\n');
        }
        // A run where nothing was loaded says nothing about the scoring, and must not read as the
        // scoring having failed -- the same distinction the terrain signal probe had to make.
        String verdict = totalCandidates == 0
                ? "INCONCLUSIVE | no candidates in window"
                : totalUnloaded == totalCandidates
                        ? "INCONCLUSIVE | every candidate unloaded"
                        : "OBSERVED | " + totalTyped + "/" + totalCandidates + " qualified, " + totalMixed + " mixed";
        sb.append("PROBE_VERDICT: ").append(verdict).append('\n');
        sb.append("Judge this by walking to the sites, not by the counts.\n");

        sendText(context, sb.toString());
    }

    /** {@code fire 5.0 > water 2.0 > earth 1.0} — enough to see the margin, not just the winner. */
    private static String summarise(List<SiteTyping> typings, LatticeParameters parameters) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < typings.size(); i++) {
            SiteTyping typing = typings.get(i);
            if (i > 0) {
                out.append(" > ");
            }
            out.append(typing.elementId())
                    .append(' ')
                    .append(String.format("%.1f", typing.score()))
                    .append(typing.score() >= parameters.qualifyingScore() ? "*" : "");
        }
        return out.toString();
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

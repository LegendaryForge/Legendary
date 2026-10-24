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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.joml.Vector3d;

/**
 * Debug command: {@code /partitiongap}
 *
 * <p>Attributes every sampled position that satisfies <strong>no</strong> element, by reading the
 * individual gates that rejected it.
 *
 * <p><strong>Second attempt at one question.</strong> {@code /residuescale}'s sum came to ~0.97 in a
 * lava biome against exactly 1.000 at a plateau, so some positions were counted by nobody. The first
 * hypothesis — an unclaimed third fluid — was tested by {@code /fluidcensus} and <em>refuted</em>:
 * every fluid present is claimed, zero unclaimed positions.
 *
 * <p>The suspect now is {@code satisfiesGeometric}, which gates on environment and sky light
 * <em>before</em> occupancy. {@code skyLightAt} returns {@code -1} for unloaded or unknown and
 * {@code -1 < 0} fails the band test, so a position whose sky value is unknown is excluded from Earth
 * and Wind both. Same for an environment that does not resolve. If that is the cause, Earth and Wind
 * are underestimates wherever terrain data is unresolved — a quieter failure than a missing fluid,
 * because it reads as terrain simply not being there.
 *
 * <p><strong>The reconciliation line is the point of this probe, not the tallies.</strong> It
 * compares this command's classification against what {@code VolumeStrength} actually counted over
 * the same window. The gate logic here is a <em>restatement</em> of the production logic, and a
 * restatement can be wrong in the same breath as the thing it explains — so unless the two agree,
 * the attribution is a story rather than a measurement.
 *
 * <p>Prints {@code PARTITIONGAP_VERDICT:} as its last line.
 */
public class PartitionGapCommand extends AbstractWorldCommand {

    /**
     * The PRODUCTION window, because the gap is radius-dependent and a narrower one cannot see it.
     *
     * <p>This probe first shipped at radius 16 step 1 and reported zero gap with an exact
     * reconciliation — both true, and useless: {@code /residuescale} sums to exactly 1.000 at radii
     * 4 through 32 and only breaks at 64 and above. The attribution was scoped to a scale where the
     * phenomenon does not occur, so its clean result was mistaken for a refutation.
     *
     * <p>Radius-dependence is itself the clue, and it indicts one gate specifically: horizontal
     * reach is what decides how many distinct environments a window covers, and Earth and Wind both
     * require the environment id to match. A narrow window sits inside one environment; a wide one
     * does not.
     */
    private static final int RADIUS = 128;

    private static final int STEP = 8;

    /** Earth's and Wind's production environment set, restated for attribution only. */
    private static final Set<String> GEOMETRIC_ENVIRONMENTS = Set.of("Env_");

    public PartitionGapCommand() {
        super("partitiongap", "Attributes positions that satisfy no element, by the gate that rejected them");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- partition gap attribution ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("no players; nothing loaded to attribute.\n")
                    .append("PARTITIONGAP_VERDICT: INCONCLUSIVE | no players\n");
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
                    .append("PARTITIONGAP_VERDICT: INCONCLUSIVE | no transform\n");
            send(context, sb.toString());
            return;
        }

        SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);

        long sampled = 0;
        long solid = 0;
        long lava = 0;
        long water = 0;
        long openClean = 0;
        long gapEnvEmpty = 0;
        long gapEnvNoMatch = 0;
        long gapSkyUnknown = 0;
        long gapSkyOutOfBand = 0;
        long gapOtherFluid = 0;
        // The offending ids, not just their count. A count says the gate fires; the ids say which
        // environments the production rules fail to recognise, which is the actionable half.
        Map<String, long[]> unmatchedEnvironments = new TreeMap<>();
        Map<String, long[]> matchedEnvironments = new TreeMap<>();

        for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                for (int y = 0; y <= 319; y += STEP) {
                    int x = playerX + dx;
                    int z = playerZ + dz;
                    if (!sample.isLoaded(x, y, z)) {
                        continue;
                    }
                    sampled++;
                    Optional<String> fluid = sample.fluidAt(x, y, z);

                    // Fire and Water are pure fluid tests -- no environment or sky gate -- so they
                    // cannot contribute to the gap. Counted first for exactly that reason.
                    if (fluid.isPresent()) {
                        String id = fluid.get();
                        if ("Lava".equals(id) || "Lava_Source".equals(id)) {
                            lava++;
                            continue;
                        }
                        if ("Water".equals(id) || "Water_Source".equals(id)) {
                            water++;
                            continue;
                        }
                    }

                    // Earth and Wind both run the Geometric gates first. Attribute by which gate
                    // rejects, in the order satisfiesGeometric applies them.
                    Optional<String> environment = sample.environmentAt(x, y, z);
                    if (environment.isEmpty()) {
                        gapEnvEmpty++;
                        continue;
                    }
                    if (!ElementGrounding.environmentMatches(environment.get(), GEOMETRIC_ENVIRONMENTS)) {
                        gapEnvNoMatch++;
                        unmatchedEnvironments.computeIfAbsent(environment.get(), k -> new long[] {0})[0]++;
                        continue;
                    }
                    matchedEnvironments.computeIfAbsent(environment.get(), k -> new long[] {0})[0]++;
                    int sky = sample.skyLightAt(x, y, z);
                    if (sky < 0) {
                        gapSkyUnknown++;
                        continue;
                    }
                    if (sky > 15) {
                        gapSkyOutOfBand++;
                        continue;
                    }
                    if (sample.isSolidAt(x, y, z)) {
                        solid++;
                    } else if (fluid.isEmpty()) {
                        openClean++;
                    } else {
                        // Not solid, holds a fluid nobody claims -- the refuted hypothesis. Kept so a
                        // zero here is on the record rather than inherited from the earlier census.
                        gapOtherFluid++;
                    }
                }
            }
        }
        long accounted = solid + lava + water + openClean;
        long gap = sampled - accounted;

        sb.append("anchor: ")
                .append(who)
                .append(" at ")
                .append(playerX)
                .append(',')
                .append(playerY)
                .append(',')
                .append(playerZ)
                .append("   window radius ")
                .append(RADIUS)
                .append(" step ")
                .append(STEP)
                .append('\n');
        sb.append(String.format("%nsampled: %d%n", sampled));
        sb.append(String.format("%-34s %10s %10s%n", "bucket", "count", "share"));
        row(sb, "earth (solid, gates passed)", solid, sampled);
        row(sb, "fire (lava fluid)", lava, sampled);
        row(sb, "water (water fluid)", water, sampled);
        row(sb, "wind (open, gates passed)", openClean, sampled);
        sb.append(String.format("%-34s %10d %10.5f%n", "-- accounted --", accounted, accounted / (double) sampled));
        row(sb, "GAP environment unresolved", gapEnvEmpty, sampled);
        row(sb, "GAP environment no match", gapEnvNoMatch, sampled);
        row(sb, "GAP sky unknown (-1)", gapSkyUnknown, sampled);
        row(sb, "GAP sky out of band (>15)", gapSkyOutOfBand, sampled);
        row(sb, "GAP unclaimed fluid", gapOtherFluid, sampled);
        sb.append(String.format("%-34s %10d %10.5f%n", "-- total gap --", gap, gap / (double) sampled));

        if (!unmatchedEnvironments.isEmpty()) {
            sb.append("\nENVIRONMENT IDS FAILING THE 'Env_' MATCH -- invisible to BOTH earth and wind:\n");
            for (Map.Entry<String, long[]> e : unmatchedEnvironments.entrySet()) {
                sb.append(String.format("  %-44s %10d%n", e.getKey(), e.getValue()[0]));
            }
        }
        sb.append("\nenvironment ids that DID match (")
                .append(matchedEnvironments.size())
                .append("):\n");
        for (Map.Entry<String, long[]> e : matchedEnvironments.entrySet()) {
            sb.append(String.format("  %-44s %10d%n", e.getKey(), e.getValue()[0]));
        }

        // Reconciliation. Without this the attribution above is a restatement of the production gates
        // checking itself, which proves nothing.
        AnchorParameters parameters = new AnchorParameters(256, 0.35, 512, RADIUS, STEP, 8, 8);
        WorldPoint2d centre = new WorldPoint2d(playerX, playerZ);
        long volumeSum = 0;
        sb.append("\nRECONCILIATION against VolumeStrength over the same window:\n");
        for (ElementGrounding grounding : HytaleDerivationRules.cosmologicalFive()) {
            if (grounding instanceof ElementGrounding.Weather) {
                continue;
            }
            try {
                ColumnMeasurement m = VolumeStrength.measure(sample, parameters, grounding, centre);
                volumeSum += m.strength();
                sb.append(String.format(
                        "  %-10s strength %10d   samplesTaken %10d%n",
                        grounding.elementId(), m.strength(), m.samplesTaken()));
            } catch (Throwable t) {
                sb.append("  ")
                        .append(grounding.elementId())
                        .append(" threw ")
                        .append(t.getClass().getSimpleName())
                        .append('\n');
            }
        }
        boolean reconciles = volumeSum == accounted;
        sb.append(String.format(
                "  VolumeStrength total %d vs this probe's accounted %d -- %s%n",
                volumeSum, accounted, reconciles ? "AGREE" : "DISAGREE, trust neither"));

        sb.append("\nREADING THIS. The largest GAP row names the gate dropping positions from Earth and\n")
                .append("Wind both. Any non-zero environment or sky GAP row means the comment in\n")
                .append("VolumeStrength is wrong: those gates do NOT 'accept everything' for Earth, they\n")
                .append("accept everything KNOWN, and unknown terrain data is silently excluded -- so\n")
                .append("Earth and Wind are underestimates wherever it is unresolved.\n");
        sb.append("PARTITIONGAP_VERDICT: ")
                .append(!reconciles ? "UNRECONCILED" : (gap > 0 ? "GAP ATTRIBUTED" : "NO GAP"))
                .append(" | ")
                .append(gap)
                .append(" of ")
                .append(sampled)
                .append(" unaccounted | reconciliation ")
                .append(reconciles ? "AGREE" : "DISAGREE")
                .append('\n');
        send(context, sb.toString());
    }

    private static void row(StringBuilder sb, String label, long count, long sampled) {
        sb.append(String.format("%-34s %10d %10.5f%n", label, count, sampled == 0 ? 0.0 : count / (double) sampled));
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

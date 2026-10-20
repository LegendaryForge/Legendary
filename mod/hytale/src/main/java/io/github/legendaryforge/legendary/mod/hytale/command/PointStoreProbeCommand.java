package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.builtin.points.PointsPlugin;
import com.hypixel.hytale.builtin.points.manager.PointEntry;
import com.hypixel.hytale.builtin.points.manager.PointManager;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Debug command: {@code /pointprobe}
 *
 * <p>Runs the probe named in §5.2 of
 * {@code docs/superpowers/specs/2026-10-20-hytale-0.7.2-capability-assessment-design.md}, which
 * gates adopting {@code builtin/points} as the store behind N9's {@code ResidueField}. That is an
 * adopt-before-build decision: we have no incumbent spatial index, so the leverage is code never
 * written — and the cost of being wrong is discovering it after {@code ResidueField} is built on it.
 *
 * <p>Three questions, in descending order of how badly a wrong answer would hurt:
 *
 * <ol>
 *   <li><b>Persistence.</b> Do registered points survive a server restart? {@code PointManager} has
 *       a {@code CODEC}, which makes persistence plausible and not proven. Nexuses must outlive a
 *       restart or the store is useless to us. <strong>Answered by the banner line on the SECOND
 *       boot</strong> — the probe reports pre-existing points before registering any.
 *   <li><b>Query correctness.</b> Points are planted at known distances (5, 15, 25, 35, 45 m) along
 *       +x, so {@code queryRadius(origin, 20)} has one correct answer: the first two. A count alone
 *       would not catch an off-by-one or a bounding-box-not-sphere implementation, so the ids are
 *       printed and the expected set is stated inline.
 *   <li><b>Tag filtering.</b> N9 types nexuses by element pair, so a tag-filtered radius query is
 *       the difference between one index and fifteen. The tag argument's format is undocumented;
 *       both plausible forms are tried and reported separately rather than guessed at.
 * </ol>
 *
 * <p>Points are left in the world on purpose — they are the next run's persistence evidence. The
 * ids are prefixed so a later cleanup can find them, and re-running is harmless: registration is
 * by id, so a second run overwrites rather than duplicates.
 */
public class PointStoreProbeCommand extends AbstractWorldCommand {

    private static final String ID_PREFIX = "legendary_probe_";

    private static final String TAG_KEY = "legendary_element";

    private static final String TAG_VALUE = "lightning";

    /** Distances along +x. queryRadius(origin, 20) must return exactly the first two. */
    private static final int[] DISTANCES = {5, 15, 25, 35, 45};

    private static final double QUERY_RADIUS = 20.0;

    public PointStoreProbeCommand() {
        super("pointprobe", "Tests builtin/points persistence, radius query and tag filtering");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- point store probe (0.7.2) ---\n");

        PointsPlugin plugin = PointsPlugin.get();
        if (plugin == null) {
            sendText(
                    context,
                    sb.append("PointsPlugin.get() == null -- plugin not loaded\n")
                            .toString());
            return;
        }
        PointManager manager = store.getResource(plugin.getManagerResourceType());
        if (manager == null) {
            sendText(
                    context,
                    sb.append("PointManager resource ABSENT on this world's store\n")
                            .toString());
            return;
        }
        sb.append("PointManager: ok | world=")
                .append(manager.getWorld() == null ? "null" : manager.getWorld().getName())
                .append(" | total points already present=")
                .append(manager.getPoints().size())
                .append("\n");

        // (1) PERSISTENCE -- read BEFORE registering. On a second boot this is the whole answer.
        int survived = 0;
        sb.append("pre-existing probe points:\n");
        for (int d : DISTANCES) {
            String id = ID_PREFIX + d;
            PointEntry e = manager.getPoint(id);
            if (e == null) {
                continue;
            }
            survived++;
            Vector3d p = e.getPosition();
            sb.append("  ")
                    .append(id)
                    .append(" at ")
                    .append(String.format("%.1f %.1f %.1f", p.x, p.y, p.z))
                    .append(" tags=")
                    .append(e.getRawTags())
                    .append("\n");
        }
        sb.append("  -> ").append(survived).append(" of ").append(DISTANCES.length);
        sb.append(survived == 0 ? " (first boot, or NOT persisted)\n" : " SURVIVED -- points persist across restart\n");

        Vector3d origin = new Vector3d(0.5, 100.0, 0.5);
        sb.append("\norigin for this run: ")
                .append(String.format("%.1f %.1f %.1f", origin.x, origin.y, origin.z))
                .append("\n");

        // (2) register at known distances
        sb.append("registering ").append(DISTANCES.length).append(" points:\n");
        for (int d : DISTANCES) {
            String id = ID_PREFIX + d;
            try {
                PointEntry entry = new PointEntry(
                        id,
                        world.getName(),
                        new Vector3d(origin.x + d, origin.y, origin.z),
                        new Vector3f(),
                        "probe " + d + "m",
                        true);
                manager.register(id, entry);
                boolean tagged = manager.setTag(id, TAG_KEY, TAG_VALUE);
                sb.append("  ")
                        .append(id)
                        .append(" hasPoint=")
                        .append(manager.hasPoint(id))
                        .append(" setTag=")
                        .append(tagged)
                        .append("\n");
            } catch (RuntimeException e) {
                sb.append("  ")
                        .append(id)
                        .append(" THREW ")
                        .append(e.getClass().getSimpleName())
                        .append(": ")
                        .append(e.getMessage())
                        .append("\n");
            }
        }
        manager.markSpatialDirty();
        sb.append("total points now: ").append(manager.getPoints().size()).append("\n");

        // (3) query correctness -- the expected answer is stated, not just the count
        sb.append("\nqueryRadius(origin, ")
                .append(QUERY_RADIUS)
                .append(") expects exactly [")
                .append(ID_PREFIX)
                .append("5, ")
                .append(ID_PREFIX)
                .append("15]\n");
        try {
            List<PointEntry> hits = manager.queryRadius(origin, QUERY_RADIUS);
            sb.append("  got ").append(hits.size()).append(": ");
            for (PointEntry e : hits) {
                sb.append(e.getId()).append(" ");
            }
            sb.append("\n");
        } catch (RuntimeException e) {
            sb.append("  THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n");
        }
        appendRadius(sb, manager, origin, 50.0);
        appendRadius(sb, manager, origin, 1.0);

        // (4) tag filtering -- format undocumented, so try both plausible forms
        sb.append("\ntag-filtered query (format is undocumented; both forms tried):\n");
        appendTagged(sb, manager, origin, TAG_KEY);
        appendTagged(sb, manager, origin, TAG_KEY + ":" + TAG_VALUE);
        try {
            sb.append("  getPointsWithTags(\"")
                    .append(TAG_KEY)
                    .append("\") -> ")
                    .append(manager.getPointsWithTags(TAG_KEY).size())
                    .append("\n");
        } catch (RuntimeException e) {
            sb.append("  getPointsWithTags THREW ")
                    .append(e.getClass().getSimpleName())
                    .append("\n");
        }

        sb.append("\nPoints are left registered on purpose -- they are the next boot's evidence.\n");
        sendText(context, sb.toString());
    }

    private void appendRadius(StringBuilder sb, PointManager manager, Vector3d origin, double r) {
        try {
            sb.append("queryRadius(origin, ")
                    .append(r)
                    .append(") -> ")
                    .append(manager.queryRadius(origin, r).size())
                    .append("\n");
        } catch (RuntimeException e) {
            sb.append("queryRadius(origin, ")
                    .append(r)
                    .append(") THREW ")
                    .append(e.getClass().getSimpleName())
                    .append("\n");
        }
    }

    private void appendTagged(StringBuilder sb, PointManager manager, Vector3d origin, String tag) {
        try {
            List<PointEntry> hits = manager.queryRadius(origin, 100.0, tag);
            sb.append("  queryRadius(.., 100, \"")
                    .append(tag)
                    .append("\") -> ")
                    .append(hits.size())
                    .append("\n");
        } catch (RuntimeException e) {
            sb.append("  queryRadius(.., 100, \"")
                    .append(tag)
                    .append("\") THREW ")
                    .append(e.getClass().getSimpleName())
                    .append(": ")
                    .append(e.getMessage())
                    .append("\n");
        }
    }

    private void sendText(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

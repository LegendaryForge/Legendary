package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Debug command: {@code /chunkhookprobe}
 *
 * <p>Reports what {@link ChunkPreLoadProcessEvent} actually delivers. That event is the placement
 * hook the generation-time residue plan needs, and it is preferable to decorating the world's
 * generator for three reasons: it is registered through the same
 * {@code getEventRegistry().registerGlobal} this plugin already uses for player events, so it needs
 * no load-order window before world init; it leaves the engine's generator untouched, so there is no
 * second implementation of terrain to keep in agreement; and it carries
 * {@link ChunkPreLoadProcessEvent#isNewlyGenerated()}.
 *
 * <p><strong>That flag is the whole reason this hook is the right one.</strong> Residue must be
 * written when a chunk is first generated and never again — re-deriving it on every load would be
 * deterministic and therefore silently undo a player mining a crystal. A counter split by the flag
 * is how we find out whether it really distinguishes a fresh chunk from one read off disk, rather
 * than assuming it from its name.
 *
 * <p><strong>The listener only counts.</strong> It fires on every chunk load, so anything expensive
 * there is a server-wide cost; and a probe that measures a hook by doing work inside it measures the
 * work rather than the hook. Reading the chunk out of the holder is attempted once and then latched,
 * to prove the data path exists without paying for it repeatedly.
 *
 * <p>Prints {@code CHUNKHOOK_VERDICT:} as its last line.
 */
public class ChunkHookProbeCommand extends AbstractWorldCommand {

    private static final AtomicLong TOTAL = new AtomicLong();
    private static final AtomicLong NEWLY_GENERATED = new AtomicLong();
    private static final AtomicLong FROM_DISK = new AtomicLong();
    private static final AtomicLong HOLDER_NULL = new AtomicLong();

    /** Latched once rather than per event: the component fetch is a capability question, not a metric. */
    private static volatile String chunkComponentOutcome = "not yet observed";

    private static volatile boolean registered;

    public ChunkHookProbeCommand() {
        super("chunkhookprobe", "Reports ChunkPreLoadProcessEvent tallies and whether the chunk is reachable");
    }

    /**
     * Wired from the plugin's {@code start()}, alongside the player listeners.
     *
     * <p>Chunk loads continue for the life of the server, so unlike the provider route this has no
     * ordering requirement against world init at all.
     */
    public static void onChunkPreLoad(ChunkPreLoadProcessEvent event) {
        TOTAL.incrementAndGet();
        if (event.isNewlyGenerated()) {
            NEWLY_GENERATED.incrementAndGet();
        } else {
            FROM_DISK.incrementAndGet();
        }
        Holder<?> holder = event.getHolder();
        if (holder == null) {
            HOLDER_NULL.incrementAndGet();
            return;
        }
        if (!"not yet observed".equals(chunkComponentOutcome)) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Holder<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> typed =
                    (Holder<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>) holder;
            WorldChunk chunk = typed.getComponent(WorldChunk.getComponentType());
            chunkComponentOutcome = chunk == null
                    ? "holder present, WorldChunk component ABSENT"
                    : "WorldChunk reached: " + chunk.getClass().getSimpleName();
        } catch (Throwable t) {
            String message = t.getMessage();
            chunkComponentOutcome =
                    "component fetch threw " + t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        }
    }

    /** Called from {@code start()} so the report can say whether the listener was ever wired. */
    public static void markRegistered() {
        registered = true;
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        long total = TOTAL.get();
        long fresh = NEWLY_GENERATED.get();
        long disk = FROM_DISK.get();
        StringBuilder sb = new StringBuilder("--- chunk pre-load hook probe ---\n");
        sb.append("world: ").append(world.getName()).append('\n');
        sb.append("listener registered: ").append(registered).append('\n');
        sb.append("events seen:        ").append(total).append('\n');
        sb.append("  newly generated:  ").append(fresh).append('\n');
        sb.append("  read from disk:   ").append(disk).append('\n');
        sb.append("  null holder:      ").append(HOLDER_NULL.get()).append('\n');
        sb.append("chunk reachability: ").append(chunkComponentOutcome).append('\n');

        sb.append("\nREADING THIS. A non-zero total proves the hook fires. BOTH buckets non-zero proves\n")
                .append("isNewlyGenerated() discriminates, which is what lets residue be written once at\n")
                .append("generation instead of re-derived on every load -- re-deriving is deterministic\n")
                .append("and would therefore undo a player mining a crystal. Walk into unexplored terrain\n")
                .append("to grow the fresh bucket; revisit somewhere you have already been to grow the\n")
                .append("disk bucket. If only one bucket ever moves, the flag is not the discriminator\n")
                .append("its name implies and placement needs a different signal.\n");
        sb.append("CHUNKHOOK_VERDICT: ")
                .append(total == 0 ? "NOT FIRING" : (fresh > 0 && disk > 0 ? "DISCRIMINATES" : "FIRING"))
                .append(" | ")
                .append(total)
                .append(" event(s), ")
                .append(fresh)
                .append(" fresh, ")
                .append(disk)
                .append(" from disk\n");
        send(context, sb.toString());
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

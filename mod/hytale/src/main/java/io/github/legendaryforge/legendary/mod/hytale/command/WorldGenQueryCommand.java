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
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider;
import com.hypixel.hytale.server.worldgen.cache.CoreDataCacheEntry;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import io.github.legendaryforge.legendary.mod.hytale.residue.SectionReaderTerrainSample;
import java.util.OptionalInt;
import org.joml.Vector3d;

/**
 * Debug command: {@code /worldgenquery}
 *
 * <p>Answers whether terrain can be queried at a position <strong>without that position's chunk
 * being generated</strong>. That is the question the residue model's timing rests on: the derivation
 * needs roughly 640b of terrain around a point to place anything there ({@code suppressionRadius}
 * 512 plus {@code measurementRadius} 128), and at chunk-generation time that terrain does not exist.
 * If the generator can be asked instead of the chunk, derivation can run world-wide and in tandem
 * with worldgen; if it cannot, placement has to be staged behind what has already been read.
 *
 * <p><strong>Why this probe exists at all.</strong> The adoption spec resolved this fork as "bounded
 * to loaded chunks" after finding {@code EnvironmentProvider.evaluate} unreachable — true, but that
 * class lives in {@code builtin/hytalegenerator}, and the generator this world actually runs is
 * {@code server.worldgen.chunk.ChunkGenerator}, a different tree that was never examined. It carries
 * its own positional queries. Everything below is {@code [javap]} until this command runs.
 *
 * <p><strong>The known-answer test comes first, and the output is unusable without it.</strong> The
 * three {@code int}s of {@code getCoreData} are read from {@code CoordinateCache$CoordinateKey},
 * whose fields are {@code int seed} and {@code long coord} — so they should be {@code (seed, x, z)},
 * a 2D query with an explicit seed. "Should be" is a hypothesis about an argument order, and a wrong
 * order returns a number rather than an error. So the probe first asks for the player's own column,
 * whose height it can read independently from the loaded chunk, and only a match licenses reading
 * the far-coordinate answer.
 *
 * <p><strong>Two side effects, both deliberate and bounded.</strong> The generator's cache is
 * read-through, so querying a coordinate may add an entry for it — a handful here. And
 * {@code getGenerator()} may construct a generator rather than return the live one; its timing is
 * printed because that distinction matters for whether this is a viable production route.
 *
 * <p>Prints {@code WORLDGENQUERY_VERDICT:} as its last line.
 */
public class WorldGenQueryCommand extends AbstractWorldCommand {

    /**
     * Horizontal offset for the "certainly never generated" probe.
     *
     * <p>100,000 blocks is ~164x the measured loaded radius of a single player (~610b), so no play
     * session could have generated it incidentally. A near-but-unvisited offset would leave the
     * result ambiguous, which is the whole failure this probe is built to avoid.
     */
    private static final int FAR_OFFSET = 100_000;

    public WorldGenQueryCommand() {
        super("worldgenquery", "Tests whether terrain is queryable at ungenerated positions");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- worldgen query probe ---\n");
        sb.append("world: ").append(world.getName()).append('\n');

        // Step 1 -- the seed. Reported first because it closes an item on its own: the residue probes
        // have been using a 0x5EED stand-in with "world-seed accessor unconfirmed" recorded against
        // them, and WorldConfig.getSeed() is public.
        WorldConfig config;
        long seed;
        try {
            config = world.getWorldConfig();
            seed = config.getSeed();
        } catch (RuntimeException e) {
            sb.append("world.getWorldConfig()/getSeed() FAILED: ")
                    .append(describe(e))
                    .append('\n');
            sb.append("WORLDGENQUERY_VERDICT: BLOCKED | no world config\n");
            send(context, sb.toString());
            return;
        }
        sb.append("seed: ")
                .append(seed)
                .append("  (0x")
                .append(Long.toHexString(seed))
                .append(") -- WorldConfig.getSeed() is public; the 0x5EED stand-in is no longer needed\n");

        // Step 2 -- reachability, through public API only. Each hop is reported separately so a
        // failure names which one, rather than collapsing to "not reachable".
        IWorldGenProvider provider;
        try {
            provider = config.getWorldGenProvider();
        } catch (RuntimeException e) {
            sb.append("getWorldGenProvider() FAILED: ").append(describe(e)).append('\n');
            sb.append("WORLDGENQUERY_VERDICT: BLOCKED | no provider\n");
            send(context, sb.toString());
            return;
        }
        if (provider == null) {
            sb.append("getWorldGenProvider() returned null\n")
                    .append("WORLDGENQUERY_VERDICT: BLOCKED | provider null\n");
            send(context, sb.toString());
            return;
        }
        sb.append("provider: ").append(provider.getClass().getName()).append('\n');

        IWorldGen generator;
        long generatorMs;
        try {
            long t0 = System.nanoTime();
            generator = provider.getGenerator();
            generatorMs = (System.nanoTime() - t0) / 1_000_000L;
        } catch (Exception e) {
            sb.append("provider.getGenerator() FAILED: ").append(describe(e)).append('\n');
            sb.append("WORLDGENQUERY_VERDICT: BLOCKED | getGenerator threw\n");
            send(context, sb.toString());
            return;
        }
        sb.append("generator: ")
                .append(generator == null ? "null" : generator.getClass().getName())
                .append("  (obtained in ")
                .append(generatorMs)
                .append("ms -- a large figure means this CONSTRUCTED one rather than returning the live instance)\n");

        if (!(generator instanceof ChunkGenerator chunkGenerator)) {
            sb.append("not a server.worldgen.chunk.ChunkGenerator, so its positional queries are not\n")
                    .append("reachable through this route.\n")
                    .append("WORLDGENQUERY_VERDICT: BLOCKED | generator is not a ChunkGenerator\n");
            send(context, sb.toString());
            return;
        }

        var playerRefs = world.getPlayerRefs();
        if (playerRefs.isEmpty()) {
            sb.append("\nreachability is established, but the known-answer test needs a player: the\n")
                    .append("comparison height is read from a LOADED chunk, and a headless server has\n")
                    .append("none. Connect a client and re-run.\n")
                    .append("WORLDGENQUERY_VERDICT: PARTIAL | reachable, semantics untested\n");
            send(context, sb.toString());
            return;
        }

        int playerX = 0;
        int playerY = 0;
        int playerZ = 0;
        String who = null;
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
                    .append("WORLDGENQUERY_VERDICT: PARTIAL | reachable, semantics untested\n");
            send(context, sb.toString());
            return;
        }

        // Step 3 -- the known answer, from the loaded chunk, independent of the generator.
        SectionReaderTerrainSample sample = new SectionReaderTerrainSample(world.getChunkStore(), playerY);
        OptionalInt loadedSurface = sample.surfaceHeightAt(playerX, playerZ);
        sb.append("\nanchor: ")
                .append(who)
                .append(" at ")
                .append(playerX)
                .append(',')
                .append(playerY)
                .append(',')
                .append(playerZ)
                .append("  loaded-chunk surface height: ")
                .append(loadedSurface.isPresent() ? Integer.toString(loadedSurface.getAsInt()) : "absent")
                .append('\n');

        sb.append("NO_HEIGHT sentinel = ").append(CoreDataCacheEntry.NO_HEIGHT).append('\n');
        sb.append(String.format("%n%-46s %8s %12s %s%n", "query", "height", "heightNoise", "biome"));

        // Both plausible argument orders at the player's own column. A wrong order returns a number
        // rather than an error, so the one that matches the loaded-chunk height is the real framing
        // and the other is noise -- which is why both are printed rather than one assumed.
        int seedInt = (int) seed;
        probe(sb, chunkGenerator, "player (seedInt, x, z)", seedInt, playerX, playerZ);
        probe(sb, chunkGenerator, "player (0, x, z)", 0, playerX, playerZ);
        probe(sb, chunkGenerator, "player (x, y, z) -- wrong-order control", playerX, playerY, playerZ);

        // Step 4 -- the decisive test. 100k blocks away cannot have been generated.
        int farX = playerX + FAR_OFFSET;
        int farZ = playerZ + FAR_OFFSET;
        probe(sb, chunkGenerator, "FAR +100k (seedInt, x, z)", seedInt, farX, farZ);
        probe(sb, chunkGenerator, "FAR +100k (0, x, z)", 0, farX, farZ);

        // Step 5 -- generateHeight, which COMPUTES rather than reading the cache. getCoreData above
        // leaves height at its NO_HEIGHT sentinel even for a loaded column, so the cache route
        // answers biome and not elevation; this is the method that should answer elevation.
        //
        // It was missed on the first pass because `javap | head -25` truncated it out of view --
        // the same "absence produced by a pipe" that hytale-engine-api.md already records against
        // itself for ChunkLightData.getSkyLight. Second instance of that mechanism in this file's
        // own investigation.
        sb.append(String.format("%n%-46s %8s  %s%n", "generateHeight query", "height", "note"));
        height(sb, chunkGenerator, "player (seedInt, x, z)", seedInt, playerX, playerZ);
        height(sb, chunkGenerator, "player (0, x, z)", 0, playerX, playerZ);
        height(sb, chunkGenerator, "player (x, y, z) -- wrong-order control", playerX, playerY, playerZ);
        height(sb, chunkGenerator, "FAR +100k (seedInt, x, z)", seedInt, farX, farZ);
        height(sb, chunkGenerator, "FAR +100k (0, x, z)", 0, farX, farZ);
        height(sb, chunkGenerator, "FAR +100k x only (seedInt, x, z)", seedInt, farX, playerZ);

        sb.append("\nREADING THIS. The generateHeight player rows are the known-answer test: the framing\n")
                .append("whose height matches the loaded-chunk surface height above is the real one.\n")
                .append("matches the loaded-chunk surface height above is the real one, and NOTHING in\n")
                .append("the FAR rows means anything until one of them matches. A FAR height that is\n")
                .append("not the NO_HEIGHT sentinel, under a validated framing, is the finding -- it\n")
                .append("means terrain is computable at ungenerated positions and residue derivation\n")
                .append("can run world-wide rather than near players.\n")
                .append("Caveat this probe cannot settle: height and biome are not solidity and fluid.\n")
                .append("Earth and Wind are largely functions of column height, but Water and Fire need\n")
                .append("fluid bodies, which are populated later in generation and are not queried here.\n");
        sb.append("WORLDGENQUERY_VERDICT: REACHED | generator=")
                .append(chunkGenerator.getClass().getSimpleName())
                .append(" | seed=")
                .append(seed)
                .append(" | compare player rows against loaded surface ")
                .append(loadedSurface.isPresent() ? Integer.toString(loadedSurface.getAsInt()) : "absent")
                .append('\n');
        send(context, sb.toString());
    }

    /** One {@code generateHeight} call. This one computes, so a real elevation here is the finding. */
    private static void height(StringBuilder sb, ChunkGenerator generator, String label, int a, int b, int c) {
        try {
            long t0 = System.nanoTime();
            int h = generator.generateHeight(a, b, c);
            long us = (System.nanoTime() - t0) / 1_000L;
            sb.append(String.format(
                    "%-46s %8d  %s, %dus%n",
                    label, h, h == CoreDataCacheEntry.NO_HEIGHT ? "SENTINEL (not computed)" : "computed", us));
        } catch (Throwable t) {
            sb.append(String.format("%-46s %8s  %s%n", label, "ERR", describe(t)));
        }
    }

    /** One {@code getCoreData} call, reported whether it answers, returns the sentinel, or throws. */
    private static void probe(StringBuilder sb, ChunkGenerator generator, String label, int a, int b, int c) {
        try {
            CoreDataCacheEntry entry = generator.getCoreData(a, b, c);
            if (entry == null) {
                sb.append(String.format("%-46s %8s %12s %s%n", label, "-", "-", "returned null"));
                return;
            }
            String biome = "-";
            try {
                var zoneBiome = entry.zoneBiomeResult;
                if (zoneBiome != null && zoneBiome.getBiome() != null) {
                    biome = String.valueOf(zoneBiome.getBiome());
                }
            } catch (RuntimeException e) {
                biome = "biome read threw: " + describe(e);
            }
            sb.append(String.format(
                    "%-46s %8d %12.4f %s%n",
                    label, entry.height, entry.heightNoise, biome.length() > 60 ? biome.substring(0, 60) : biome));
        } catch (Throwable t) {
            // Throwable, not Exception: a wrong argument order into native-ish cache code can produce
            // an Error, and a probe that dies silently on one row is worse than one that says so.
            sb.append(String.format("%-46s %8s %12s %s%n", label, "ERR", "-", describe(t)));
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** Mirrors the private helper in the sibling probe commands; {@code AbstractWorldCommand} has none. */
    private static void send(CommandContext context, String text) {
        FormattedMessage fmt = new FormattedMessage();
        fmt.rawText = text;
        context.sendMessage(new Message(fmt));
    }
}

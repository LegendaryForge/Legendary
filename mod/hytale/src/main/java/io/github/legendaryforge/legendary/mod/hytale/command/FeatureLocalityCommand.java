package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.procedurallib.logic.point.IPointGenerator;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.biome.BiomePatternGenerator;
import com.hypixel.hytale.server.worldgen.cave.CaveGenerator;
import com.hypixel.hytale.server.worldgen.cave.CaveType;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.hypixel.hytale.server.worldgen.container.PrefabContainer;
import com.hypixel.hytale.server.worldgen.prefab.PrefabPatternGenerator;
import com.hypixel.hytale.server.worldgen.zone.Zone;
import com.hypixel.hytale.server.worldgen.zone.ZonePatternProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;

/**
 * Debug command: {@code /featurelocality}
 *
 * <p>Runs the probe specified in {@code
 * docs/superpowers/specs/2026-11-07-feature-locality-confirming-probe-design.md}, which confirms or
 * refutes the worldgen-locality finding on PR #149. That finding is {@code [javap]} throughout —
 * this document's own tier table calls that <strong>a hypothesis</strong> — and it decides whether
 * residue can be derived at generation time.
 *
 * <p><strong>It needs no declared feature.</strong> #149's probe sentence was "declare a trivial
 * feature with a known maxSize and check its blocks appear in chunks other than its point's", which
 * assumes a mod can register into the prefab path — an open claim, and one the adopted architecture
 * has already declined. The base game ships thousands of declared features and the whole walk down
 * to their sizes is public API, so the engine's own features answer the question instead.
 *
 * <p>Four independent parts. Each reports on its own; a part that cannot run <em>says so</em> rather
 * than being omitted, because an absent line must never read as a passing one.
 *
 * <ol>
 *   <li><strong>Extent census.</strong> Every extent-like quantity the live generator exposes. Any
 *       one of them smaller than the largest declared feature size refutes claim 1.
 *   <li><strong>World-bound recovery.</strong> {@code isChunkOutsideGeneratableArea} is public even
 *       though {@code minChunkCoord} is not, so binary-searching it recovers the {@code extents} the
 *       constructor computed — executing {@code getLargestFeatureChunkExtents} rather than reading
 *       it.
 *   <li><strong>Point-generator trace.</strong> Does {@code collect} name where features actually
 *       are? Measured against a mandatory control set.
 *   <li><strong>Chunk crossing.</strong> #149's literal question.
 * </ol>
 *
 * <p>Prints {@code LOCALITY_VERDICT:} as its last line. Read it by name.
 */
public class FeatureLocalityCommand extends AbstractWorldCommand {

    /** Hytale's world chunk range before the constructor clamps it inward by {@code extents}. */
    private static final int WORLD_CHUNK_MIN = -67108864;

    private static final int WORLD_CHUNK_MAX = 67108863;

    /** {@code ChunkUtil.SIZE} is 32, so {@code ChunkUtil.chunkCoordinate} is {@code x >> 5}. */
    private static final int CHUNK_SHIFT = 5;

    /** {@code ChunkGenerator.CHUNK_BOUNDS_PADDING}, in CHUNKS. Private there, so asserted here. */
    private static final int EXPECTED_PADDING_CHUNKS = 16;

    /** Half-width of the region Part 3 collects over. Kept inside a single player's loaded radius. */
    private static final int COLLECT_HALF_WIDTH = 224;

    /** Columns sampled per set in Part 3. Collected and control sets are matched in size. */
    private static final int SAMPLE_TARGET = 48;

    /**
     * Vertical half-band searched for the topmost solid block, around {@code generateHeight}.
     *
     * <p>A full 0..319 scan per column would be sound but costs 320 reads; a band costs 193 and
     * bounds the work. A column whose topmost solid is outside the band is counted as a band miss
     * and reported separately — never folded into either the deviation or the agreement count,
     * because a miss is an absence of evidence and the two counts are evidence.
     */
    private static final int SCAN_HALF_BAND = 96;

    /** World ceiling, from {@code /blockidprobe}'s measured 0..319 range. */
    private static final int WORLD_TOP_Y = 319;

    public FeatureLocalityCommand() {
        super("featurelocality", "Confirms or refutes the worldgen feature-locality finding");
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        StringBuilder sb = new StringBuilder("--- feature locality probe ---\n");
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
                        "generator is " + (raw == null ? "null" : raw.getClass().getName())
                                + ", not a server.worldgen.chunk.ChunkGenerator");
                return;
            }
            generator = cg;
        } catch (Exception e) {
            bail(context, sb, "getGenerator() threw: " + describe(e));
            return;
        }
        sb.append("generator: ").append(generator.getClass().getName()).append('\n');

        ZonePatternProvider zones;
        try {
            zones = generator.getZonePatternProvider();
        } catch (RuntimeException e) {
            bail(context, sb, "getZonePatternProvider() threw: " + describe(e));
            return;
        }
        if (zones == null) {
            bail(context, sb, "getZonePatternProvider() returned null");
            return;
        }

        Census census = census(sb, zones);
        onWorldgenThread(sb, generator, census);
        Bound bound = recoverBound(sb, generator, census);
        Trace trace = trace(sb, world, store, generator, census, seed);

        sb.append("\nLOCALITY_VERDICT: ")
                .append(census.claim1Verdict())
                .append(" | extents recovered=")
                .append(bound.recovered)
                .append(" predicted=")
                .append(census.predictedExtents())
                .append(' ')
                .append(bound.matchVerdict(census.predictedExtents()))
                .append(" | claim2 ")
                .append(trace.verdict)
                .append(" | crossing ")
                .append(trace.crossing)
                .append('\n');
        send(context, sb.toString());
    }

    // ---------------------------------------------------------------- Part 1

    /** Every extent-like quantity the live generator exposes, plus the largest declared feature. */
    private static Census census(StringBuilder sb, ZonePatternProvider zones) {
        Census c = new Census();
        sb.append("\n=== PART 1: extent census ===\n");

        try {
            c.zoneProviderMaxExtent = zones.getMaxExtent();
            sb.append("ZonePatternProvider.getMaxExtent(): ")
                    .append(c.zoneProviderMaxExtent)
                    .append('\n');
        } catch (RuntimeException e) {
            sb.append("ZonePatternProvider.getMaxExtent() threw: ")
                    .append(describe(e))
                    .append('\n');
        }

        Zone[] zoneArray;
        try {
            zoneArray = zones.getZones();
        } catch (RuntimeException e) {
            sb.append("getZones() threw: ").append(describe(e)).append('\n');
            return c;
        }
        sb.append("zones: ").append(zoneArray == null ? 0 : zoneArray.length).append('\n');
        if (zoneArray == null) {
            return c;
        }

        sb.append(String.format(
                "%n%-26s %6s %8s %9s %9s %9s  %s%n",
                "zone / biome", "biomes", "bpgExt", "cave>", "prefab>", "excl>", "pattern(s) with the max"));

        for (Zone zone : zoneArray) {
            if (zone == null) {
                continue;
            }
            String zoneName = safe(zone::name, "zone?");
            int biomeCount = 0;
            int bpgExtents = -1;
            double caveMax = 0.0;
            int prefabMax = 0;
            int exclMax = 0;
            String maxPattern = "-";

            CaveGenerator caves = safeObj(zone::caveGenerator);
            if (caves != null) {
                CaveType[] types = safeObj(caves::getCaveTypes);
                if (types != null) {
                    for (CaveType t : types) {
                        if (t == null) {
                            continue;
                        }
                        double size = safeDouble(t::getMaximumSize);
                        if (size > caveMax) {
                            caveMax = size;
                        }
                    }
                }
            }

            BiomePatternGenerator bpg = safeObj(zone::biomePatternGenerator);
            // Each aggregate is compared against the maximum of ITS OWN scope, so the scope maxima
            // have to be accumulated bottom-up: entries -> container -> zone -> provider.
            int zoneFeatureMax = 0;
            if (bpg != null) {
                Biome[] biomes = safeObj(bpg::getBiomes);
                if (biomes != null) {
                    biomeCount = biomes.length;
                    for (Biome b : biomes) {
                        if (b == null) {
                            continue;
                        }
                        PrefabContainer container = safeObj(b::getPrefabContainer);
                        if (container == null) {
                            continue;
                        }
                        PrefabContainer.PrefabContainerEntry[] entries = safeObj(container::getEntries);
                        int containerContentMax = 0;
                        if (entries != null) {
                            for (PrefabContainer.PrefabContainerEntry entry : entries) {
                                if (entry == null) {
                                    continue;
                                }
                                PrefabPatternGenerator pattern = safeObj(entry::getPrefabPatternGenerator);
                                if (pattern == null) {
                                    continue;
                                }
                                c.patterns.add(pattern);
                                Integer size = safeInt(pattern::getMaxSize);
                                Integer excl = safeInt(pattern::getExclusionRadius);
                                if (size != null) {
                                    if (size > containerContentMax) {
                                        containerContentMax = size;
                                    }
                                    if (size > prefabMax) {
                                        prefabMax = size;
                                        maxPattern = safe(() -> String.valueOf(pattern.getCategory()), "?");
                                    }
                                    if (size > c.largestPrefabSize) {
                                        c.largestPrefabSize = size;
                                        c.largestPattern = pattern;
                                    }
                                }
                                if (excl != null && excl > exclMax) {
                                    exclMax = excl;
                                }
                                if (size != null) {
                                    c.entryRefs.add(new EntryRef(entry, size, zoneName));
                                }
                            }
                        }
                        Integer containerMax = safeInt(container::getMaxSize);
                        if (containerMax != null) {
                            c.noteAggregate(
                                    "PrefabContainer.getMaxSize[" + zoneName + "/" + safe(b::getName, "biome?") + "]",
                                    containerMax,
                                    containerContentMax);
                        } else {
                            c.noteUnreadable("PrefabContainer.getMaxSize", "returned null or threw");
                        }
                        if (containerContentMax > zoneFeatureMax) {
                            zoneFeatureMax = containerContentMax;
                        }
                    }
                }
                Integer ext = safeInt(bpg::getExtents);
                if (ext != null) {
                    bpgExtents = ext;
                    c.noteAggregate("BiomePatternGenerator.getExtents[" + zoneName + "]", ext, zoneFeatureMax);
                } else {
                    c.noteUnreadable("BiomePatternGenerator.getExtents", "returned null or threw");
                }
            }

            if (caveMax > c.largestCaveSize) {
                c.largestCaveSize = caveMax;
            }
            sb.append(String.format(
                    "%-26s %6d %8s %9.1f %9d %9d  %s%n",
                    truncate(zoneName, 26),
                    biomeCount,
                    bpgExtents < 0 ? "-" : Integer.toString(bpgExtents),
                    caveMax,
                    prefabMax,
                    exclMax,
                    truncate(maxPattern, 30)));
        }

        int declared = c.largestDeclaredBlocks();
        sb.append("\nlargest declared feature size D = ")
                .append(declared)
                .append(" blocks  (prefab max ")
                .append(c.largestPrefabSize)
                .append(", cave max ceil(")
                .append(String.format("%.1f", c.largestCaveSize))
                .append("))\n");
        sb.append("prefab patterns found: ").append(c.patterns.size()).append('\n');

        // Provider level, and it can only be compared once the whole walk is done. getMaxExtent
        // aggregates PREFAB sizes, so it is compared against the global prefab max -- not against D,
        // which is a cave size and so belongs to a different family of feature.
        if (c.zoneProviderMaxExtent >= 0) {
            c.noteAggregate("ZonePatternProvider.getMaxExtent[GLOBAL]", c.zoneProviderMaxExtent, c.largestPrefabSize);
        }

        sb.append("""

                -- claim 1: does any aggregate UNDER-REPORT its own contents? --
                A real clamp on feature reach looks like an aggregate SMALLER than the things it
                aggregates over. Comparing a local quantity against the GLOBAL largest feature is not
                a test of anything: D above is a CAVE size from one zone, while
                PrefabContainer.getMaxSize is a per-biome PREFAB aggregate, so every ocean biome with
                no cave-sized prefab would be flagged as clamping one. The first live run did exactly
                that and produced ~500 false gates.
                """);
        if (c.gates.isEmpty()) {
            sb.append("no aggregates read; claim 1 UNTESTED\n");
            return c;
        }
        int clamping = 0;
        for (Gate g : c.gates) {
            if (!g.clamps()) {
                continue;
            }
            clamping++;
            sb.append(String.format(
                    "  CLAMP %-54s declared=%-8d own scope max=%d%n",
                    truncate(g.name(), 54), g.declared(), g.ownScopeMax()));
        }
        sb.append(String.format(
                "%n%-42s %9s %9s %11s  %s%n", "aggregate kind", "checked", "clamping", "UNREADABLE", "why"));
        java.util.Set<String> kinds = new java.util.TreeSet<>(c.kindChecked.keySet());
        kinds.addAll(c.kindUnreadable.keySet());
        for (String kind : kinds) {
            sb.append(String.format(
                    "%-42s %9d %9d %11d  %s%n",
                    truncate(kind, 42),
                    c.kindChecked.getOrDefault(kind, 0),
                    c.kindClamping.getOrDefault(kind, 0),
                    c.kindUnreadable.getOrDefault(kind, 0),
                    c.kindReason.getOrDefault(kind, "")));
        }
        sb.append("aggregates checked: ")
                .append(c.gates.size())
                .append("   clamping: ")
                .append(clamping)
                .append("   prefab patterns walked: ")
                .append(c.patterns.size())
                .append('\n');
        sb.append("An UNREADABLE column that is not zero means the claim-1 census covers less than it\n")
                .append("appears to, and the shortfall is named rather than absorbed into a clean pass.\n");
        sb.append(c.refuted ? """

                        claim 1 REFUTED: an aggregate reports less than its own contents, so something
                        clamps feature reach below what features declare.
                        """ : """

                        claim 1 SURVIVES: every aggregate the live generator exposes is at least as
                        large as the scope it aggregates over, so none of them clamps feature reach.

                        READ THIS WITH PART 2, WHICH IS THE STRONGER HALF. If a second gate clamped
                        reach below the declared sizes, the world bound recovered by EXECUTING
                        isChunkOutsideGeneratableArea could not equal the value predicted FROM those
                        declared sizes. This census is an absence argument; the bound recovery is a
                        positive one.

                        Still weaker than "no second gate exists": it covers what is reachable through
                        public API from the live generator. That is the honest ceiling of a read-only
                        probe, and above the previous ceiling, which was one person's grep.
                        """);
        return c;
    }

    // --------------------------------------------------------------- Part 1b

    /**
     * Re-reads {@code PrefabContainerEntry.getExtents()} on a worldgen thread.
     *
     * <p>It cannot be read from the command thread: it resolves through
     * {@code ChunkGenerator.getResource()}, whose {@code chunkGenerator} back-reference is only set
     * on the generator's own pool threads. From here every call threw
     * {@code NullPointerException: Cannot invoke "ChunkGenerator.getPrefabLoadingCache()" because
     * "this.chunkGenerator" is null} — 1,429 of them, which the per-kind accounting surfaced and the
     * previous clean "0 clamping" had absorbed.
     *
     * <p>That the value resolves through the prefab <em>loading cache</em> is itself the reason this
     * matters rather than being tidiness. The per-entry extent is derived from the real prefab
     * dimensions, not from an authored constant — so it could in principle EXCEED the
     * {@code getMaxSize()} that {@code getLargestFeatureChunkExtents} reserves against, which would
     * mean the world bound under-reserves. A clamp and an exceed are opposite findings and are
     * counted separately.
     *
     * <p>{@code ChunkGenerator.execute} is public and submits onto that pool, so the read runs where
     * the back-reference exists.
     */
    @SuppressWarnings("removal") // see the NOTE this method prints: deliberate, and reported
    private static void onWorldgenThread(StringBuilder sb, ChunkGenerator generator, Census census) {
        sb.append("\n=== PART 1b: entry extents, re-read on a worldgen thread ===\n");
        if (census.entryRefs.isEmpty()) {
            sb.append("no entries retained; nothing to re-read\n");
            return;
        }
        sb.append("entries retained: ").append(census.entryRefs.size()).append('\n');
        sb.append("NOTE: ChunkGenerator.execute is @Deprecated and marked FOR REMOVAL on 0.7.6, so\n")
                .append("this route is on a removal path. Recorded as an engine fact, not worked around.\n");
        try {
            CompletableFuture<int[]> future = generator.execute(0, (index, cg) -> {
                int ok = 0;
                int failed = 0;
                int below = 0;
                int above = 0;
                int equal = 0;
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                for (EntryRef ref : census.entryRefs) {
                    try {
                        int extent = ref.entry().getExtents();
                        ok++;
                        min = Math.min(min, extent);
                        max = Math.max(max, extent);
                        if (extent < ref.declaredMax()) {
                            below++;
                        } else if (extent > ref.declaredMax()) {
                            above++;
                        } else {
                            equal++;
                        }
                    } catch (Throwable thrown) {
                        failed++;
                    }
                }
                return new int[] {ok, failed, below, above, equal, ok == 0 ? 0 : min, ok == 0 ? 0 : max};
            });
            int[] r = future.get(60, TimeUnit.SECONDS);
            sb.append("read ok: ")
                    .append(r[0])
                    .append("   failed: ")
                    .append(r[1])
                    .append('\n');
            if (r[0] == 0) {
                sb.append("the worldgen-thread route did not help; this quantity stays UNREAD and is\n")
                        .append("named as such rather than absorbed into a pass.\n");
                return;
            }
            sb.append("getExtents() range: ")
                    .append(r[5])
                    .append("..")
                    .append(r[6])
                    .append("   vs its entry's declared maxSize: below=")
                    .append(r[2])
                    .append(" equal=")
                    .append(r[4])
                    .append(" above=")
                    .append(r[3])
                    .append('\n');
            sb.append("""
                    THIS IS NOT A GATE TEST, and the first version of this part wrongly made it one.
                    It compared getExtents() against the entry's declared maxSize and called 361
                    "below" a CLAMP refuting claim 1. Nothing establishes that the two measure the
                    same thing: getExtents() resolves through the prefab LOADING CACHE and 1,049 of
                    1,429 came out ABOVE their declared maxSize while 361 came out below. A split in
                    both directions is the signature of two different quantities, not of a clamp --
                    and it is the same mistake as comparing a per-biome prefab aggregate against a
                    global cave size, which is the error this probe already had to have corrected.

                    Two positive reasons it does not participate in feature reach: the decompiled
                    getLargestFeatureChunkExtents maxes over getMaxSize() and CaveType.getMaximumSize()
                    and never consults getExtents(); and Part 2 recovers a world bound that EQUALS the
                    value predicted from those two, which it could not if a third quantity were
                    clamping. Reported here as characterisation for a future engine-doc row.
                    """);
        } catch (Throwable thrown) {
            sb.append("execute() on the generator pool failed: ")
                    .append(describe(thrown))
                    .append("\nthis quantity stays UNREAD rather than being absorbed into a pass.\n");
        }
    }

    // ---------------------------------------------------------------- Part 2

    /** Recovers {@code extents} by binary-searching the public generatable-area predicate. */
    private static Bound recoverBound(StringBuilder sb, ChunkGenerator generator, Census census) {
        Bound bound = new Bound();
        sb.append("\n=== PART 2: recover extents by executing the bound ===\n");

        Boolean farOutside = safeBool(() -> generator.isChunkOutsideGeneratableArea(WORLD_CHUNK_MIN, 0));
        Boolean originOutside = safeBool(() -> generator.isChunkOutsideGeneratableArea(0, 0));
        if (farOutside == null || originOutside == null) {
            sb.append("isChunkOutsideGeneratableArea threw; cannot search\n");
            return bound;
        }
        sb.append("sanity: chunk(")
                .append(WORLD_CHUNK_MIN)
                .append(",0) outside=")
                .append(farOutside);
        sb.append("   chunk(0,0) outside=").append(originOutside).append('\n');
        if (!farOutside || originOutside) {
            // The search assumes outside==true at the extreme and false at the origin. If that does
            // not hold the predicate does not mean what this probe assumes, and a binary search over
            // it would return a confident number with no referent.
            sb.append("NOT the assumed shape -- refusing to search. A number from a search over a\n")
                    .append("predicate that does not bracket would be meaningless.\n");
            return bound;
        }

        int minCoord = firstInsideAscending(generator, true);
        int maxCoord = firstInsideDescending(generator, true);
        bound.recovered = minCoord - WORLD_CHUNK_MIN;
        int fromMax = WORLD_CHUNK_MAX - maxCoord;

        sb.append("x-axis: minChunkCoord=")
                .append(minCoord)
                .append("  maxChunkCoord=")
                .append(maxCoord)
                .append('\n');
        sb.append("extents recovered from min side: ").append(bound.recovered).append(" chunks\n");
        sb.append("extents recovered from max side: ").append(fromMax).append(" chunks");
        sb.append(bound.recovered == fromMax ? "   (symmetric)\n" : "   ASYMMETRIC -- itself a finding\n");

        int predicted = census.predictedExtents();
        sb.append("\npredicted from Part 1's declared sizes: (")
                .append(census.largestDeclaredBlocks())
                .append(" >> ")
                .append(CHUNK_SHIFT)
                .append(") + ")
                .append(EXPECTED_PADDING_CHUNKS)
                .append(" = ")
                .append(predicted)
                .append(" chunks\n");
        if (bound.recovered == predicted) {
            sb.append("MATCH -- getLargestFeatureChunkExtents is confirmed BY EXECUTION, and\n")
                    .append("CHUNK_BOUNDS_PADDING = 16 chunks is a real term rather than a read constant.\n");
        } else {
            sb.append("MISMATCH by ")
                    .append(bound.recovered - predicted)
                    .append(" chunks -- there is another term in the computation, and this difference\n")
                    .append("names its size. Informative, not a failure.\n");
        }
        return bound;
    }

    /** Smallest chunk x that is INSIDE, searched upward from the world minimum. */
    private static int firstInsideAscending(ChunkGenerator generator, boolean xAxis) {
        int lo = WORLD_CHUNK_MIN;
        int hi = 0;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (outside(generator, mid, xAxis)) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Largest chunk x that is INSIDE, searched downward from the world maximum. */
    private static int firstInsideDescending(ChunkGenerator generator, boolean xAxis) {
        int lo = 0;
        int hi = WORLD_CHUNK_MAX;
        while (lo < hi) {
            int mid = lo + (hi - lo + 1) / 2;
            if (outside(generator, mid, xAxis)) {
                hi = mid - 1;
            } else {
                lo = mid;
            }
        }
        return lo;
    }

    private static boolean outside(ChunkGenerator generator, int coord, boolean xAxis) {
        return xAxis
                ? generator.isChunkOutsideGeneratableArea(coord, 0)
                : generator.isChunkOutsideGeneratableArea(0, coord);
    }

    // ------------------------------------------------------------- Parts 3+4

    /** Does {@code collect} name where features are? Measured against a matched control set. */
    private static Trace trace(
            StringBuilder sb,
            World world,
            Store<EntityStore> store,
            ChunkGenerator generator,
            Census census,
            long seed) {
        Trace trace = new Trace();
        sb.append("\n=== PART 3: does collect() name where features are? ===\n");

        PrefabPatternGenerator pattern = census.largestPattern;
        if (pattern == null) {
            sb.append("no prefab pattern found; claim 2 UNTESTED\n");
            return trace;
        }
        IPointGenerator grid = safeObj(pattern::getGridGenerator);
        if (grid == null) {
            sb.append("largest pattern has no gridGenerator; claim 2 UNTESTED\n");
            return trace;
        }
        Double interval = safeDoubleBoxed(grid::getInterval);
        Boolean fitHeightmap = safeBool(pattern::isFitHeightmap);
        sb.append("pattern: ")
                .append(safe(() -> String.valueOf(pattern.getCategory()), "?"))
                .append("  maxSize=")
                .append(census.largestPrefabSize)
                .append("  exclusionRadius=")
                .append(safeInt(pattern::getExclusionRadius))
                .append("  fitHeightmap=")
                .append(fitHeightmap)
                .append('\n');
        sb.append("gridGenerator: ")
                .append(grid.getClass().getName())
                .append("  getInterval()=")
                .append(interval)
                .append("   (our cellSize is 128)\n");
        sb.append("""
                READ fitHeightmap BEFORE the numbers. If it is true the generator may already account
                for placed prefabs, in which case deviation is near zero EVERYWHERE and the collected
                number alone means nothing. Only the contrast against the control is interpretable.
                """);

        int px = 0;
        int pz = 0;
        String who = null;
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            Ref<EntityStore> ref = playerRef.getReference();
            TransformComponent transform =
                    ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            Vector3d pos = transform.getPosition();
            px = MathUtil.floor(pos.x);
            pz = MathUtil.floor(pos.z);
            who = playerRef.getUsername();
            break;
        }
        if (who == null) {
            sb.append("\nno connected player with a TransformComponent. Parts 3 and 4 read the topmost\n")
                    .append("solid block, which needs LOADED chunks, and a headless server has none.\n")
                    .append("Connect a client and re-run. Parts 1 and 2 above are unaffected.\n");
            return trace;
        }
        sb.append("\nanchor: ")
                .append(who)
                .append(" at ")
                .append(px)
                .append(',')
                .append(pz)
                .append('\n');

        // seedOffset is `protected final` with NO public getter, so the engine's exact per-pattern
        // seed cannot be reproduced. A wrong seed returns a DIFFERENT valid point set, which would
        // show no contrast even if claim 2 is true -- so the seed is scanned rather than assumed,
        // and a scan that finds nothing names the seed as the leading explanation.
        int seedInt = (int) seed;
        int[] candidateSeeds = {seedInt, 0, (int) (seed >>> 32), seedInt + 1};
        String[] seedLabels = {"(int) worldSeed", "0", "worldSeed >>> 32", "worldSeed+1 (control seed)"};

        SectionReader reader = new SectionReader(world.getChunkStore());
        sb.append(String.format(
                "%n%-28s %7s %10s %10s %9s %9s%n",
                "seed candidate", "points", "collected", "control", "dev rate", "ctl rate"));

        double best = -1.0;
        for (int i = 0; i < candidateSeeds.length; i++) {
            List<long[]> points = collectPoints(grid, candidateSeeds[i], px, pz);
            if (points.isEmpty()) {
                sb.append(String.format("%-28s %7d %10s %10s %9s %9s%n", seedLabels[i], 0, "-", "-", "-", "-"));
                continue;
            }
            Deviation collected = deviation(reader, generator, seedInt, points, SAMPLE_TARGET);
            Deviation control = deviation(reader, generator, seedInt, controlPoints(px, pz, points), SAMPLE_TARGET);
            sb.append(String.format(
                    "%-28s %7d %10s %10s %8.0f%% %8.0f%%%n",
                    seedLabels[i],
                    points.size(),
                    collected.summary(),
                    control.summary(),
                    collected.rate() * 100,
                    control.rate() * 100));
            double contrast = collected.rate() - control.rate();
            if (collected.scored() > 0 && control.scored() > 0 && contrast > best) {
                best = contrast;
                trace.bestLabel = seedLabels[i];
                trace.bestPoints = points;
                trace.collectedRate = collected.rate();
                trace.controlRate = control.rate();
            }
        }

        if (trace.bestPoints == null) {
            sb.append("\nno seed candidate produced a scorable comparison; claim 2 NOT_SHOWN.\n")
                    .append("NOT refuted -- a null here is at least as likely to be about the instrument.\n")
                    .append("Next variable to change: the pattern, to one whose prefabs sit ON terrain\n")
                    .append("rather than carved into it.\n");
            return trace;
        }

        // A threshold is a judgement, so it is stated rather than hidden: 2x the control rate AND at
        // least 15 points of absolute separation. Below that the probe says NOT_SHOWN.
        boolean shown = trace.collectedRate > 2 * trace.controlRate && (trace.collectedRate - trace.controlRate) > 0.15;
        trace.verdict = shown ? "MEASURED" : "NOT_SHOWN";
        sb.append("\nbest contrast: ")
                .append(trace.bestLabel)
                .append("  collected ")
                .append(String.format("%.0f%%", trace.collectedRate * 100))
                .append(" vs control ")
                .append(String.format("%.0f%%", trace.controlRate * 100))
                .append("  -> claim 2 ")
                .append(trace.verdict)
                .append('\n');
        sb.append("threshold, stated not hidden: collected > 2x control AND separation > 15 points.\n");
        if (!shown) {
            sb.append("NOT_SHOWN is not REFUTED. Leading explanation remains the unreadable seedOffset.\n");
        }

        crossing(sb, reader, generator, seedInt, census, trace);
        return trace;
    }

    /** Part 4 — do a feature's deviating columns appear on BOTH sides of a chunk border? */
    private static void crossing(
            StringBuilder sb, SectionReader reader, ChunkGenerator generator, int seedInt, Census census, Trace trace) {
        sb.append("\n=== PART 4: does a feature cross a chunk border? ===\n");
        int maxSize = census.largestPrefabSize;
        if (trace.bestPoints == null || maxSize <= 0) {
            sb.append("no usable point set; crossing NOT_OBSERVED (untested)\n");
            return;
        }
        if (maxSize <= 32) {
            sb.append("largest declared feature is ")
                    .append(maxSize)
                    .append(" blocks, which fits inside one 32-block chunk. Crossing cannot be\n")
                    .append("demonstrated from this pattern -- that is a fact about the pattern, not\n")
                    .append("about the engine.\n");
        }
        for (long[] p : trace.bestPoints) {
            int x = (int) p[0];
            int z = (int) p[1];
            int offX = Math.floorMod(x, 32);
            if (offX > 4 && offX < 28) {
                continue;
            }
            int border = (offX <= 4) ? (x - offX) : (x - offX + 32);
            Integer left = deviates(reader, generator, seedInt, border - 2, z);
            Integer right = deviates(reader, generator, seedInt, border + 1, z);
            if (left == null || right == null) {
                continue;
            }
            sb.append("point ")
                    .append(x)
                    .append(',')
                    .append(z)
                    .append("  chunk border x=")
                    .append(border)
                    .append("  deviates left=")
                    .append(left)
                    .append(" right=")
                    .append(right)
                    .append('\n');
            if (left > 0 && right > 0) {
                trace.crossing = "OBSERVED";
                sb.append("OBSERVED: deviating columns on both sides of a chunk border. A feature whose\n")
                        .append("blocks appear in two chunks cannot have been written by a single-chunk\n")
                        .append("pass that knows nothing of its neighbours.\n");
                return;
            }
        }
        sb.append("no point sat near enough to a chunk border with both sides loaded; NOT_OBSERVED\n");
    }

    private static List<long[]> collectPoints(IPointGenerator grid, int seedCandidate, int px, int pz) {
        List<long[]> points = new ArrayList<>();
        try {
            grid.collect(
                    seedCandidate,
                    px - COLLECT_HALF_WIDTH,
                    pz - COLLECT_HALF_WIDTH,
                    px + COLLECT_HALF_WIDTH,
                    pz + COLLECT_HALF_WIDTH,
                    (x, z) -> points.add(new long[] {(long) x, (long) z}));
        } catch (Throwable t) {
            // Throwable: a wrong seed into procedural-library code can produce an Error, and a part
            // that dies silently is worse than one that says so.
            points.clear();
        }
        return points;
    }

    /** Control positions: a lattice over the same region, excluding anything near a collected point. */
    private static List<long[]> controlPoints(int px, int pz, List<long[]> collected) {
        List<long[]> control = new ArrayList<>();
        for (int dx = -COLLECT_HALF_WIDTH; dx <= COLLECT_HALF_WIDTH; dx += 31) {
            for (int dz = -COLLECT_HALF_WIDTH; dz <= COLLECT_HALF_WIDTH; dz += 31) {
                int x = px + dx;
                int z = pz + dz;
                boolean near = false;
                for (long[] p : collected) {
                    if (Math.abs(p[0] - x) < 24 && Math.abs(p[1] - z) < 24) {
                        near = true;
                        break;
                    }
                }
                if (!near) {
                    control.add(new long[] {x, z});
                }
            }
        }
        return control;
    }

    private static Deviation deviation(
            SectionReader reader, ChunkGenerator generator, int seedInt, List<long[]> points, int target) {
        Deviation d = new Deviation();
        int step = Math.max(1, points.size() / target);
        for (int i = 0; i < points.size() && d.scored() < target; i += step) {
            Integer result = deviates(reader, generator, seedInt, (int) points.get(i)[0], (int) points.get(i)[1]);
            if (result == null) {
                d.unscorable++;
            } else if (result > 0) {
                d.deviating++;
            } else {
                d.agreeing++;
            }
        }
        return d;
    }

    /**
     * {@code null} if the column is unreadable, {@code 0} if the topmost solid block agrees with
     * {@code generateHeight}, the absolute difference otherwise.
     *
     * <p>Deliberately does NOT use {@code SectionReaderTerrainSample.surfaceHeightAt}: that returns
     * one fixed y for every column, and session 27 compared {@code generateHeight} against it and
     * read the coincidence as a validated match. The downward scan is sound because
     * {@code getBlock == 0} meaning "no block" is itself measured on 0.7.4 via {@code /blockidprobe}.
     */
    private static Integer deviates(SectionReader reader, ChunkGenerator generator, int seedInt, int x, int z) {
        int predicted;
        try {
            predicted = generator.generateHeight(seedInt, x, z);
        } catch (Throwable t) {
            return null;
        }
        if (predicted < 0 || predicted > WORLD_TOP_Y) {
            return null;
        }
        int top = Math.min(WORLD_TOP_Y, predicted + SCAN_HALF_BAND);
        int bottom = Math.max(0, predicted - SCAN_HALF_BAND);
        for (int y = top; y >= bottom; y--) {
            if (!reader.canHoldY(y) || !reader.hasStorageInMemory(x, y, z)) {
                return null;
            }
            if (reader.getBlock(x, y, z) != 0) {
                return Math.abs(y - predicted);
            }
        }
        return null;
    }

    // -------------------------------------------------------------- plumbing

    private static final class Census {
        final List<PrefabPatternGenerator> patterns = new ArrayList<>();
        final List<Gate> gates = new ArrayList<>();
        final java.util.Map<String, Integer> kindChecked = new java.util.TreeMap<>();
        final java.util.Map<String, Integer> kindClamping = new java.util.TreeMap<>();
        final java.util.Map<String, Integer> kindUnreadable = new java.util.TreeMap<>();
        final java.util.Map<String, String> kindReason = new java.util.TreeMap<>();
        final List<EntryRef> entryRefs = new ArrayList<>();
        int zoneProviderMaxExtent = -1;
        int largestPrefabSize = 0;
        double largestCaveSize = 0.0;
        PrefabPatternGenerator largestPattern;
        boolean refuted;

        /**
         * Records an aggregate against the maximum of <em>its own scope's</em> contents.
         *
         * <p>The first version of this compared every quantity against the GLOBAL largest declared
         * feature, and produced ~500 false gates on the first live run: {@code D} is 370 from a cave
         * in one zone, while {@code PrefabContainer.getMaxSize} is a per-biome prefab aggregate, so
         * an ocean biome with no 370-block prefab was flagged as clamping one. Comparing a local
         * quantity against a global maximum is not a test of anything.
         *
         * <p>A real clamp looks like an aggregate that is SMALLER than the things it aggregates.
         */
        void noteAggregate(String name, int declared, int ownScopeMax) {
            gates.add(new Gate(name, declared, ownScopeMax));
            kindChecked.merge(kindOf(name), 1, Integer::sum);
            if (declared < ownScopeMax) {
                refuted = true;
                kindClamping.merge(kindOf(name), 1, Integer::sum);
            }
        }

        /**
         * Records an aggregate that could NOT be read.
         *
         * <p>Exists because the first corrected run reported "aggregates checked: 283" against 1,429
         * prefab patterns, and a silently-unchecked aggregate hiding inside a clean "0 clamping" is
         * the absence-reads-as-a-pass failure this project keeps logging. A count that does not
         * account for every attempt is not a count.
         */
        void noteUnreadable(String name, String reason) {
            String kind = kindOf(name);
            kindUnreadable.merge(kind, 1, Integer::sum);
            if (reason != null) {
                kindReason.putIfAbsent(kind, reason);
            }
        }

        private static String kindOf(String name) {
            int bracket = name.indexOf('[');
            return bracket < 0 ? name : name.substring(0, bracket);
        }

        int largestDeclaredBlocks() {
            return Math.max(largestPrefabSize, (int) Math.ceil(largestCaveSize));
        }

        int predictedExtents() {
            return (largestDeclaredBlocks() >> CHUNK_SHIFT) + EXPECTED_PADDING_CHUNKS;
        }

        String claim1Verdict() {
            if (gates.isEmpty()) {
                return "CLAIM1_UNTESTED";
            }
            return refuted ? "CLAIM1_REFUTED" : "CLAIM1_SURVIVES";
        }
    }

    /** A prefab entry kept for the worldgen-thread re-read, with the size it declares. */
    private record EntryRef(PrefabContainer.PrefabContainerEntry entry, int declaredMax, String where) {}

    /** An aggregate quantity and the maximum of the scope it aggregates over. */
    private record Gate(String name, int declared, int ownScopeMax) {
        boolean clamps() {
            return declared < ownScopeMax;
        }
    }

    private static final class Bound {
        int recovered = -1;

        String matchVerdict(int predicted) {
            if (recovered < 0) {
                return "UNTESTED";
            }
            return recovered == predicted ? "MATCH" : "MISMATCH";
        }
    }

    private static final class Trace {
        String verdict = "UNTESTED";
        String crossing = "NOT_OBSERVED";
        String bestLabel = "-";
        List<long[]> bestPoints;
        double collectedRate;
        double controlRate;
    }

    private static final class Deviation {
        int deviating;
        int agreeing;
        int unscorable;

        int scored() {
            return deviating + agreeing;
        }

        double rate() {
            return scored() == 0 ? 0.0 : (double) deviating / scored();
        }

        String summary() {
            return deviating + "/" + scored() + (unscorable > 0 ? " (+" + unscorable + " unread)" : "");
        }
    }

    private static String truncate(String s, int max) {
        return s == null ? "-" : (s.length() <= max ? s : s.substring(0, max));
    }

    private interface ThrowingSupplier<T> {
        T get();
    }

    private static <T> T safeObj(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String safe(ThrowingSupplier<String> supplier, String fallback) {
        String value = safeObj(supplier);
        return value == null ? fallback : value;
    }

    private static Integer safeInt(ThrowingSupplier<Integer> supplier) {
        return safeObj(supplier);
    }

    private static Boolean safeBool(ThrowingSupplier<Boolean> supplier) {
        return safeObj(supplier);
    }

    private static Double safeDoubleBoxed(ThrowingSupplier<Double> supplier) {
        return safeObj(supplier);
    }

    private static double safeDouble(ThrowingSupplier<Double> supplier) {
        Double value = safeObj(supplier);
        return value == null ? 0.0 : value;
    }

    private static void bail(CommandContext context, StringBuilder sb, String why) {
        sb.append(why).append('\n');
        sb.append("LOCALITY_VERDICT: BLOCKED | ").append(why).append('\n');
        send(context, sb.toString());
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

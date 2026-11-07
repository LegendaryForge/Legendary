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
import com.hypixel.hytale.server.worldgen.container.UniquePrefabContainer;
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
     * Minimum scorable columns per set before a contrast may be called MEASURED.
     *
     * <p>The first run with a player reported MEASURED off <strong>8</strong> collected points, at
     * 5/8 against a control 10/48. Fisher one-sided on that single table is p ~ 0.026, and it was the
     * best of four seed candidates, so the corrected figure is ~0.10 -- not significant. A ratio
     * threshold with no sample-size floor and no multiple-comparison correction waves noise through.
     */
    private static final int MIN_SCORABLE = 30;

    /**
     * Minimum |actual - generateHeight| counted as a placed feature, in blocks.
     *
     * <p>Was effectively 1, which counted a one-block disagreement as a prefab. generateHeight
     * matched the topmost solid block 7/7 EXACTLY on 0.7.4, so a small difference is real signal
     * about the generator -- but it is not evidence of a structure, which is what Part 3 is asking.
     */
    private static final int DEVIATION_MIN_BLOCKS = 3;

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
    private static int seedInt(long seed) {
        return (int) seed;
    }

    private static Trace trace(
            StringBuilder sb,
            World world,
            Store<EntityStore> store,
            ChunkGenerator generator,
            Census census,
            long seed) {
        Trace trace = new Trace();
        sb.append("\n=== PART 3: does collect() name where features are? ===\n");

        // The player comes FIRST now: every measurement below reads loaded chunks, so without one
        // there is nothing to do and printing pattern details first only buried that.
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
            sb.append("no connected player with a TransformComponent. PART 3 reads the topmost solid\n")
                    .append("block, which needs LOADED chunks, and a headless server has none, so claim 2\n")
                    .append("stays UNTESTED. Parts 1, 1b, 2 and 4 do not need a player.\n");
            trace.crossing = crossing(sb, generator, seedInt(seed));
            return trace;
        }
        sb.append("anchor: ")
                .append(who)
                .append(" at ")
                .append(px)
                .append(',')
                .append(pz)
                .append('\n');

        // PATTERN SELECTION IS WHAT THIS TEST TURNS ON, and both obvious selectors are wrong.
        //
        // Picking the largest maxSize gave getInterval()=333 blocks: a region inside one player's
        // loaded radius holds ~8 of its points, so the comparison can never reach the sample floor,
        // and the first version reported MEASURED off exactly that.
        //
        // Picking the DENSEST then gave getInterval()=1, which collects 130,812 points in the same
        // region -- and produced control 0/0, because a control position must not sit near a
        // collected one and at interval 1 every position does. That failure is structural, not a
        // bug in the control: when candidate sites cover everything there is no "not a site" to
        // contrast against, so no contrast exists to measure.
        //
        // The usable interval is therefore a BAND, derived from the geometry rather than guessed:
        //   upper: enough points to clear the sample floor, (2*HW/iv)^2 >= 1.5*MIN_SCORABLE
        //   lower: sparse enough that excluded neighbourhoods leave a control set behind
        double bandMax = (2.0 * COLLECT_HALF_WIDTH) / Math.sqrt(1.5 * MIN_SCORABLE);
        double bandMin = 16.0;
        sb.append(String.format(
                "usable getInterval() band for this region (+/-%d blocks) and floor n=%d: %.0f..%.0f%n",
                COLLECT_HALF_WIDTH, MIN_SCORABLE, bandMin, bandMax));

        // Group by interval: 1,429 patterns share a handful of intervals, and testing 500 patterns
        // with identical spacing would just inflate the comparison count the correction divides by.
        java.util.Map<Double, PrefabPatternGenerator> byInterval = new java.util.TreeMap<>();
        java.util.Map<Double, Integer> intervalCounts = new java.util.TreeMap<>();
        for (PrefabPatternGenerator candidate : census.patterns) {
            IPointGenerator g = safeObj(candidate::getGridGenerator);
            if (g == null) {
                continue;
            }
            Double iv = safeDoubleBoxed(g::getInterval);
            if (iv == null || iv <= 0.0) {
                continue;
            }
            double key = Math.round(iv * 100.0) / 100.0;
            intervalCounts.merge(key, 1, Integer::sum);
            byInterval.putIfAbsent(key, candidate);
        }
        sb.append("distinct getInterval() values across ")
                .append(census.patterns.size())
                .append(" patterns: ")
                .append(intervalCounts.size())
                .append("  -> ");
        int shown = 0;
        for (java.util.Map.Entry<Double, Integer> e : intervalCounts.entrySet()) {
            if (shown++ > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%.0f(x%d)", e.getKey(), e.getValue()));
            if (shown >= 14) {
                sb.append(", ...");
                break;
            }
        }
        sb.append('\n');

        List<PrefabPatternGenerator> chosen = new ArrayList<>();
        for (java.util.Map.Entry<Double, PrefabPatternGenerator> e : byInterval.entrySet()) {
            if (e.getKey() >= bandMin && e.getKey() <= bandMax) {
                chosen.add(e.getValue());
            }
        }
        if (chosen.isEmpty()) {
            // Keep the two extremes so the output still SHOWS why nothing is in band, rather than
            // printing an empty table that reads like a probe that did not run.
            sb.append("NO pattern falls in the band -- the two extremes are tested anyway so the\n")
                    .append("table shows the failure rather than printing nothing.\n");
            if (!byInterval.isEmpty()) {
                chosen.add(byInterval.values().iterator().next());
                if (census.largestPattern != null) {
                    chosen.add(census.largestPattern);
                }
            }
        } else {
            sb.append("in-band patterns selected: ").append(chosen.size()).append('\n');
        }

        if (chosen.isEmpty()) {
            sb.append("no prefab pattern with a point generator; claim 2 UNTESTED\n");
            return trace;
        }

        // seedOffset is `protected final` with NO public getter, so the engine's exact per-pattern
        // seed cannot be reproduced. A wrong seed returns a DIFFERENT valid point set, which shows no
        // contrast even if claim 2 is true -- so the seed is scanned. Every (pattern, seed) pair
        // tried is a comparison, and the count is carried into the correction below: selecting the
        // best of N and then testing it at 0.05 is testing at 0.05*N.
        int seedInt = (int) seed;
        int[] candidateSeeds = {seedInt, 0, (int) (seed >>> 32), seedInt + 1};
        String[] seedLabels = {"(int) worldSeed", "0", "worldSeed>>>32", "worldSeed+1 (ctl seed)"};

        SectionReader reader = new SectionReader(world.getChunkStore());
        sb.append("deviation counted at >= ")
                .append(DEVIATION_MIN_BLOCKS)
                .append(" blocks from generateHeight; a set needs >= ")
                .append(MIN_SCORABLE)
                .append(" scorable columns to be eligible.\n");
        sb.append(String.format(
                "%n%-34s %-22s %7s %9s %9s %7s %7s %9s%n",
                "pattern (interval)", "seed", "points", "collected", "control", "dev%", "ctl%", "p (1-sided)"));

        int comparisons = 0;
        double bestP = 1.0;
        for (PrefabPatternGenerator candidate : chosen) {
            IPointGenerator grid = safeObj(candidate::getGridGenerator);
            if (grid == null) {
                continue;
            }
            Double iv = safeDoubleBoxed(grid::getInterval);
            Integer maxSize = safeInt(candidate::getMaxSize);
            Boolean fit = safeBool(candidate::isFitHeightmap);
            String label = String.format("iv=%.0f max=%s fit=%s", iv == null ? -1.0 : iv, maxSize, fit);
            for (int i = 0; i < candidateSeeds.length; i++) {
                List<long[]> points = collectPoints(grid, candidateSeeds[i], px, pz);
                if (points.isEmpty()) {
                    sb.append(String.format(
                            "%-34s %-22s %7d %9s %9s %7s %7s %9s%n", label, seedLabels[i], 0, "-", "-", "-", "-", "-"));
                    continue;
                }
                Deviation collected = deviation(reader, generator, seedInt, points, SAMPLE_TARGET);
                Deviation control = deviation(
                        reader,
                        generator,
                        seedInt,
                        controlPoints(px, pz, points, iv == null ? 1.0 : iv),
                        SAMPLE_TARGET);
                comparisons++;
                double pValue =
                        fisherRightTail(collected.deviating, collected.scored(), control.deviating, control.scored());
                boolean eligible = collected.scored() >= MIN_SCORABLE && control.scored() >= MIN_SCORABLE;
                sb.append(String.format(
                        "%-34s %-22s %7d %9s %9s %6.0f%% %6.0f%% %9s%n",
                        label,
                        seedLabels[i],
                        points.size(),
                        collected.summary(),
                        control.summary(),
                        collected.rate() * 100,
                        control.rate() * 100,
                        eligible ? String.format("%.4f", pValue) : "n too low"));
                if (eligible && pValue < bestP) {
                    bestP = pValue;
                    trace.bestLabel = label + " / " + seedLabels[i];
                    trace.bestPoints = points;
                    trace.collectedRate = collected.rate();
                    trace.controlRate = control.rate();
                    trace.bestEligible = true;
                }
            }
        }

        double corrected = Math.min(1.0, bestP * Math.max(1, comparisons));
        sb.append("\ncomparisons tried: ")
                .append(comparisons)
                .append("   best eligible p: ")
                .append(trace.bestEligible ? String.format("%.4f", bestP) : "none eligible")
                .append("   Bonferroni-corrected: ")
                .append(trace.bestEligible ? String.format("%.4f", corrected) : "-")
                .append('\n');

        if (!trace.bestEligible) {
            sb.append("""
                    claim 2 NOT_SHOWN -- no comparison reached the sample floor, so nothing here is
                    evidence either way. NOT refuted.

                    Why, concretely: this pattern family's getInterval() is large relative to the area
                    one player keeps loaded, so a region that fits inside loaded chunks cannot hold
                    enough of its points. The remedy is a denser pattern, which is why the densest is
                    selected first -- if even that is too sparse, the next variable is the region, and
                    that needs more loaded chunks, i.e. a second player or a larger view distance.
                    """);
            trace.crossing = crossing(sb, generator, seedInt);
            return trace;
        }

        trace.verdict = corrected < 0.05 ? "MEASURED" : "NOT_SHOWN";
        sb.append("best: ")
                .append(trace.bestLabel)
                .append("  collected ")
                .append(String.format("%.0f%%", trace.collectedRate * 100))
                .append(" vs control ")
                .append(String.format("%.0f%%", trace.controlRate * 100))
                .append("  -> claim 2 ")
                .append(trace.verdict)
                .append('\n');
        sb.append("""
                The test is Fisher's exact right tail on (collected deviating, control deviating),
                multiplied by the number of comparisons tried, against 0.05. The previous rule was
                "collected > 2x control AND separation > 15 points", which has no sample-size floor
                and no correction: it returned MEASURED on 5/8 vs 10/48, a single-table p of ~0.026
                that is ~0.10 once the four seed candidates it was the best of are accounted for.
                """);
        if (!"MEASURED".equals(trace.verdict)) {
            sb.append("NOT_SHOWN is not REFUTED. The unreadable seedOffset remains a live explanation.\n");
        }

        trace.crossing = crossing(sb, generator, seedInt);
        return trace;
    }
    /**
     * Part 4 — does a real placed prefab's bounding box span a chunk border?
     *
     * <p><strong>This replaces a test that was not sound.</strong> The first version inferred
     * placement from terrain: it read the topmost solid block at {@code border-2} and
     * {@code border+1} and called "both deviate from generateHeight" a crossing. It reported
     * OBSERVED, and the number that invalidates it is in Part 3's own table — control deviation
     * rates run <strong>15–31%</strong> everywhere, so the detector has a ~20% background
     * disagreement rate. Worse, those two columns are three blocks apart and adjacent terrain
     * columns are strongly correlated, so "both sides deviate" is close to the same measurement
     * twice rather than two independent events. Third instance in this one probe of a measurement
     * that does not test what it claims.
     *
     * <p>{@code UniquePrefabEntry} removes the inference entirely: it exposes
     * {@code getLowBoundX/Y/Z} and {@code getHighBoundX/Y/Z} — the actual bounding box of a placed
     * prefab — so crossing is exact arithmetic, {@code lowX >> 5 != highX >> 5}. No terrain read, no
     * noise floor, and no player needed.
     */
    private static String crossing(StringBuilder sb, ChunkGenerator generator, int seedInt) {
        sb.append("\n=== PART 4: does a placed prefab's bounding box span a chunk border? ===\n");
        try {
            CompletableFuture<String> future = generator.execute(0, (index, cg) -> {
                UniquePrefabContainer.UniquePrefabEntry[] entries = cg.getUniquePrefabs(seedInt);
                if (entries == null || entries.length == 0) {
                    return "NONE";
                }
                int crossX = 0;
                int crossZ = 0;
                int maxSpanBlocks = 0;
                int maxSpanChunks = 0;
                String example = "-";
                for (UniquePrefabContainer.UniquePrefabEntry entry : entries) {
                    if (entry == null) {
                        continue;
                    }
                    int lowX = entry.getLowBoundX();
                    int highX = entry.getHighBoundX();
                    int lowZ = entry.getLowBoundZ();
                    int highZ = entry.getHighBoundZ();
                    boolean spansX = (lowX >> CHUNK_SHIFT) != (highX >> CHUNK_SHIFT);
                    boolean spansZ = (lowZ >> CHUNK_SHIFT) != (highZ >> CHUNK_SHIFT);
                    if (spansX) {
                        crossX++;
                    }
                    if (spansZ) {
                        crossZ++;
                    }
                    int spanBlocks = Math.max(highX - lowX, highZ - lowZ);
                    int spanChunks = Math.max(
                            (highX >> CHUNK_SHIFT) - (lowX >> CHUNK_SHIFT),
                            (highZ >> CHUNK_SHIFT) - (lowZ >> CHUNK_SHIFT));
                    if (spanBlocks > maxSpanBlocks) {
                        maxSpanBlocks = spanBlocks;
                    }
                    if (spanChunks > maxSpanChunks && (spansX || spansZ)) {
                        maxSpanChunks = spanChunks;
                        example = String.format(
                                "%s x[%d..%d] chunks[%d..%d]  z[%d..%d] chunks[%d..%d]",
                                entry.getName(),
                                lowX,
                                highX,
                                lowX >> CHUNK_SHIFT,
                                highX >> CHUNK_SHIFT,
                                lowZ,
                                highZ,
                                lowZ >> CHUNK_SHIFT,
                                highZ >> CHUNK_SHIFT);
                    }
                }
                return entries.length + "|" + crossX + "|" + crossZ + "|" + maxSpanBlocks + "|" + maxSpanChunks + "|"
                        + example;
            });
            String raw = future.get(60, TimeUnit.SECONDS);
            if ("NONE".equals(raw)) {
                sb.append("getUniquePrefabs(seed) returned no entries, so there is nothing to measure.\n")
                        .append("crossing NOT_OBSERVED (untested, not refuted)\n");
                return "NOT_OBSERVED";
            }
            String[] f = raw.split("\\|", 6);
            int total = Integer.parseInt(f[0]);
            int crossX = Integer.parseInt(f[1]);
            int crossZ = Integer.parseInt(f[2]);
            sb.append("unique prefab entries: ")
                    .append(total)
                    .append("   spanning a chunk border in x: ")
                    .append(crossX)
                    .append("   in z: ")
                    .append(crossZ)
                    .append('\n');
            sb.append("largest bounding box: ")
                    .append(f[3])
                    .append(" blocks, ")
                    .append(f[4])
                    .append(" chunk border(s) crossed\n");
            sb.append("example: ").append(f[5]).append('\n');
            if (crossX > 0 || crossZ > 0) {
                sb.append("""
                        OBSERVED, by arithmetic on a real prefab's own bounds rather than by inferring
                        placement from terrain. A feature whose declared box lies in two chunks cannot
                        have been written by a single-chunk pass that knows nothing of its neighbours,
                        which is what #149's locality claim asserts.
                        """);
                return "OBSERVED";
            } else {
                sb.append("""
                        NOT_OBSERVED: every unique prefab's box falls inside one chunk. That is a fact
                        about the unique-prefab set at this seed, not a refutation -- these are the
                        hand-placed landmarks, and the ordinary per-biome patterns are a different
                        population this route does not see.
                        """);
                return "NOT_OBSERVED";
            }
        } catch (Throwable thrown) {
            sb.append("getUniquePrefabs via the generator pool failed: ")
                    .append(describe(thrown))
                    .append("\ncrossing NOT_OBSERVED (untested, not refuted)\n");
        }
        return "NOT_OBSERVED";
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

    /**
     * Control positions: a lattice over the same region, excluding neighbourhoods of collected
     * points.
     *
     * <p>The exclusion radius SCALES with the collected spacing. A fixed 24 blocks returned an empty
     * control set against a pattern whose {@code getInterval()} is 1 -- every candidate position was
     * within 24 blocks of some collected point -- and an empty control reads as a failed test rather
     * than as the structural fact it is: when candidate sites cover everything, there is no contrast
     * to measure. Scaling keeps the control non-empty wherever one can exist, and the band check in
     * Part 3 is what rules out the case where one cannot.
     */
    private static List<long[]> controlPoints(int px, int pz, List<long[]> collected, double interval) {
        int exclusion = (int) Math.max(3.0, Math.min(24.0, interval / 4.0));
        int step = (int) Math.max(17.0, Math.min(47.0, interval / 2.0));
        List<long[]> control = new ArrayList<>();
        for (int dx = -COLLECT_HALF_WIDTH; dx <= COLLECT_HALF_WIDTH; dx += step) {
            for (int dz = -COLLECT_HALF_WIDTH; dz <= COLLECT_HALF_WIDTH; dz += step) {
                int x = px + dx;
                int z = pz + dz;
                boolean near = false;
                for (long[] point : collected) {
                    if (Math.abs(point[0] - x) < exclusion && Math.abs(point[1] - z) < exclusion) {
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
            } else if (result >= DEVIATION_MIN_BLOCKS) {
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
        boolean bestEligible;
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

    /**
     * Fisher's exact test, right tail: P(at least {@code a} of {@code n} deviating), given the
     * pooled rate from the control.
     *
     * <p>Exists because a ratio threshold is not a test. The first run with a player called 5/8
     * against 10/48 a MEASURED contrast; this returns 0.0256 for that table, which is ~0.10 after
     * correcting for having picked the best of four seed candidates.
     */
    private static double fisherRightTail(int a, int n, int c, int m) {
        int deviating = a + c;
        int total = n + m;
        if (n <= 0 || m <= 0 || deviating <= 0 || deviating > total) {
            return 1.0;
        }
        double tail = 0.0;
        int upper = Math.min(n, deviating);
        for (int k = a; k <= upper; k++) {
            tail += Math.exp(logChoose(deviating, k) + logChoose(total - deviating, n - k) - logChoose(total, n));
        }
        return Math.min(1.0, Math.max(0.0, tail));
    }

    private static double logChoose(int n, int k) {
        if (k < 0 || k > n) {
            return Double.NEGATIVE_INFINITY;
        }
        return logFactorial(n) - logFactorial(k) - logFactorial(n - k);
    }

    private static double logFactorial(int n) {
        double sum = 0.0;
        for (int i = 2; i <= n; i++) {
            sum += Math.log(i);
        }
        return sum;
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

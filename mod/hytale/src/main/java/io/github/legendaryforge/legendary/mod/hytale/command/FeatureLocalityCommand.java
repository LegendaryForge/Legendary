package io.github.legendaryforge.legendary.mod.hytale.command;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.procedurallib.logic.point.IPointGenerator;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;
import com.hypixel.hytale.server.worldgen.ChunkGeneratorResource;
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

/**
 * Debug command: {@code /featurelocality}
 *
 * <p>Runs the probe specified in {@code
 * docs/superpowers/specs/2026-11-07-feature-locality-confirming-probe-design.md}, which confirmed
 * the worldgen-locality finding merged as PR #149. Most of that finding is now
 * {@code [measured, 0.7.6]}; this command is what measured it, and it decides whether residue can
 * be derived at generation time.
 *
 * <p><strong>Part 3 was replaced</strong> by {@code
 * docs/superpowers/specs/2026-11-07-collect-call-edge-claim2-design.md}. Its terrain-deviation
 * contrast could not work — a ~20% background deviation rate, and no pattern both dense enough for
 * statistics and large enough to detect — and the instrument named to replace it compared two
 * different placement systems. What is there now is the {@code collect()} call edge in
 * {@code PrefabPopulator}, plus the runtime preconditions it depends on.
 *
 * <p><strong>It needs no declared feature.</strong> #149's probe sentence was "declare a trivial
 * feature with a known maxSize and check its blocks appear in chunks other than its point's", which
 * assumes a mod can register into the prefab path — an open claim, and one the adopted architecture
 * has already declined. The base game ships thousands of declared features and the whole walk down
 * to their sizes is public API, so the engine's own features answer the question instead.
 *
 * <p>Four independent parts, and <strong>none of them needs a connected player</strong> since Part 3
 * stopped reading terrain. Each reports on its own; a part that cannot run <em>says so</em> rather
 * than being omitted, because an absent line must never read as a passing one.
 *
 * <ol>
 *   <li><strong>Extent census.</strong> Every extent-like quantity the live generator exposes. Any
 *       one of them smaller than the largest declared feature size refutes claim 1.
 *   <li><strong>World-bound recovery.</strong> {@code isChunkOutsideGeneratableArea} is public even
 *       though {@code minChunkCoord} is not, so binary-searching it recovers the {@code extents} the
 *       constructor computed — executing {@code getLargestFeatureChunkExtents} rather than reading
 *       it.
 *   <li><strong>The {@code collect} call edge.</strong> Is {@code IPointGenerator.collect} what
 *       per-chunk prefab placement calls? The edge is in {@code PrefabPopulator.collectPrefabs};
 *       this part measures that the live world can answer that exact call, for every prefab entry,
 *       at real chunk coordinates.
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

    /** {@code ChunkUtil.maxBlock(c)} is {@code (c << 5) + 31}; this is the 31. */
    private static final int CHUNK_MAX_OFFSET = 31;

    /**
     * The chunks Part 3 reproduces the populator's {@code collect} call at.
     *
     * <p>Origin alone would not show that the region TRACKS the chunk: a degenerate region at
     * {@code (0,0)} is indistinguishable from a correct one, since {@code 0 << 5} is 0. The second
     * chunk is asymmetric and negative in z for that reason.
     */
    private static final int[][] CALL_CHUNKS = {{0, 0}, {100, -37}};

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

        // Parts 3 and 4 are SIBLINGS now. Part 4 used to be called from inside Part 3, which is how
        // it came to be skipped on one path and how its verdict came to be read off a field Part 3
        // owned. Each part returns its own verdict and the caller assigns it.
        String claim2 = callEdge(sb, generator, census, seed);
        String crossing = crossing(sb, generator, seedInt(seed));

        sb.append("\nLOCALITY_VERDICT: ")
                .append(census.claim1Verdict())
                .append(" | extents recovered=")
                .append(bound.recovered)
                .append(" predicted=")
                .append(census.predictedExtents())
                .append(' ')
                .append(bound.matchVerdict(census.predictedExtents()))
                .append(" | claim2 ")
                .append(claim2)
                .append(" | crossing ")
                .append(crossing)
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

    // --------------------------------------------------------------- Part 3

    private static int seedInt(long seed) {
        return (int) seed;
    }

    /**
     * Part 3 — is {@code collect()} what per-chunk prefab placement calls? (claim 2)
     *
     * <p><strong>This replaces the terrain-deviation test, and also replaces the instrument the
     * previous session named to replace it.</strong> That instrument was {@code
     * UniquePrefabEntry.getPosition()} against {@code collect()} output, and it compares two
     * different placement systems: {@code UniquePrefabGenerator} has no {@code IPointGenerator} at
     * all — not on the generator, not on its {@code UniquePrefabConfiguration}, which places by
     * anchor + maxDistance + maxAttempts rejection sampling against a {@code Random}. The engine
     * runs the two as separate steps of one pass ({@code generatePrefabs} from the collect-filled
     * {@code prefabs} list, then {@code generateUniquePrefabs} from {@code getUniquePrefabs}), so
     * the comparison would have disagreed by construction and reported CLAIM2_REFUTED. That would
     * have been the fourth cross-scope comparison in this probe's history.
     *
     * <p><strong>What answers claim 2 is a call edge</strong>, decompiled from {@code 0.7.6}:
     *
     * <pre>
     * ChunkGeneratorExecution -&gt; PrefabPopulator.populate(seed, execution)
     *   -&gt; ChunkGenerator.getResource().prefabPopulator.run(seed, execution)
     *        -&gt; collectBiomes(...)
     *        -&gt; collectPrefabs(...)   // entry.getPrefabPatternGenerator()
     *                                 //      .getGridGenerator()
     *                                 //      .collect(seed, minX, minZ, maxX, maxZ, this::collectPrefab)
     *        -&gt; generatePrefabs(...)       // places from `prefabs`
     *        -&gt; generateUniquePrefabs(...) // the OTHER system
     * </pre>
     *
     * <p>and the region is the chunk's own bounds expanded by the entry's own extent:
     * {@code [ChunkUtil.minBlock(cx) - entry.getExtents(), ChunkUtil.maxBlock(cx) + entry.getExtents()]},
     * where {@code minBlock(c) = c << 5} and {@code maxBlock(c) = (c << 5) + 31}.
     *
     * <p><strong>The expansion term is {@code getExtents()}, not {@code getMaxSize()}.</strong>
     * {@code maxSize} feeds {@code getLargestFeatureChunkExtents} and therefore the world bound;
     * {@code getExtents()} feeds this per-chunk query region. Part 1b calls {@code getExtents()}
     * characterisation with no known role — this is its role, and it is also why it needs a worldgen
     * thread: it is read inside the populator, which only ever runs on one.
     *
     * <p>The edge itself is static evidence. What this part MEASURES is that the live world can
     * actually answer it: the populator instance exists on the resource, every prefab entry has a
     * usable grid generator, and the exact call the populator makes completes for every entry at
     * real chunk coordinates.
     *
     * <p><strong>It does not prove the engine used those points to place anything.</strong> The test
     * that would close that is containment — every pattern prefab placed in a chunk sits at one of
     * the collected points — and it is blocked on enumerating placed prefabs, which no public API
     * appears to offer: {@code PrefabContainerEntry} holds no instance records, {@code run} clears
     * its candidate list on the way out, and {@code getPrefabLoadingCache()} is a type-keyed
     * template cache rather than a placement record.
     */
    @SuppressWarnings("removal") // ChunkGenerator.execute, as Part 1b: deliberate, and reported
    private static String callEdge(StringBuilder sb, ChunkGenerator generator, Census census, long seed) {
        sb.append("\n=== PART 3: is collect() what per-chunk prefab placement calls? ===\n");
        sb.append("""
                The call edge, decompiled from 0.7.6 bytecode -- this is the evidence, and it is
                static. Full listing in the spec and in the engine doc.

                  ChunkGeneratorExecution -> PrefabPopulator.populate(seed, execution)
                    -> ChunkGenerator.getResource().prefabPopulator.run(seed, execution)
                         -> collectPrefabs(seed, execution)
                              entry.getPrefabPatternGenerator()
                                   .getGridGenerator()
                                   .collect(seed,
                                            minBlock(cx) - entry.getExtents(),
                                            minBlock(cz) - entry.getExtents(),
                                            maxBlock(cx) + entry.getExtents(),
                                            maxBlock(cz) + entry.getExtents(),
                                            this::collectPrefab)
                         -> generatePrefabs(...)        places from the collect-filled list
                         -> generateUniquePrefabs(...)  the OTHER placement system

                minBlock(c) = c << 5 and maxBlock(c) = (c << 5) + 31, both read from their bodies.
                So the region is THE CHUNK expanded by the entry's own extent -- and the expansion
                term is getExtents(), not getMaxSize(). What follows measures that the live world
                can answer that exact call.
                """);

        if (census.entryRefs.isEmpty()) {
            sb.append("no prefab entries retained by Part 1, so there is nothing to call. UNTESTED\n");
            return "UNTESTED";
        }

        Edge edge;
        try {
            CompletableFuture<Edge> future = generator.execute(0, (index, cg) -> {
                Edge e = new Edge();

                ChunkGeneratorResource resource;
                try {
                    resource = ChunkGenerator.getResource();
                } catch (Throwable thrown) {
                    e.resourceFail = describe(thrown);
                    return e;
                }
                if (resource == null) {
                    e.resourceFail = "getResource() returned null";
                    return e;
                }
                e.resourceOk = true;
                try {
                    e.populatorOk = resource.prefabPopulator != null;
                    if (!e.populatorOk) {
                        e.resourceFail = "resource.prefabPopulator is null";
                    }
                } catch (Throwable thrown) {
                    e.resourceFail = describe(thrown);
                    return e;
                }

                // The populator's own arguments, at two real chunks. Origin alone would not show
                // that the region TRACKS the chunk, and a degenerate region there would be
                // indistinguishable from a correct one.
                for (int chunkIndex = 0; chunkIndex < CALL_CHUNKS.length; chunkIndex++) {
                    int[] chunk = CALL_CHUNKS[chunkIndex];
                    for (EntryRef ref : census.entryRefs) {
                        PrefabPatternGenerator pattern;
                        IPointGenerator grid;
                        int extents;
                        try {
                            pattern = ref.entry().getPrefabPatternGenerator();
                            if (pattern == null) {
                                e.gridNull++;
                                e.gridReason = "getPrefabPatternGenerator() returned null";
                                continue;
                            }
                            grid = pattern.getGridGenerator();
                            if (grid == null) {
                                e.gridNull++;
                                e.gridReason = "getGridGenerator() returned null";
                                continue;
                            }
                            extents = ref.entry().getExtents();
                        } catch (Throwable thrown) {
                            e.gridThrew++;
                            e.gridReason = describe(thrown);
                            continue;
                        }
                        e.gridOk++;

                        double interval;
                        try {
                            interval = grid.getInterval();
                        } catch (Throwable thrown) {
                            e.intervalBad++;
                            e.gridReason = describe(thrown);
                            continue;
                        }
                        if (!Double.isFinite(interval) || interval <= 0.0) {
                            e.intervalBad++;
                            e.gridReason = "getInterval() = " + interval;
                            continue;
                        }
                        e.intervalOk++;

                        // extents vs maxSize, so correction 1 is visible in the output.
                        //
                        // ONCE PER ENTRY, not once per (entry, chunk): this is a property of the
                        // entry and the loop visits each one twice. Counting it inside the chunk
                        // loop would print exactly 2x the entry count, which would look like a
                        // census over twice as many entries as exist.
                        if (chunkIndex == 0) {
                            if (extents > ref.declaredMax()) {
                                e.extAbove++;
                            } else if (extents < ref.declaredMax()) {
                                e.extBelow++;
                            } else {
                                e.extEqual++;
                            }
                        }

                        int minX = (chunk[0] << CHUNK_SHIFT) - extents;
                        int minZ = (chunk[1] << CHUNK_SHIFT) - extents;
                        int maxX = (chunk[0] << CHUNK_SHIFT) + CHUNK_MAX_OFFSET + extents;
                        int maxZ = (chunk[1] << CHUNK_SHIFT) + CHUNK_MAX_OFFSET + extents;
                        int[] count = {0};
                        try {
                            grid.collect(seedInt(seed), minX, minZ, maxX, maxZ, (x, z) -> count[0]++);
                            e.callOk++;
                            e.callPoints += count[0];
                            // THE WORKED EXAMPLE EXISTS TO MAKE THE EXPANSION VISIBLE, so it is
                            // selected for that and not for density. Picking the smallest interval
                            // chose extents=0 at chunk(0,0): region [0..31], i.e. the chunk itself
                            // with no expansion, at the one coordinate where `c << 5` is 0 -- the
                            // degenerate case CALL_CHUNKS was written to avoid, demonstrating
                            // neither of the two things the example is for. Selecting by an extreme
                            // and getting an uninformative one is correction #5 of the previous
                            // spec. Prefer the non-origin chunk, then the largest extents.
                            int score = chunkIndex * 1_000_000 + extents;
                            if (count[0] > 0 && score > e.sampleScore) {
                                e.sampleScore = score;
                                e.sampleInterval = interval;
                                e.sampleWhere = ref.where();
                                e.sampleExtents = extents;
                                e.sampleMaxSize = ref.declaredMax();
                                e.sampleChunkX = chunk[0];
                                e.sampleChunkZ = chunk[1];
                                e.sampleMinX = minX;
                                e.sampleMaxX = maxX;
                                e.samplePoints = count[0];
                            }
                        } catch (Throwable thrown) {
                            // Throwable, not Exception: procedural-library code can raise an Error,
                            // and a swallowed reason is the defect Part 1b had to have corrected.
                            e.callThrew++;
                            e.callReason = describe(thrown);
                        }
                    }
                }
                return e;
            });
            edge = future.get(120, TimeUnit.SECONDS);
        } catch (Throwable thrown) {
            sb.append("execute() on the generator pool failed: ")
                    .append(describe(thrown))
                    .append("\nclaim 2 stays UNTESTED rather than being absorbed into a pass.\n");
            return "UNTESTED";
        }

        sb.append("resource: ")
                .append(edge.resourceOk ? "ChunkGenerator.getResource() OK" : "UNAVAILABLE")
                .append("   resource.prefabPopulator: ")
                .append(edge.populatorOk ? "present" : "ABSENT")
                .append(edge.resourceOk && edge.populatorOk ? "" : "   (" + edge.resourceFail + ")")
                .append('\n');

        int attempts = census.entryRefs.size() * CALL_CHUNKS.length;
        sb.append("entries x chunks attempted: ")
                .append(attempts)
                .append("   (")
                .append(census.entryRefs.size())
                .append(" entries x ")
                .append(CALL_CHUNKS.length)
                .append(" chunks)\n");
        sb.append("grid generator present: ")
                .append(edge.gridOk)
                .append("   null: ")
                .append(edge.gridNull)
                .append("   threw: ")
                .append(edge.gridThrew)
                .append('\n');
        sb.append("getInterval() usable: ")
                .append(edge.intervalOk)
                .append("   unusable: ")
                .append(edge.intervalBad)
                .append('\n');
        sb.append("collect() with the populator's own arguments -- completed: ")
                .append(edge.callOk)
                .append("   threw: ")
                .append(edge.callThrew)
                .append("   points returned: ")
                .append(edge.callPoints)
                .append('\n');
        if (edge.gridNull + edge.gridThrew + edge.intervalBad + edge.callThrew > 0) {
            sb.append("first failure reason: ")
                    .append(truncate(edge.gridReason.equals("-") ? edge.callReason : edge.gridReason, 160))
                    .append('\n');
        }

        sb.append("getExtents() vs its entry's declared maxSize: above=")
                .append(edge.extAbove)
                .append(" equal=")
                .append(edge.extEqual)
                .append(" below=")
                .append(edge.extBelow)
                .append("  -- two different quantities, and the per-chunk region uses the FORMER\n");

        if (edge.samplePoints > 0) {
            sb.append(String.format(
                    "worked example: %s  interval=%.0f extents=%d maxSize=%d  chunk(%d,%d)%n",
                    truncate(edge.sampleWhere, 40),
                    edge.sampleInterval,
                    edge.sampleExtents,
                    edge.sampleMaxSize,
                    edge.sampleChunkX,
                    edge.sampleChunkZ));
            sb.append(String.format(
                    "  x region = [%d << 5) - %d .. (%d << 5) + 31 + %d] = [%d .. %d]  width %d -> %d points%n",
                    edge.sampleChunkX,
                    edge.sampleExtents,
                    edge.sampleChunkX,
                    edge.sampleExtents,
                    edge.sampleMinX,
                    edge.sampleMaxX,
                    edge.sampleMaxX - edge.sampleMinX + 1,
                    edge.samplePoints));
        }

        String verdict = edge.verdict();
        sb.append("\nclaim 2 ")
                .append(verdict)
                .append(" -- ")
                .append(
                        switch (verdict) {
                            case "CALL_EDGE" -> """
                    the edge is recorded and every runtime precondition holds: the populator exists
                    on the live resource, every prefab entry answers getGridGenerator() with a usable
                    interval, and the exact call collectPrefabs makes completed for every one of them
                    at real chunk coordinates.

                    This is NOT [measured] for "the engine placed something from these points". It is
                    the call edge plus the measured fact that the live world can answer it. The
                    containment test that would close the rest is blocked on an enumeration API that
                    does not appear to exist -- see the spec.
                    """;
                            case "PRECONDITION_FAILED" -> """
                    a precondition the edge depends on does not hold in the live world. Read the
                    counts above for which: an absent populator, a null grid generator, an unusable
                    interval, or a collect() that threw. Any of these is a real finding -- the
                    per-chunk query model would not hold universally -- and NOT a refutation of the
                    call edge, which is in the bytecode either way.
                    """;
                            default -> """
                    the worldgen thread was not reachable, so nothing above ran. UNTESTED is not
                    evidence either way.
                    """;
                        });
        return verdict;
    }

    /**
     * Part 4 — does a real placed prefab's bounding box span a chunk border?
     *
     * <p><strong>This replaces a test that was not sound.</strong> The first version inferred
     * placement from terrain: it read the topmost solid block at {@code border-2} and
     * {@code border+1} and called "both deviate from generateHeight" a crossing. It reported
     * OBSERVED, and the number that invalidates it was measured by the Part 3 that has since been
     * retired: control deviation rates ran <strong>15–31%</strong> everywhere, so the detector has
     * a ~20% background disagreement rate. That table is in the 2026-11-07 probe spec's § Results,
     * not in this command's output any more. Worse, those two columns are three blocks apart and adjacent terrain
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

    /**
     * Part 3's result, built on the worldgen thread and formatted on the command thread.
     *
     * <p>Built-then-formatted rather than appended-in-place because the lambda runs on the
     * generator pool. Part 1b established the same shape.
     */
    private static final class Edge {
        boolean resourceOk;
        boolean populatorOk;
        String resourceFail = "-";
        int gridOk;
        int gridNull;
        int gridThrew;
        String gridReason = "-";
        int intervalOk;
        int intervalBad;
        int callOk;
        int callThrew;
        int callPoints;
        String callReason = "-";
        int extAbove;
        int extEqual;
        int extBelow;
        int sampleScore = -1;
        double sampleInterval;
        String sampleWhere = "-";
        int sampleExtents;
        int sampleMaxSize;
        int sampleChunkX;
        int sampleChunkZ;
        int sampleMinX;
        int sampleMaxX;
        int samplePoints;

        /**
         * The verdict, computed from the counts the body printed.
         *
         * <p>Correction #7 of the previous spec was a verdict line that contradicted its own body,
         * because a refactor stopped something setting the field the summary read. This reads only
         * fields the body above it has already printed.
         *
         * <p>A zero point count is NOT a failure: a sparse pattern legitimately has no candidate in
         * a 32-block chunk. Only a throw is.
         */
        String verdict() {
            if (!resourceOk || !populatorOk) {
                return "PRECONDITION_FAILED";
            }
            if (gridOk == 0 || callOk == 0) {
                return "PRECONDITION_FAILED";
            }
            if (gridNull > 0 || gridThrew > 0 || intervalBad > 0 || callThrew > 0) {
                return "PRECONDITION_FAILED";
            }
            return "CALL_EDGE";
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

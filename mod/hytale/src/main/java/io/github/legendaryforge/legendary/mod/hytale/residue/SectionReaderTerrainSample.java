package io.github.legendaryforge.legendary.mod.hytale.residue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.environment.config.Environment;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The engine side of {@link TerrainSample}. Resolves ids to names so {@code :core} never sees one.
 *
 * <p>Compiling this against the real jar is itself the point: every read below was a {@code [javap]}
 * hypothesis under the project's standing method rule, and a module that compiles proves existence
 * and shape — not behaviour, which is what the probe run is for.
 *
 * <p><strong>The reference plane is a deliberate v1 limitation.</strong> {@link #surfaceHeightAt}
 * returns one fixed y for every column rather than following terrain. {@code IHeightmap.getHeight}
 * exists and would be the right source, but the accessor that hands a caller the <em>world's</em>
 * heightmap is unresolved — {@code IHeightmap.forLoader} constructs one rather than fetching it — and
 * inventing a downward block scan would have required assuming which block id means empty, an
 * assumption nothing here has verified. So the probe samples a slab at the operator's own elevation
 * and says so in its output. Finding the real heightmap accessor is the next probe's question, not a
 * silent approximation inside this one.
 */
public final class SectionReaderTerrainSample implements TerrainSample {

    private final SectionReader reader;
    private final Store<ChunkStore> sectionStore;
    private final int referencePlaneY;

    public SectionReaderTerrainSample(ChunkStore chunkStore, int referencePlaneY) {
        this.reader = new SectionReader(chunkStore);
        this.sectionStore = chunkStore.getStore();
        this.referencePlaneY = referencePlaneY;
    }

    @Override
    public boolean isLoaded(int x, int y, int z) {
        return reader.canHoldY(y) && reader.hasStorageInMemory(x, y, z);
    }

    @Override
    public Optional<String> fluidAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return Optional.empty();
        }
        int id = reader.getFluidId(x, y, z);
        if (id == Fluid.EMPTY_ID) {
            return Optional.empty();
        }
        try {
            Fluid asset = Fluid.getAssetMap().getAsset(id);
            return asset == null
                    ? Optional.empty()
                    : Optional.ofNullable(asset.getId()).map(String::valueOf);
        } catch (RuntimeException e) {
            // An id the asset map cannot resolve is reported as absent rather than thrown. A probe
            // that dies on one unexpected index tells you nothing about the other several hundred
            // reads in the same run.
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> environmentAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return Optional.empty();
        }
        try {
            Environment asset = Environment.getAssetMap().getAsset(reader.getEnvironment(x, y, z));
            return asset == null
                    ? Optional.empty()
                    : Optional.ofNullable(asset.getId()).map(String::valueOf);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Sky light via the section's <em>global</em> light.
     *
     * <p>Global rather than local because §5's argument for sky light is that it is "deterministic,
     * identical for every player, and immune to a player lighting a cave", and the 2026-10-21
     * measurement found that true of the global channel: 15 at the surface at both midday and night,
     * 0 beside a lit torch underground. World coordinates are passed because the same measurement
     * found these accessors mask internally.
     */
    @Override
    public int skyLightAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return -1;
        }
        Ref<ChunkStore> sectionRef = reader.sectionAt(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return -1;
        }
        BlockSection section = sectionStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null || !section.hasGlobalLight()) {
            return -1;
        }
        ChunkLightData global = section.getGlobalLight();
        return global.getSkyLight(x, y, z);
    }

    /**
     * The block id meaning "no block here" — air or void.
     *
     * <p><strong>Measured, not assumed</strong>, via {@code /blockidprobe} on 0.7.4 against three
     * positions whose answers were known from standing there: the player's own feet level read
     * {@code 0}, open sky far above terrain read {@code 0}, and bedrock depth read {@code 445}.
     * Corroborated by mechanism rather than only by agreement — at {@code y=16} the same column read
     * {@code getBlock=0} alongside {@code getFluidId=8}, so a fluid occupies a position
     * {@code getBlock} calls empty, which a real block type could not do.
     *
     * <p>It is measured because a wrong value here inverts Earth and Wind <em>simultaneously</em>:
     * Earth counts solid points and Wind counts open ones, so the build stays green and the anchors
     * stay plausible while every one of them is in the wrong kind of place.
     */
    private static final int NO_BLOCK = 0;

    @Override
    public boolean isSolidAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return false;
        }
        // A fluid-filled position is NOT solid: getBlock reports 0 there, as measured at y=16 with
        // getFluidId=8. That is the wanted semantics -- Earth must not count a flooded cavity as
        // rock -- and it is why this reads getBlock rather than negating a fluid check.
        return reader.getBlock(x, y, z) != NO_BLOCK;
    }

    @Override
    public OptionalInt surfaceHeightAt(int x, int z) {
        return isLoaded(x, referencePlaneY, z) ? OptionalInt.of(referencePlaneY) : OptionalInt.empty();
    }

    /**
     * Mean lightning-bearing-weather share across all of this environment's forecast slots.
     *
     * <p>Read from the environment's own {@code WeatherForecasts} weights, so this is authored data
     * rather than inference: {@code Env_Zone4_Crucible} carries {@code Zone4_Swamp_Storm} at weight
     * 50 against {@code Zone4_Swamp_Gloomy} at 50.
     *
     * <p><strong>Three approximations, each deliberate and each the probe's to expose.</strong>
     *
     * <p>First, <strong>storm is not lightning.</strong> {@code Zone2_Sand_Storm} and
     * {@code Zone3_Snow_Storm} are storms with no thunder, so counting every {@code Storm} would
     * site Lightning in a desert. The weather <em>tags</em> discriminate cleanly —
     * {@code Rain:[Storm]} and {@code Thunderstorm} against {@code Sandstorm} and
     * {@code Snow:[Storm]} — but there is no tag accessor on the runtime {@code Weather} object
     * either, so the id is all there is. {@link #isLightningBearing} therefore matches on name and
     * excludes the two known non-thunder forms by name as well.
     *
     * <p>Second, <strong>the forecast key's meaning is unverified.</strong>
     * {@code getWeatherForecasts()} is an {@code Int2ObjectMap} keyed 0,1,2,3… — season, day index,
     * or something else. All slots are averaged with equal weight, which is the right answer if the
     * key is a season (it becomes the annual share) and merely a reasonable one otherwise.
     *
     * <p>Third, it is the <strong>environment's</strong> share, not this column's. Weather is
     * regional; there is no per-position storm probability to read.
     */
    @Override
    public OptionalDouble stormProbabilityAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return OptionalDouble.empty();
        }
        try {
            Environment asset = Environment.getAssetMap().getAsset(reader.getEnvironment(x, y, z));
            if (asset == null) {
                return OptionalDouble.empty();
            }
            var forecasts = asset.getWeatherForecasts();
            if (forecasts == null || forecasts.isEmpty()) {
                // No forecast at all is a real zero rather than an unknown: an environment that
                // authors no weather has no storms.
                return OptionalDouble.of(0);
            }
            double shareSum = 0;
            int slots = 0;
            for (var entry : forecasts.values()) {
                if (entry == null) {
                    continue;
                }
                // forEachEntry hands over (element, weight), which is the only way to see the
                // weights rather than sample from them. The array is the usual dodge for mutating
                // from a lambda; index 0 is the total weight and index 1 the lightning-bearing part.
                double[] acc = {0, 0};
                entry.forEachEntry((forecast, weight) -> {
                    acc[0] += weight;
                    if (forecast != null && isLightningBearing(forecast.getWeatherId())) {
                        acc[1] += weight;
                    }
                });
                double total = acc[0];
                double lightning = acc[1];
                if (total > 0) {
                    shareSum += lightning / total;
                    slots++;
                }
            }
            return slots == 0 ? OptionalDouble.of(0) : OptionalDouble.of(shareSum / slots);
        } catch (RuntimeException e) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Names that count as lightning-bearing, and the two that explicitly do not.
     *
     * <p>Name matching, because the asset tags that would do this properly are not on the runtime
     * object. {@code Zone4_Storm} is genuinely ambiguous — tagged only {@code Zone4:[Storm]} with no
     * precipitation group — and is counted, which is a choice the probe should be allowed to
     * contradict.
     */
    private static boolean isLightningBearing(String weatherId) {
        if (weatherId == null) {
            return false;
        }
        String id = weatherId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("sand") || id.contains("snow") || id.contains("dust")) {
            return false;
        }
        return id.contains("storm") || id.contains("thunder") || id.contains("lightning");
    }

    /** The plane this sample reads, for the probe to report rather than leave implicit. */
    public int referencePlaneY() {
        return referencePlaneY;
    }

    /**
     * Both fluid accessors at one position, for the probe to disambiguate by execution.
     *
     * <p>{@code SectionReader} exposes {@code getFluidId} and {@code getWorldFluidId} and the
     * difference is undocumented. {@link #fluidAt} commits to the former; this reports both so one
     * run settles whether that choice matters. Same discipline the sky-light probe used for the
     * global/local split rather than picking one and hoping.
     */
    public String fluidComparisonAt(int x, int y, int z) {
        if (!isLoaded(x, y, z)) {
            return "unloaded";
        }
        return "getFluidId=" + reader.getFluidId(x, y, z) + " getWorldFluidId=" + reader.getWorldFluidId(x, y, z);
    }
}

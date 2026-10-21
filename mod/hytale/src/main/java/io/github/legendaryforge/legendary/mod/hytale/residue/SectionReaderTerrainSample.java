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

    @Override
    public OptionalInt surfaceHeightAt(int x, int z) {
        return isLoaded(x, referencePlaneY, z) ? OptionalInt.of(referencePlaneY) : OptionalInt.empty();
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

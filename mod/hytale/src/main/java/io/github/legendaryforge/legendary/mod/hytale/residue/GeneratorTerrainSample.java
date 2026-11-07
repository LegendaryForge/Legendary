package io.github.legendaryforge.legendary.mod.hytale.residue;

import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import io.github.legendaryforge.legendary.core.api.residue.TerrainSample;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * A {@link TerrainSample} backed by the generator rather than by loaded chunks.
 *
 * <p><strong>Why this exists.</strong> {@link SectionReaderTerrainSample} gates every read on
 * {@code hasStorageInMemory}, so it can only answer inside chunks a connected client has caused to
 * load. That makes any strength measurement depend on a player being present and standing in the
 * right place. {@code ChunkGenerator.generateHeight(seed, x, z)} is a pure generator call that
 * answers at any coordinate with nothing loaded — measured against the topmost solid block
 * <strong>7/7 exactly</strong> on {@code 0.7.4} by {@code /worldgenheight} — so this adapter can
 * measure anywhere on a headless server. That is what lets a question about window geometry be
 * answered without a client.
 *
 * <p><strong>IT IS VALID FOR EARTH AND FOR NOTHING ELSE.</strong> This is not a limitation to work
 * around later; it follows from what each predicate needs, and using it for another element would
 * produce a plausible wrong number rather than an error:
 *
 * <ul>
 *   <li><strong>Earth</strong> — {@code Geometric}, {@code SOLID}. Its environment set is
 *       {@code Set.of("Env_")} and its sky-light band is {@code 0..15}, so <em>both of those gates
 *       are wide open</em> and only occupancy discriminates. The constants returned below for them
 *       therefore change nothing, and the measurement is driven entirely by {@link #isSolidAt} —
 *       the one read being substituted. <strong>Proxy, stated:</strong> {@code y <= height} treats
 *       the whole column below the surface as solid, so caves and overhangs count as rock. Earth is
 *       overestimated wherever the ground is hollow, and what the measurement therefore describes is
 *       <em>surface relief</em> rather than rock volume.
 *   <li><strong>Wind</strong> — same wide-open gates, but {@code occupied(OPEN)} is
 *       {@code !solid && fluidAt(x,y,z).isEmpty()}, and this adapter has no fluid data. Every ocean
 *       and lake column above the seabed would count as open air, so Wind would read strongest over
 *       deep water. Not a small distortion; Wind is not measurable here.
 *   <li><strong>Water, Fire</strong> — {@code Physical}, keyed entirely on {@link #fluidAt}. With no
 *       fluid data their strength is identically zero, indistinguishable from "absent".
 *   <li><strong>Lightning</strong> — {@code Weather}, which {@code VolumeStrength} throws for
 *       anyway, and which has no measure yet by any route.
 * </ul>
 *
 * <p>Accessors that cannot be answered return empty rather than a stand-in, so a caller that ignores
 * the restriction gets a zero it must explain rather than a number that looks usable.
 * {@link #isLoaded} returns true for every in-world y, which is honest here and is the point: there
 * is nothing to load.
 */
public final class GeneratorTerrainSample implements TerrainSample {

    /**
     * Hytale's world height, {@code y = 0..319}.
     *
     * <p>Matches {@code AnchorParameters.worldHeight()} and the engine's own {@code canHoldY}. Held
     * as a constant rather than taking the parameters record, because this adapter answers about the
     * world and not about one measurement's dials.
     */
    private static final int WORLD_HEIGHT = 320;

    /**
     * What {@link #environmentAt} returns everywhere.
     *
     * <p>Contains the literal {@code Env_} that Earth's grounding matches on, so the environment gate
     * passes. Deliberately not the name of any real environment: nothing should be able to read this
     * as having identified one.
     */
    private static final String SYNTHETIC_ENVIRONMENT = "Env_GeneratorDerived";

    /** Inside Earth's accepting {@code 0..15} band, so sky light cannot silently gate a measurement. */
    private static final int SYNTHETIC_SKY_LIGHT = 15;

    /** Where a column's surface comes from. Injected so the flat control needs no subclassing. */
    @FunctionalInterface
    private interface HeightSource {
        int heightAt(int x, int z);
    }

    private final HeightSource source;

    /**
     * Surface height per column, keyed {@code (x, z)}.
     *
     * <p>{@code VolumeStrength} walks y innermost, so it asks {@link #isSolidAt} about the same
     * column once per sampled level — 40 times at the default step. Without this, a scan makes 40x
     * the generator calls it needs. Unbounded on purpose: a run is one command, and the key space is
     * one region's sampled columns.
     */
    private final Long2IntMap heights = new Long2IntOpenHashMap();

    private GeneratorTerrainSample(HeightSource source) {
        this.source = source;
        this.heights.defaultReturnValue(Integer.MIN_VALUE);
    }

    /** Backed by {@code generator.generateHeight(seed, x, z)}. */
    public static GeneratorTerrainSample of(ChunkGenerator generator, int seed) {
        return new GeneratorTerrainSample((x, z) -> generator.generateHeight(seed, x, z));
    }

    /**
     * A flat-terrain variant, for use as a <strong>negative control</strong>.
     *
     * <p>Every column has the same surface, so every candidate's window holds the same volume and
     * every contest is an exact tie — the state in which the position hash decides everything. A
     * separation measurement that does not report zero separation here is measuring something other
     * than terrain. The standing rule is to run the control that seems unnecessary; the six-marker
     * weather result was one control away from a confidently wrong finding.
     */
    public static GeneratorTerrainSample flat(int surfaceHeight) {
        return new GeneratorTerrainSample((x, z) -> surfaceHeight);
    }

    /** Distinct columns whose height was fetched, so a caller can report the real call count. */
    public int columnsCached() {
        return heights.size();
    }

    /** Surface height at a column, memoised. */
    public int heightAt(int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        int cached = heights.get(key);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }
        int height = source.heightAt(x, z);
        heights.put(key, height);
        return height;
    }

    /**
     * True for every in-world y: the generator answers at any coordinate.
     *
     * <p>This is the difference that matters against {@link SectionReaderTerrainSample}, and it is
     * why a {@code ColumnMeasurement} from this adapter always reports the full window as sampled.
     */
    @Override
    public boolean isLoaded(int x, int y, int z) {
        return y >= 0 && y < WORLD_HEIGHT;
    }

    /** {@code y <= surface height}. See the proxy note in the class javadoc. */
    @Override
    public boolean isSolidAt(int x, int y, int z) {
        return isLoaded(x, y, z) && y <= heightAt(x, z);
    }

    /** The generator's own height for the column — the read this adapter exists to provide. */
    @Override
    public OptionalInt surfaceHeightAt(int x, int z) {
        return OptionalInt.of(heightAt(x, z));
    }

    /** Empty always: no fluid data, so Water and Fire are not measurable here. */
    @Override
    public Optional<String> fluidAt(int x, int y, int z) {
        return Optional.empty();
    }

    /** A synthetic name carrying {@code Env_}, so the gate passes without claiming a real one. */
    @Override
    public Optional<String> environmentAt(int x, int y, int z) {
        return isLoaded(x, y, z) ? Optional.of(SYNTHETIC_ENVIRONMENT) : Optional.empty();
    }

    /** A constant inside Earth's accepting band. */
    @Override
    public int skyLightAt(int x, int y, int z) {
        return SYNTHETIC_SKY_LIGHT;
    }

    /** Empty always: weather is not derivable from the generator. */
    @Override
    public OptionalDouble stormProbabilityAt(int x, int y, int z) {
        return OptionalDouble.empty();
    }
}

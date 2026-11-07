package io.github.legendaryforge.legendary.mod.hytale.residue;

import io.github.legendaryforge.legendary.core.api.residue.ElementGrounding;
import java.util.List;
import java.util.Set;

/**
 * The roster's groundings, in the only module allowed to name Hytale assets.
 *
 * <p>{@code :core} holds the grounding <em>types</em> and the scoring; it may not hold these ids,
 * because {@code Lava_Source} and {@code Env_Zone4_Volcanoes} are Hytale's and {@code :core} is
 * engine-agnostic by build guard. That separation is also what keeps the roster a reversible
 * documentation decision rather than an API: there is no enum of elements anywhere, only these
 * strings.
 *
 * <p><strong>Roster: the cosmological five</strong>, decided 2026-10-21 — and grouped here by how
 * each is grounded, because that is not uniform and a single rule shape got two of them wrong.
 *
 * <p><strong>Void is absent, by decision.</strong> It is a staged incursion the engine runs inside a
 * Fragment on a timer and ends by eviction, and its droppers span 16 environments across three zones
 * with no terrain character. Nothing about terrain should site it. Ice and Life are absent too: Ice
 * is modelled inside Water (Water's own text covers *"the magic of water and ice"*, so glacial
 * terrain is Water's to claim), and Life is farmed rather than sited — 1 NPC dropper against 64 crop
 * tables.
 *
 * <p><strong>Every id here was verified present in build 0.7.3</strong> rather than guessed: the
 * fluids are {@code Fluid} assets under {@code Server/Item/Block/Fluids/}, and the environment
 * substrings were matched against the 134 shipped {@code Server/Environments} assets. A rule naming
 * an asset that does not exist would score zero forever and look exactly like terrain that does not
 * qualify.
 *
 * <p><strong>That is not a hypothetical, and the fluid namespace is the trap.</strong> Until
 * 2026-10-21 these required {@code Fluid_Lava} and {@code Fluid_Water}, taken from
 * {@code Server/BlockTypeList/Fluids.json} — a <em>block type</em> list. Those names exist, so the
 * verification above passed; they are simply not what the runtime hands back.
 * {@code SectionReader.getFluidId} returns a {@code Fluid} asset index, which
 * {@link SectionReaderTerrainSample} resolves through {@code Fluid.getAssetMap()} to ids like
 * {@code Water_Source}. So both {@code Physical} elements — the only two with a required fluid —
 * could never match, and {@code Physical} rejected every candidate while looking like correct
 * strictness. Caught by the first live {@code /residueprobe} run standing in a river
 * ({@code fluid=Water_Source getFluidId=2}) and confirmed standing in lava
 * ({@code fluid=Lava getFluidId=14}). The unit tests could not catch it, because {@code :core}
 * treats fluid names as opaque strings and {@code SiteScorerTest} supplies the same invented name to
 * both the grounding and its fake terrain.
 *
 * <p><strong>Why substrings and not the asset tags.</strong> Environments carry structured
 * {@code Tags} in their JSON — {@code Volcanic}, {@code Shores}, {@code Glacial}, {@code Mountains} —
 * which would be the principled key. They are not usable: there is <strong>no tag accessor on the
 * runtime {@code Environment} object</strong>, only {@code getId()}. And they are incomplete anyway —
 * 11 environments are volcanic by name against 5 by tag, the gap being exactly the {@code _T2} and
 * {@code _T3} tiers, and 29 of 134 environments carry no sub-tag at all. So names are both the only
 * runtime option and the more complete one. Measured, not assumed.
 */
public final class HytaleDerivationRules {

    private HytaleDerivationRules() {}

    /**
     * The five groundings, as a starting position for tuning rather than a settled answer.
     *
     * <p>Known gaps, stated because the probe exists to expose them:
     *
     * <ul>
     *   <li><strong>River versus ocean is not expressible.</strong> No {@code River}, {@code Ocean}
     *       or {@code Lake} environment ships, there is no sea-level constant in the jar, and no
     *       river or ocean worldgen node exists. Water is water.
     *   <li><strong>Earth leans on caves</strong>, the cheapest enclosure proxy available without
     *       block-level sampling. It will miss exposed mountain stone.
     *   <li><strong>Lightning's storm gate is a guess.</strong> 0.10 sits well above Zone 1's ~0.014
     *       and well below the Crucible's 0.50, but nothing has measured the distribution between
     *       them across all 134 environments.
     * </ul>
     */
    /**
     * The volume that reproduces the old 1% fraction floor at the default window — {@code ceil(0.01 *
     * 43,560) = 436} samples at step 8, so {@code 436 * 8³}. Used for every element whose floor was
     * never the binding constraint.
     *
     * <p><strong>This number's provenance is a window fraction, not a volume.</strong> It is carried
     * forward unchanged because changing it is a separate decision from making it step-invariant, and
     * because for Earth, Wind and Water the floor has never been the binding constraint — a column is
     * 20-80% solid, and session 26 measured water at 4,225 samples where a pocket read 91. If
     * {@code measurementRadius} is ever narrowed, this is the value to re-derive: at radius 64 the
     * same volume is 3.8% of the window rather than 1%.
     */
    private static final int ABUNDANT_VOLUME = 436 * 8 * 8 * 8;

    /**
     * Fire's floor, derived from a live measurement rather than chosen.
     *
     * <p>Measured 2026-10-23 in a lava biome with a player swimming in deep lava rivers: across 15
     * fully-loaded candidates in a 1 km region, seven held <strong>0</strong> lava samples and the
     * weakest real river held <strong>44</strong>, with the richest at 261. Any threshold in 1..44
     * separates absence from presence, so this is a 44x margin rather than a tuned value; 10 sits
     * near its geometric middle and is ~5,120 block-volumes at the default step of 8 — plainly not
     * the "2x2 puddle" the floor exists to reject.
     *
     * <p>The old 1% fraction floor was 436 samples and lava's best reading anywhere was 261, so Fire
     * could never site at all. See {@code ElementGrounding.minimumStrengthVolume()}.
     *
     * <p>Stated as the volume directly, so the 44x separation it rests on cannot be rescaled by a
     * sampling change: {@code 10 * 8³}.
     */
    private static final int FIRE_VOLUME = 10 * 8 * 8 * 8;

    public static List<ElementGrounding> cosmologicalFive() {
        return List.of(
                // --- Physical: the fluid is required. A volcanic region with no lava is not a Fire
                // site, which is the defect the Physical type exists to prevent.
                // Source is the body of a lava lake; the bare id is its flowing child. Both count,
                // and the flowing one is not a speculative inclusion: standing in lava measured
                // fluid=Lava getFluidId=14, so a set holding only Lava_Source would still have
                // missed what a player was standing in.
                new ElementGrounding.Physical(
                        "fire",
                        Set.of("Lava_Source", "Lava"),
                        Set.of("Volcanoes", "Caves_Volcanic", "Crucible", "Wastes"),
                        FIRE_VOLUME),

                // Glacial is corroboration here rather than a separate Ice element: the roster
                // decision models Ice inside Water.
                //
                // Water_Finite is deliberately excluded. It shares Water_Source as its Parent but
                // carries Ticker.Type=Finite, which is contained water — a player emptying a bucket
                // should not mint a Water nexus. Worldgen bodies read as Water_Source (measured).
                new ElementGrounding.Physical(
                        "water",
                        Set.of("Water_Source", "Water"),
                        Set.of("Shores", "Swamps", "Glacial", "Caves_Swamps", "Oasis"),
                        ABUNDANT_VOLUME),

                // --- Geometric: terrain shape, no marker to require.
                //
                // Wind is open space -- the roster decision names the element Wind, and wind and air
                // are interchangeable here because the game has no wind mechanic at all (operator,
                // 2026-10-22). The id stays "wind" to match that decision; only the MEASURE is air.
                // It is symmetric with Earth below: earth is rock, air is
                // not-rock-and-not-water. No sky gate and no environment list, so a cave counts as
                // air -- air is there. The element is named for open space rather than for any wind
                // mechanic, because the game has none (operator, 2026-10-22): an earlier version of
                // this entry argued wind-as-exposure and gated on Mountains/Peaks/Highlands/
                // Plateaus/Cliffs with sky 12..15, which measured ZERO anchors on plains while
                // Earth and Water each produced two. That was a biome label standing in for a
                // physical property, the same mistake as Earth's old cave list.
                //
                // Consequence, recorded rather than discovered later: open space over a full column
                // is inversely correlated with ground height -- about 80% of a column over an ocean
                // floor against 20% at a peak, the exact inverse of Earth's 20% to 80%. So air
                // anchors favour valleys and lowlands while earth anchors favour high ground, and
                // the two tile the world instead of competing for it.
                new ElementGrounding.Geometric(
                        "wind", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.OPEN, ABUNDANT_VOLUME),

                // Deliberately the inverse exposure band: enclosed stone, not open ground. A cave
                // environment read at high sky light is a cave mouth, and scores lower for it.
                // Earth is rock, and rock needs no environment label and no darkness. The cave
                // environment set and the 0..6 sky band this once carried were the inverted framing
                // §2 rejected: they excluded a mountain's bulk, the clearest case of a lot of earth.
                // Sky 0..15 and a substring every environment id carries keep the record's non-empty
                // invariant while gating on occupancy alone.
                new ElementGrounding.Geometric(
                        "earth", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.SOLID, ABUNDANT_VOLUME),

                // --- Weather: no physical presence at all. Storm probability comes from the
                // environment's own WeatherForecasts weights, which is why this is the one grounding
                // where an environment id is the primary signal rather than a coarse proxy.
                new ElementGrounding.Weather("lightning", 0.10, 13, 15, ABUNDANT_VOLUME));
    }
}

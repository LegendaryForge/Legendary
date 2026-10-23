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
                        Set.of("Volcanoes", "Caves_Volcanic", "Crucible", "Wastes")),

                // Glacial is corroboration here rather than a separate Ice element: the roster
                // decision models Ice inside Water.
                //
                // Water_Finite is deliberately excluded. It shares Water_Source as its Parent but
                // carries Ticker.Type=Finite, which is contained water — a player emptying a bucket
                // should not mint a Water nexus. Worldgen bodies read as Water_Source (measured).
                new ElementGrounding.Physical(
                        "water",
                        Set.of("Water_Source", "Water"),
                        Set.of("Shores", "Swamps", "Glacial", "Caves_Swamps", "Oasis")),

                // --- Geometric: terrain shape, no marker to require. Both halves needed.
                // Open sky is the discriminator, so the band is tight.
                new ElementGrounding.Geometric(
                        "wind",
                        Set.of("Mountains", "Peaks", "Highlands", "Plateaus", "Cliffs"),
                        12,
                        15,
                        ElementGrounding.Occupancy.OPEN_ABOVE_SOLID),

                // Deliberately the inverse exposure band: enclosed stone, not open ground. A cave
                // environment read at high sky light is a cave mouth, and scores lower for it.
                // Earth is rock, and rock needs no environment label and no darkness. The cave
                // environment set and the 0..6 sky band this once carried were the inverted framing
                // §2 rejected: they excluded a mountain's bulk, the clearest case of a lot of earth.
                // Sky 0..15 and a substring every environment id carries keep the record's non-empty
                // invariant while gating on occupancy alone.
                new ElementGrounding.Geometric("earth", Set.of("Env_"), 0, 15, ElementGrounding.Occupancy.SOLID),

                // --- Weather: no physical presence at all. Storm probability comes from the
                // environment's own WeatherForecasts weights, which is why this is the one grounding
                // where an environment id is the primary signal rather than a coarse proxy.
                new ElementGrounding.Weather("lightning", 0.10, 13, 15));
    }
}
